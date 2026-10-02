package com.deepsight.engine.segmentation

import com.deepsight.engine.quality.PixelImage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CellCropperTest {
    @Test
    fun `extracts exact pixels and normalized bbox`() {
        val image = image(
            width = 4,
            height = 3,
            pixels = IntArray(12) { it },
        )

        val crop = CellCropper.crop(image, listOf(PixelRect(x = 1, y = 1, width = 2, height = 2))).single()

        assertEquals(2, crop.image.width)
        assertEquals(2, crop.image.height)
        assertArrayEquals(intArrayOf(5, 6, 9, 10), crop.image.argb)
        assertEquals(listOf(0.25, 1.0 / 3.0, 0.5, 2.0 / 3.0), crop.normalizedBbox)
    }

    @Test
    fun `preserves detector box order`() {
        val image = image(width = 3, height = 1, pixels = intArrayOf(10, 20, 30))
        val boxes = listOf(
            PixelRect(x = 2, y = 0, width = 1, height = 1),
            PixelRect(x = 0, y = 0, width = 1, height = 1),
        )

        val crops = CellCropper.crop(image, boxes)

        assertEquals(listOf(boxes[0], boxes[1]), crops.map { it.bounds })
        assertEquals(listOf(30, 10), crops.map { it.image.argb.single() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a box outside the field`() {
        val image = image(width = 4, height = 3, pixels = IntArray(12))

        CellCropper.crop(image, listOf(PixelRect(x = 3, y = 2, width = 2, height = 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an empty box`() {
        PixelRect(x = 0, y = 0, width = 0, height = 1)
    }

    private fun image(width: Int, height: Int, pixels: IntArray) = PixelImage(width, height, pixels)
}
