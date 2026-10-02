package com.deepsight.engine.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.PackLoader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device smoke/parity guard for both models in the automatic B-ALL cascade. */
@RunWith(AndroidJUnit4::class)
class LeukaemiaModelsDeviceTest {
    @Test
    fun classifierAndDetectorLoadAndProducePinnedShapes() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val pack = PackLoader.fromAssets(assets).load("leukaemia_wbc")
        val detector = checkNotNull(pack.detector)

        OnnxModel(pack.modelBytes, OnnxModel.Accelerator.CPU).use { model ->
            val logits = model.run(FloatArray(224 * 224 * 3), longArrayOf(1, 224, 224, 3))
            assertArrayEquals(floatArrayOf(-0.38546613f, 0.9015371f, -1.9467115f, 3.1661456f), logits, 1e-4f)
        }
        OnnxModel(detector.modelBytes, OnnxModel.Accelerator.CPU).use { model ->
            val output = model.run(FloatArray(3 * 640 * 640), longArrayOf(1, 3, 640, 640))
            assertEquals(7 * 33_600, output.size)
            assertTrue(output.all(Float::isFinite))
        }
    }
}
