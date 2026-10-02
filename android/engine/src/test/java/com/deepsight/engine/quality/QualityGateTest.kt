package com.deepsight.engine.quality

import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualitySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGateTest {
    @Test
    fun `sharp well-exposed field passes`() {
        val result = QualityGate.evaluate(checkerboard(8, 64, 192), thresholds())

        assertTrue(result.pass)
        assertEquals(262_144.0, result.blurScore, 0.0)
        assertEquals(0.0, result.exposureScore, 0.0)
        assertEquals(emptyList<QualityReason>(), result.reasons)
    }

    @Test
    fun `blurred field is rejected for blur`() {
        val result = QualityGate.evaluate(solid(8, 128), thresholds())

        assertFalse(result.pass)
        assertEquals(0.0, result.blurScore, 0.0)
        assertEquals(listOf(QualityReason.BLUR), result.reasons)
    }

    @Test
    fun `bright clipped field is rejected for overexposure`() {
        val result = QualityGate.evaluate(checkerboard(8, 128, 255), thresholds())

        assertFalse(result.pass)
        assertTrue(result.blurScore > 1.0)
        assertEquals(0.5, result.exposureScore, 0.0)
        assertEquals(listOf(QualityReason.OVEREXPOSED), result.reasons)
    }

    @Test
    fun `dark and bright clipping report both reasons in stable order`() {
        val result = QualityGate.evaluate(checkerboard(8, 0, 255), thresholds())

        assertEquals(
            listOf(QualityReason.UNDEREXPOSED, QualityReason.OVEREXPOSED),
            result.reasons,
        )
    }

    @Test
    fun `threshold boundaries pass`() {
        val image = checkerboard(8, 128, 255)
        val scores = QualityGate.evaluate(image, QualitySpec(minBlur = 0.0, maxClippedFraction = 1.0))

        val result = QualityGate.evaluate(
            image,
            QualitySpec(
                minBlur = scores.blurScore,
                maxClippedFraction = scores.exposureScore,
            ),
        )

        assertTrue(result.pass)
        assertEquals(emptyList<QualityReason>(), result.reasons)
    }

    @Test
    fun `same sparse field uses pack-specific blur threshold`() {
        val sparseField = verticalEdge(9, 64, 192)
        val score = QualityGate.evaluate(
            sparseField,
            QualitySpec(minBlur = 0.0, maxClippedFraction = 1.0),
        ).blurScore

        val calibratedPack = QualityGate.evaluate(
            sparseField,
            QualitySpec(minBlur = score, maxClippedFraction = 0.05),
        )
        val stricterPack = QualityGate.evaluate(
            sparseField,
            QualitySpec(minBlur = score + 1.0, maxClippedFraction = 0.05),
        )

        assertTrue(calibratedPack.pass)
        assertFalse(stricterPack.pass)
        assertEquals(listOf(QualityReason.BLUR), stricterPack.reasons)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pixel count must match dimensions`() {
        PixelImage(width = 2, height = 2, argb = intArrayOf(0, 0, 0))
    }

    private fun thresholds() = QualitySpec(minBlur = 1.0, maxClippedFraction = 0.05)

    private fun solid(size: Int, value: Int) = image(size) { _, _ -> value }

    private fun checkerboard(size: Int, low: Int, high: Int) =
        image(size) { x, y -> if ((x + y) % 2 == 0) low else high }

    private fun verticalEdge(size: Int, low: Int, high: Int) =
        image(size) { x, _ -> if (x < size / 2) low else high }

    private fun image(size: Int, grayAt: (x: Int, y: Int) -> Int): PixelImage {
        val pixels = IntArray(size * size) { index ->
            val gray = grayAt(index % size, index / size)
            (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        return PixelImage(width = size, height = size, argb = pixels)
    }
}
