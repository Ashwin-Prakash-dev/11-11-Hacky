package com.deepsight.engine.pipeline

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.exp

/** Physical-device smoke/parity guard for both models in the automatic B-ALL cascade. */
@RunWith(AndroidJUnit4::class)
class LeukaemiaModelsDeviceTest {
    @Test
    fun classifierAndDetectorLoadAndProducePinnedShapes() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val pack = PackLoader.fromAssets(assets).load("leukaemia_wbc")
        val detector = checkNotNull(pack.detector)

        OnnxModel.Accelerator.entries.forEach { accelerator ->
            OnnxModel(pack.modelBytes, accelerator).use { model ->
                val logits = model.run(FloatArray(224 * 224 * 3), longArrayOf(1, 224, 224, 3))
                Log.i("DeepSightLeukaemia", "$accelerator classifier logits=${logits.joinToString()}")
                assertArrayEquals(
                    floatArrayOf(0.025188116f, 0.09122925f, 0.005286344f, 0.8782963f),
                    softmax(logits),
                    2e-5f,
                )
            }
            OnnxModel(detector.modelBytes, accelerator).use { model ->
                val output = model.run(FloatArray(3 * 640 * 640), longArrayOf(1, 3, 640, 640))
                assertEquals(7 * 33_600, output.size)
                assertTrue(output.all(Float::isFinite))
            }
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exponentials = logits.map { exp((it - max).toDouble()) }
        val sum = exponentials.sum()
        return FloatArray(logits.size) { (exponentials[it] / sum).toFloat() }
    }
}
