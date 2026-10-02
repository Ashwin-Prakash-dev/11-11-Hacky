package com.deepsight.engine.pipeline

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.QualitySpec
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.quality.PixelImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * FieldPipeline with the engine's red-cell finder (CellFinders.forPack) on malaria_thin, against
 * ml/reference/malaria_pipeline.py. Fields are decoded with OpenCV, as the Python reference does (EXIF applied).
 * Needs the local-only malaria_thin weights; the RBCNet test also needs ml/data/android_parity (gitignored).
 * Timings: adb logcat -d -s DeepSightField
 */
@RunWith(AndroidJUnit4::class)
class RbcFieldPipelineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    // The test APK holds <repo>/ml/packs under packs/ (engine/build.gradle.kts, stageGoldenPacks).
    private val pack: LoadedPack? by lazy { runCatching { PackLoader.fromAssets(assets, packsRoot = "packs").load("malaria_thin") }.getOrNull() }
    private val problems = mutableListOf<String>()

    @Before
    fun packInstalled() {
        assumeTrue("malaria_thin weights are not installed", pack != null)
        assertTrue(OpenCVLoader.initLocal())
    }

    @Test
    fun malariaThinGetsTheRedCellFinder() {
        assertNotNull(CellFinders.forPack(pack!!.manifest))
    }

    @Test
    fun syntheticFieldMatchesPythonReference() {
        // The synthetic field is mostly vignette, so quality is opened up: this test is about cells and scores.
        val loaded = pack!!
        val open = loaded.copy(manifest = loaded.manifest.copy(quality = QualitySpec(minBlur = 0.0, maxClippedFraction = 1.0)))
        val image = decode("nlm_synthetic.png")
        val field = FieldPipeline(open, cellFinder = CellFinders.forPack(open)).use { it.analyze("case-test", "field-01", image) }
        val ok = compare("synthetic", field, image, json("packs/malaria_thin/golden/field_synthetic.json"))
        assertTrue(problems.joinToString(), ok)
    }

    @Test
    fun rbcnetFieldsMatchPythonReference() {
        val names = assets.list("").orEmpty().filter { it.contains("ThinF_IMG") && it.endsWith(".json") }.map { it.removeSuffix(".json") }
        assumeTrue("ml/data/android_parity is not present", names.isNotEmpty())
        FieldPipeline(pack!!, cellFinder = CellFinders.forPack(pack!!)).use { pipeline ->
            for (name in names) {
                val image = decode("$name.jpg")
                val field = pipeline.analyze("case-test", name, image)
                if (!field.quality.pass) problems += "$name failed quality: ${field.quality}"
                else compare(name, field, image, json("$name.json"))
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The phone against desktop Python, end to end. OpenCV's INTER_CUBIC resize differs by +-1 on ~1% of pixels
     * between ARM and x86, so a few cells move by one pixel at segmentation scale (RV full-resolution pixels) and their
     * crops change at the edge. Measured on the edge 50 fusion, 2026-10-02: 92.6-97.7% of RBCNet cells and 90.3% of
     * synthetic cells matched below; counts within 1.1%; parasitized counts within 2.
     *
     * Bounds: boxes within 2 segmentation pixels and scores within 0.02 (docs/architecture.md's 2 px / 0.02, at the
     * detector's scale) for 90% of cells; counts within 2%; parasitized within 2 or 10%. The port's logic is checked
     * exactly, without the resize, by RbcDetectorTest.segmentationMatchesNlmJavaOnTheSameResizedInput.
     * Records failures in [problems] and returns whether this field passed.
     */
    private fun compare(name: String, field: FieldResult, image: PixelImage, golden: JsonObject): Boolean {
        val expected = golden.getValue("cells").jsonArray.map { cell ->
            cell.jsonObject.getValue("box_xyxy").jsonArray.map { it.jsonPrimitive.int } to
                cell.jsonObject.getValue("p_parasitized").jsonPrimitive.double
        }
        val ours = field.objects.map { o ->
            val (x, y, w, h) = o.bbox!!
            listOf(x * image.width, y * image.height, (x + w) * image.width, (y + h) * image.height) to
                if (o.label == "parasitized") o.score else 1.0 - o.score
        }
        val rv = 6.0 / sqrt(5312.0 * 2988.0 / (image.width.toDouble() * image.height))   // CameraActivity.resizeImage
        val gaps = expected.mapNotNull { (box, p) ->
            ours.filter { (b, _) -> box.indices.all { abs(b[it] - box[it]) <= 2 * rv } }.minOfOrNull { abs(it.second - p) }
        }
        val matched = gaps.count { it <= 0.02 }
        val parasitized = field.counts["parasitized"] ?: 0
        val pythonParasitized = expected.count { it.second > 0.5 }
        Log.i(TAG, "$name: ${ours.size} cells (python ${expected.size}), within 2 segmentation px ${gaps.size} of which score " +
            "within 0.02 $matched (max gap %.3f); parasitized $parasitized (python $pythonParasitized), timing ${field.timingMs}"
                .format(gaps.maxOrNull() ?: 0.0))
        val countOk = abs(ours.size - expected.size) <= maxOf(2, expected.size / 50)
        val matchOk = matched >= expected.size * 90 / 100
        val parasitizedOk = abs(parasitized - pythonParasitized) <= maxOf(2, pythonParasitized / 10)
        if (!countOk) problems += "$name: cell count ${ours.size} vs ${expected.size}"
        if (!matchOk) problems += "$name: only $matched of ${expected.size} cells match"
        if (!parasitizedOk) problems += "$name: parasitized $parasitized vs $pythonParasitized"
        return countOk && matchOk && parasitizedOk
    }

    /** OpenCV imread (EXIF applied, like cv2.imread in the reference) into ARGB pixels. */
    private fun decode(asset: String): PixelImage {
        val file = File(instrumentation.targetContext.cacheDir, asset).also { out ->
            out.outputStream().use { sink -> assets.open(asset).use { it.copyTo(sink) } }
        }
        val rgb = Mat().also { Imgproc.cvtColor(Imgcodecs.imread(file.path, Imgcodecs.IMREAD_COLOR), it, Imgproc.COLOR_BGR2RGB) }
        val argb = IntArray(rgb.cols() * rgb.rows())
        val row = ByteArray(rgb.cols() * 3)
        for (y in 0 until rgb.rows()) {
            rgb.get(y, 0, row)
            for (x in 0 until rgb.cols()) {
                argb[y * rgb.cols() + x] = (0xff shl 24) or ((row[3 * x].toInt() and 0xff) shl 16) or
                    ((row[3 * x + 1].toInt() and 0xff) shl 8) or (row[3 * x + 2].toInt() and 0xff)
            }
        }
        return PixelImage(rgb.cols(), rgb.rows(), argb).also { rgb.release() }
    }

    private fun json(name: String) = assets.open(name).use { Json.parseToJsonElement(it.reader().readText()).jsonObject }

    private companion object {
        const val TAG = "DeepSightField"
    }
}
