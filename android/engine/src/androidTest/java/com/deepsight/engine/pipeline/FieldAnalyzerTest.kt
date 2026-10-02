package com.deepsight.engine.pipeline

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.QualitySpec
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.pack.PackLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Whole field: quality -> RBC detector -> crops -> model -> decoder, against ml/reference/malaria_pipeline.py.
 * Needs the local-only malaria_thin weights; the RBCNet test also needs ml/data/android_parity (gitignored).
 * Timings: adb logcat -d -s DeepSightField
 */
@RunWith(AndroidJUnit4::class)
class FieldAnalyzerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    // The test APK holds <repo>/ml/packs under packs/ (engine/build.gradle.kts, stageGoldenPacks).
    private val pack: LoadedPack? by lazy { runCatching { PackLoader.fromAssets(assets, packsRoot = "packs").load("malaria_thin") }.getOrNull() }

    @Before
    fun packInstalled() = assumeTrue("malaria_thin weights are not installed", pack != null)

    @Test
    fun syntheticFieldMatchesPythonReference() {
        // The synthetic field is mostly vignette, so quality is opened up: this test is about detection and scores.
        val loaded = pack!!
        val open = loaded.copy(manifest = loaded.manifest.copy(quality = QualitySpec(minBlur = 0.0, maxClippedFraction = 1.0)))
        FieldAnalyzer(open).use { analyzer ->
            val analysis = analyzer.analyze("case-test", "field-01", copyAsset("nlm_synthetic.png").path)
            val ok = compare("synthetic", analysis, json("packs/malaria_thin/golden/field_synthetic.json"))
            assertTrue(problems.joinToString(), ok)
        }
    }

    @Test
    fun rbcnetFieldsMatchPythonReference() {
        val names = assets.list("").orEmpty().filter { it.contains("ThinF_IMG") && it.endsWith(".json") }.map { it.removeSuffix(".json") }
        assumeTrue("ml/data/android_parity is not present", names.isNotEmpty())
        FieldAnalyzer(pack!!).use { analyzer ->
            for (name in names) {
                val analysis = analyzer.analyze("case-test", name, copyAsset("$name.jpg").path)
                if (analysis.field?.quality?.pass != true) problems += "$name failed quality: ${analysis.field?.quality}"
                else compare(name, analysis, json("$name.json"))
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private val problems = mutableListOf<String>()

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
    private fun compare(name: String, analysis: FieldAnalysis, golden: JsonObject): Boolean {
        val field: FieldResult = analysis.field ?: return false.also { problems += "$name: NLM asked for a retake" }
        val expected = golden.getValue("cells").jsonArray.map { cell ->
            cell.jsonObject.getValue("box_xyxy").jsonArray.map { it.jsonPrimitive.int } to
                cell.jsonObject.getValue("p_parasitized").jsonPrimitive.double
        }
        val ours = field.objects.map { o ->
            val (x, y, w, h) = o.bbox!!
            listOf(x * analysis.width, y * analysis.height, (x + w) * analysis.width, (y + h) * analysis.height) to
                if (o.label == "parasitized") o.score else 1.0 - o.score
        }
        val rv = 6.0 / sqrt(5312.0 * 2988.0 / (analysis.width.toDouble() * analysis.height))   // CameraActivity.resizeImage
        fun gaps(tolerance: Double) = expected.mapNotNull { (box, p) ->
            ours.filter { (b, _) -> box.indices.all { abs(b[it] - box[it]) <= tolerance } }.minOfOrNull { abs(it.second - p) }
        }
        val fullRes = gaps(2.0)
        val segScale = gaps(2 * rv)
        val matched = segScale.count { it <= 0.02 }
        Log.i(TAG, "$name: ${ours.size} cells (python ${expected.size}); boxes within 2 px ${fullRes.size}, within 2 segmentation px " +
            "${segScale.size} of which score within 0.02 $matched (max gap %.3f); ".format(segScale.maxOrNull() ?: 0.0) +
            "parasitized ${field.counts["parasitized"]} (python ${expected.count { it.second > 0.5 }}), timing ${field.timingMs}")
        val parasitized = field.counts["parasitized"] ?: 0
        val pythonParasitized = expected.count { it.second > 0.5 }
        val countOk = abs(ours.size - expected.size) <= maxOf(2, expected.size / 50)
        val matchOk = matched >= expected.size * 90 / 100
        val parasitizedOk = abs(parasitized - pythonParasitized) <= maxOf(2, pythonParasitized / 10)
        if (!countOk) problems += "$name: cell count ${ours.size} vs ${expected.size}"
        if (!matchOk) problems += "$name: only $matched of ${expected.size} cells match"
        if (!parasitizedOk) problems += "$name: parasitized $parasitized vs $pythonParasitized"
        return countOk && matchOk && parasitizedOk
    }

    private fun json(name: String) = assets.open(name).use { Json.parseToJsonElement(it.reader().readText()).jsonObject }

    private fun copyAsset(name: String): File =
        File(instrumentation.targetContext.cacheDir, name).also { out ->
            out.outputStream().use { sink -> assets.open(name).use { it.copyTo(sink) } }
        }

    private companion object {
        const val TAG = "DeepSightField"
    }
}
