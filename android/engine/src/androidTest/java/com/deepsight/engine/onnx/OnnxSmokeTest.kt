package com.deepsight.engine.onnx

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spike S2: ONNX Runtime on the phone. Checks output against desktop ORT (assets/smoke, made by
 * ml/eval/make_smoke_model.py), then logs timings: adb logcat -d -s DeepSightS2
 */
@RunWith(AndroidJUnit4::class)
class OnnxSmokeTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val modelBytes = assets.open("smoke/model.onnx").use { it.readBytes() }
    private val expected = assets.open("smoke/expected.json").use { Json.parseToJsonElement(it.reader().readText()).jsonObject }

    @Test
    fun matchesDesktopOutput() {
        val batch = expected.getValue("batch").jsonPrimitive.int
        val want = expected.getValue("probs").jsonArray.map { it.jsonPrimitive.float }
        for (accelerator in OnnxModel.Accelerator.entries) {
            OnnxModel(modelBytes, accelerator).use { model ->
                val got = model.run(smokeInput(batch), shapeOf(batch))
                assertEquals("$accelerator output size", want.size, got.size)
                want.indices.forEach { assertEquals("$accelerator[$it]", want[it], got[it], 1e-4f) }
            }
        }
    }

    @Test
    fun logTimings() {
        for (accelerator in OnnxModel.Accelerator.entries) {
            val loadStart = System.nanoTime()
            OnnxModel(modelBytes, accelerator).use { model ->
                val loadMs = (System.nanoTime() - loadStart) / 1e6
                for (batch in intArrayOf(1, 256)) {
                    val input = smokeInput(batch)
                    val shape = shapeOf(batch)
                    repeat(3) { model.run(input, shape) }
                    val runsMs = List(10) {
                        val start = System.nanoTime()
                        model.run(input, shape)
                        (System.nanoTime() - start) / 1e6
                    }.sorted()
                    Log.i(TAG, "%s load=%.1fms batch=%d median=%.1fms min=%.1fms".format(accelerator, loadMs, batch, runsMs[5], runsMs[0]))
                }
            }
        }
    }

    private fun shapeOf(batch: Int) = longArrayOf(batch.toLong(), 3, 64, 64)

    // Same formula as smoke_input() in ml/eval/make_smoke_model.py: an integer divided once, float32-exact on both sides.
    private fun smokeInput(batch: Int): FloatArray {
        val perSample = 3 * 64 * 64
        return FloatArray(batch * perSample) { i ->
            val k = (i.toLong() * 7919 % 256).toInt()
            (k * (i / perSample + 1)).toFloat() / 1020f
        }
    }

    private companion object {
        const val TAG = "DeepSightS2"
    }
}
