package com.deepsight.engine.segmentation

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * The Kotlin port against NLM's own Java (OpenCV 3.4.2, x86) on ml/tests/data/nlm_synthetic.png.
 *
 * OpenCV's INTER_CUBIC resize differs by +-1 on ~1% of pixels between ARM and x86, which moves a few cells. So the
 * port's logic is checked on the desktop-resized input (nlm_synthetic_small.png) against the golden, and the full
 * photo-to-cells path within that platform noise. Timing: adb logcat -d -s DeepSightField
 */
@RunWith(AndroidJUnit4::class)
class RbcDetectorTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val golden by lazy {
        assets.open("nlm_synthetic_cells.json").use { Json.parseToJsonElement(it.reader().readText()) }
            .jsonObject.getValue("cell_centers_row_col").jsonArray
            .map { c -> c.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int } }
    }

    @Before
    fun loadOpenCv() = assertTrue(OpenCVLoader.initLocal())

    @Test
    fun segmentationMatchesNlmJavaOnTheSameResizedInput() {
        val (watershed, wbc) = requireNotNull(RbcDetector.segment(rgb("nlm_synthetic_small.png"), 1.5f)) { "retake" }
        assertTrue("watershed mask differs", differing(watershed, gray("nlm_synthetic_ws.png")) <= 10)
        val goldenWbc = Mat().also { Core.divide(gray("nlm_synthetic_wbc.png"), Scalar(255.0), it) }
        assertTrue("golden must exercise the WBC path", Core.countNonZero(goldenWbc) > 0)
        assertTrue("WBC mask differs", differing(wbc, goldenWbc) <= 5)

        val centers = RbcDetector.cells(watershed, wbc, rgb("nlm_synthetic.png")).map { it.centerRow to it.centerCol }
        assertTrue("cell count ${centers.size} vs ${golden.size}", abs(centers.size - golden.size) <= 1)
        assertTrue("cell centers differ", matched(centers) >= golden.size - 1)
    }

    @Test
    fun fullDetectionAgreesWithNlmJavaWithinPlatformNoise() {
        val start = System.nanoTime()
        val detection = requireNotNull(RbcDetector.detect(rgb("nlm_synthetic.png"))) { "retake" }
        val ms = (System.nanoTime() - start) / 1e6
        val maskDiff = differing(detection.watershed, gray("nlm_synthetic_ws.png"))
        val centers = detection.cells.map { it.centerRow to it.centerCol }
        Log.i(TAG, "synthetic: ${centers.size} cells (Java ${golden.size}), ${matched(centers)} matched within 2 px, " +
            "watershed $maskDiff px differ, %.0f ms".format(ms))
        assertTrue("watershed differs on $maskDiff px", maskDiff <= detection.watershed.total() / 200)
        assertTrue("cell count ${centers.size} vs ${golden.size}", abs(centers.size - golden.size) <= maxOf(2, golden.size / 50))
        assertTrue("cell centers differ", matched(centers) >= golden.size * 95 / 100)
    }

    /** Golden centers with one of [centers] within 2 px. */
    private fun matched(centers: List<Pair<Int, Int>>) =
        golden.count { (r, c) -> centers.any { abs(it.first - r) <= 2 && abs(it.second - c) <= 2 } }

    private fun decode(name: String, flags: Int): Mat =
        Imgcodecs.imdecode(MatOfByte(*assets.open(name).use { it.readBytes() }), flags)

    private fun rgb(name: String) = Mat().also { Imgproc.cvtColor(decode(name, Imgcodecs.IMREAD_COLOR), it, Imgproc.COLOR_BGR2RGB) }

    private fun gray(name: String) = decode(name, Imgcodecs.IMREAD_GRAYSCALE)

    private fun differing(a: Mat, b: Mat): Int {
        assertEquals(b.size(), a.size())
        return Mat().also { Core.compare(a, b, it, Core.CMP_NE) }.let(Core::countNonZero)
    }

    private companion object {
        const val TAG = "DeepSightField"
    }
}
