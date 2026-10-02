package com.deepsight.engine.preprocess

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.quality.PixelImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TensorPreprocessorDeviceTest {
    @Test
    fun manifestPreprocessingMatchesDesktopSmokeGolden() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val manifest = assets.open("smoke/manifest.json").use {
            Contracts.parseManifest(it.reader().readText())
        }
        val expected = assets.open("smoke/expected.json").use {
            Json.parseToJsonElement(it.reader().readText()).jsonObject
        }
        val allExpected = expected.getValue("probs").jsonArray.map { it.jsonPrimitive.float }
        val expectedBatch = expected.getValue("batch").jsonPrimitive.int
        val want = allExpected.take(allExpected.size / expectedBatch)
        val tensor = TensorPreprocessor.preprocess(smokeImage(), manifest.input, manifest.preprocess)
        val shape = requireNotNull(manifest.input.shape).map(Int::toLong).toLongArray()
        val modelBytes = assets.open("smoke/model.onnx").use { it.readBytes() }

        OnnxModel(modelBytes, OnnxModel.Accelerator.CPU).use { model ->
            val got = model.run(tensor, shape)
            assertEquals(want.size, got.size)
            want.indices.forEach { assertEquals("output[$it]", want[it], got[it], 1e-4f) }
        }
    }

    private fun smokeImage(): PixelImage {
        val side = 64
        val channelSize = side * side
        val pixels = IntArray(channelSize) { pixel ->
            val red = smokeByte(pixel)
            val green = smokeByte(channelSize + pixel)
            val blue = smokeByte(2 * channelSize + pixel)
            (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return PixelImage(side, side, pixels)
    }

    private fun smokeByte(index: Int) = (index.toLong() * 7919 % 256).toInt()
}
