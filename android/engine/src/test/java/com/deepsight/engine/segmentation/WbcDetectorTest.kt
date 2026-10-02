package com.deepsight.engine.segmentation

import com.deepsight.engine.pack.DetectorSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class WbcDetectorTest {
    @Test
    fun `decodes WBC boxes through letterbox coordinates and suppresses overlap`() {
        val predictions = FloatArray(7 * 3)
        candidate(predictions, 3, 0, cx = 320f, cy = 320f, width = 200f, height = 100f, wbc = 0.90f)
        candidate(predictions, 3, 1, cx = 325f, cy = 320f, width = 200f, height = 100f, wbc = 0.80f)
        candidate(predictions, 3, 2, cx = 80f, cy = 80f, width = 40f, height = 40f, wbc = 0.10f, rbc = 0.99f)

        val boxes = WbcDetectionPostprocessor.decode(
            predictions = predictions,
            candidates = 3,
            fieldWidth = 320,
            fieldHeight = 160,
            resizedWidth = 640,
            resizedHeight = 320,
            padX = 0,
            padY = 160,
            spec = spec(),
        )

        assertEquals(listOf(PixelRect(x = 100, y = 50, width = 120, height = 60)), boxes)
    }

    @Test
    fun `returns no boxes when no WBC score reaches threshold`() {
        val predictions = FloatArray(7).also { candidate(it, 1, 0, 100f, 100f, 50f, 50f, wbc = 0.24f, rbc = 0.99f) }

        assertEquals(
            emptyList<PixelRect>(),
            WbcDetectionPostprocessor.decode(predictions, 1, 640, 640, 640, 640, 0, 0, spec()),
        )
    }

    private fun spec() = DetectorSpec(
        file = "wbc_detector.onnx",
        sha256 = "0".repeat(64),
        inputTensor = "images",
        outputTensor = "output0",
        inputShape = listOf(1, 3, 640, 640),
        labels = listOf("wbc", "rbc", "platelets"),
        scoreThreshold = 0.25,
        iouThreshold = 0.45,
        cropPaddingFraction = 0.1,
        pixelScale = 1.0 / 255.0,
        letterboxValue = 114,
    )

    private fun candidate(
        values: FloatArray,
        count: Int,
        index: Int,
        cx: Float,
        cy: Float,
        width: Float,
        height: Float,
        wbc: Float,
        rbc: Float = 0f,
        platelets: Float = 0f,
    ) {
        listOf(cx, cy, width, height, wbc, rbc, platelets).forEachIndexed { channel, value ->
            values[channel * count + index] = value
        }
    }
}
