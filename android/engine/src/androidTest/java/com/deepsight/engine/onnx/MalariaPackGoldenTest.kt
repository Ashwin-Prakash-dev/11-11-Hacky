package com.deepsight.engine.onnx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor

/**
 * Golden test for the malaria thin-smear pack (ml/packs/malaria_thin), ported from its golden/verify.py.
 * Timings: adb logcat -d -s DeepSightMalaria
 */
@RunWith(AndroidJUnit4::class)
class MalariaPackGoldenTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val expected by lazy { json("$PACK/golden/expected.json") }
    private val cases by lazy { expected.getValue("cases").jsonArray.map { it.jsonObject } }
    private val modelFile by lazy { json("$PACK/manifest.json").getValue("model").jsonObject.getValue("file").jsonPrimitive.content }
    private val modelBytes by lazy { assets.open("$PACK/$modelFile").use { it.readBytes() } }

    // Weights and NIH chips are local-only until their licences are resolved (S1), so most checkouts skip this test.
    @Before
    fun packPresent() = assumeTrue(
        "$PACK weights or golden chips missing",
        assets.list(PACK)?.contains(modelFile) == true && assets.list("$PACK/golden/chips")?.isNotEmpty() == true,
    )

    /** Test A: the exact desktop input tensor must reproduce expected.json within its tolerance. */
    @Test
    fun tensorMatchesDesktop() {
        val tolerance = expected.getValue("tolerance_tensor").jsonPrimitive.float
        val input = assets.open("$PACK/golden/input_32x44x44x3_float32.bin").use { it.readBytes() }
            .let { bytes -> FloatArray(bytes.size / 4).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) } }
        for (accelerator in OnnxModel.Accelerator.entries) {
            val probs = OnnxModel(modelBytes, accelerator).use { it.run(input, shapeOf(cases.size)) }
            val err = cases.indices.maxOf { i -> maxOf(abs(probs[2 * i] - cases[i].f("p_infected")), abs(probs[2 * i + 1] - cases[i].f("p_uninfected"))) }
            Log.i(TAG, "Test A %s: max abs err %.2e (tolerance %.0e)".format(accelerator, err, tolerance))
            assertTrue("$accelerator max abs err $err", err < tolerance)
        }
    }

    /**
     * Test B: PNG chips decoded on Android and resized like OpenCV INTER_CUBIC must match desktop within 0.07.
     * Also logs Android's bilinear createScaledBitmap, which the pack README suggests, for comparison.
     */
    @Test
    fun pngPreprocessingMatchesDesktop() {
        val bicubicErr = compareChips("bicubic", bicubic = true)
        compareChips("bilinear", bicubic = false)
        assertTrue("bicubic max abs err $bicubicErr", bicubicErr < 0.07f)
    }

    private fun compareChips(name: String, bicubic: Boolean): Float {
        val input = FloatArray(cases.size * CHIP_FLOATS)
        cases.forEachIndexed { i, case -> chipToRgb(case.getValue("file").jsonPrimitive.content, input, i * CHIP_FLOATS, bicubic) }
        val probs = OnnxModel(modelBytes).use { it.run(input, shapeOf(cases.size)) }
        val pInfected = cases.indices.map { probs[2 * it] }
        val err = cases.indices.maxOf { abs(pInfected[it] - cases[it].f("p_infected")) }
        val flips = cases.indices.count { (pInfected[it] > 0.5f) != (cases[it].f("p_infected") > 0.5f) }
        val correct = cases.indices.count { (pInfected[it] > 0.5f) == (cases[it].getValue("label").jsonPrimitive.content == "parasitized") }
        Log.i(TAG, "Test B %s: max abs err %.4f (tolerance 0.07), decisions flipped vs desktop %d/%d, correct vs label %d/%d"
            .format(name, err, flips, cases.size, correct, cases.size))
        return err
    }

    @Test
    fun logTimings() {
        val one = FloatArray(CHIP_FLOATS).also { chipToRgb(cases[0].getValue("file").jsonPrimitive.content, it, 0, bicubic = true) }
        for (accelerator in OnnxModel.Accelerator.entries) {
            val loadStart = System.nanoTime()
            OnnxModel(modelBytes, accelerator).use { model ->
                val loadMs = (System.nanoTime() - loadStart) / 1e6
                for (batch in intArrayOf(1, 32, 256)) {
                    val input = FloatArray(batch * CHIP_FLOATS) { one[it % CHIP_FLOATS] }
                    repeat(3) { model.run(input, shapeOf(batch)) }
                    val runsMs = List(10) {
                        val start = System.nanoTime()
                        model.run(input, shapeOf(batch))
                        (System.nanoTime() - start) / 1e6
                    }.sorted()
                    Log.i(TAG, "%s load=%.1fms batch=%d median=%.1fms min=%.1fms".format(accelerator, loadMs, batch, runsMs[5], runsMs[0]))
                }
            }
        }
    }

    // ARGB_8888 -> 44x44 -> RGB / 255, NHWC.
    private fun chipToRgb(file: String, out: FloatArray, offset: Int, bicubic: Boolean) {
        val decoded = assets.open("$PACK/golden/chips/$file").use { BitmapFactory.decodeStream(it) }
        val pixels = if (bicubic) {
            val src = IntArray(decoded.width * decoded.height)
            decoded.getPixels(src, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            resizeCubic(src, decoded.width, decoded.height)
        } else {
            IntArray(CHIP * CHIP).also { Bitmap.createScaledBitmap(decoded, CHIP, CHIP, true).getPixels(it, 0, CHIP, 0, 0, CHIP, CHIP) }
        }
        pixels.forEachIndexed { j, p ->
            out[offset + 3 * j] = ((p shr 16) and 0xFF) / 255f
            out[offset + 3 * j + 1] = ((p shr 8) and 0xFF) / 255f
            out[offset + 3 * j + 2] = (p and 0xFF) / 255f
        }
    }

    // cv2.resize INTER_CUBIC: A = -0.75, half-pixel centres, replicated border, rounded to 8 bits like OpenCV's uint8 output.
    private fun resizeCubic(src: IntArray, w: Int, h: Int): IntArray {
        val (xIndex, xWeight) = cubicTaps(w)
        val (yIndex, yWeight) = cubicTaps(h)
        return IntArray(CHIP * CHIP) { o ->
            val y = o / CHIP
            val x = o % CHIP
            val rgb = DoubleArray(3)
            for (j in 0..3) for (i in 0..3) {
                val weight = yWeight[y * 4 + j] * xWeight[x * 4 + i]
                val p = src[yIndex[y * 4 + j] * w + xIndex[x * 4 + i]]
                for (c in 0..2) rgb[c] += weight * ((p shr (16 - 8 * c)) and 0xFF)
            }
            rgb.fold(0) { acc, v -> (acc shl 8) or Math.round(v).toInt().coerceIn(0, 255) }
        }
    }

    private fun cubicTaps(srcSize: Int): Pair<IntArray, DoubleArray> {
        val a = -0.75
        val index = IntArray(CHIP * 4)
        val weight = DoubleArray(CHIP * 4)
        for (d in 0 until CHIP) {
            val f = (d + 0.5) * srcSize / CHIP - 0.5
            val s = floor(f).toInt()
            val t = f - s
            val w0 = ((a * (t + 1) - 5 * a) * (t + 1) + 8 * a) * (t + 1) - 4 * a
            val w1 = ((a + 2) * t - (a + 3)) * t * t + 1
            val w2 = ((a + 2) * (1 - t) - (a + 3)) * (1 - t) * (1 - t) + 1
            doubleArrayOf(w0, w1, w2, 1 - w0 - w1 - w2).forEachIndexed { k, wk ->
                index[d * 4 + k] = (s - 1 + k).coerceIn(0, srcSize - 1)
                weight[d * 4 + k] = wk
            }
        }
        return index to weight
    }

    private fun shapeOf(batch: Int) = longArrayOf(batch.toLong(), CHIP.toLong(), CHIP.toLong(), 3)

    private fun json(path: String) = assets.open(path).use { Json.parseToJsonElement(it.reader().readText()).jsonObject }

    private fun JsonObject.f(key: String) = getValue(key).jsonPrimitive.float

    private companion object {
        const val TAG = "DeepSightMalaria"
        const val PACK = "mlpacks/malaria_thin"
        const val CHIP = 44
        const val CHIP_FLOATS = CHIP * CHIP * 3
    }
}
