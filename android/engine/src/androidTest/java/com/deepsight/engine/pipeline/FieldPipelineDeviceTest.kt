package com.deepsight.engine.pipeline

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the smoke pack (random weights, ml/eval/make_smoke_model.py) end to end through the real ONNX Runtime. */
@RunWith(AndroidJUnit4::class)
class FieldPipelineDeviceTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun smokeFieldGivesValidFieldResultAndCaseResult() {
        val pack = PackLoader.fromAssets(assets, packsRoot = "").load("smoke")
        val expected = assets.open("smoke/expected.json").use {
            Json.parseToJsonElement(it.reader().readText()).jsonObject
        }.getValue("probs").jsonArray.map { it.jsonPrimitive.float }

        FieldPipeline(pack, OnnxModel.Accelerator.CPU).use { pipeline ->
            val field = pipeline.analyzeField("case-1", "field-1", smokeBitmap())
            Log.i("DeepSightS2", "field timing_ms=${field.timingMs}")

            assertTrue(field.quality.pass)
            assertEquals(RouterVerdict.MATCH, field.router?.verdict)
            assertEquals(setOf("quality", "router", "preprocess", "pack", "total"), field.timingMs.keys)
            // Same input as TensorPreprocessorDeviceTest. The smoke model already emits probabilities but its manifest
            // declares softmax, so the decoder softmaxes them again: score = e^p1 / (e^p0 + e^p1) of the desktop output.
            val want = Math.exp(expected[1].toDouble()) / (Math.exp(expected[0].toDouble()) + Math.exp(expected[1].toDouble()))
            assertEquals(want, field.imageScore!!, 1e-4)
            assertEquals(1, field.counts.values.sum())

            // #19: closing the case on-device applies the pack's triage rules to the real field result.
            val case = pipeline.closeCase("case-1", listOf(field))
            Log.i("DeepSightS2", "case triage=${case.triage}")
            assertEquals(listOf("field-1"), case.fieldIds)
            val positive = field.counts.getValue("positive")
            assertEquals(positive >= 1, case.triage.level == TriageLevel.ABNORMAL_FLAG)
            assertNotNull(Contracts.encode(case))
        }
    }

    // Pixel pattern identical to TensorPreprocessorDeviceTest.smokeImage().
    private fun smokeBitmap(): Bitmap {
        val side = 64
        val channel = side * side
        fun byte(i: Int) = (i.toLong() * 7919 % 256).toInt()
        val pixels = IntArray(channel) { p ->
            (0xff shl 24) or (byte(p) shl 16) or (byte(channel + p) shl 8) or byte(2 * channel + p)
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}
