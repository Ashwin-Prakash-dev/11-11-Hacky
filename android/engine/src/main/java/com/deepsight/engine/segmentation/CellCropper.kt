package com.deepsight.engine.segmentation

import com.deepsight.engine.quality.PixelImage

/** Integer pixel bounds, with an exclusive right and bottom edge. */
data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0 && y >= 0) { "box origin must be non-negative" }
        require(width > 0 && height > 0) { "box width and height must be positive" }
    }

    internal fun requireInside(image: PixelImage) {
        require(x.toLong() + width <= image.width && y.toLong() + height <= image.height) {
            "box $this is outside ${image.width}x${image.height} image"
        }
    }

    internal fun normalized(image: PixelImage): List<Double> = listOf(
        x.toDouble() / image.width,
        y.toDouble() / image.height,
        width.toDouble() / image.width,
        height.toDouble() / image.height,
    )
}

data class CellCrop(
    val bounds: PixelRect,
    val normalizedBbox: List<Double>,
    val image: PixelImage,
)

/** Extracts detector boxes without changing their order or pixel values. */
object CellCropper {
    fun crop(field: PixelImage, boxes: List<PixelRect>): List<CellCrop> = boxes.map { box ->
        box.requireInside(field)
        val pixels = IntArray(box.width * box.height)
        for (row in 0 until box.height) {
            field.argb.copyInto(
                destination = pixels,
                destinationOffset = row * box.width,
                startIndex = (box.y + row) * field.width + box.x,
                endIndex = (box.y + row) * field.width + box.x + box.width,
            )
        }
        CellCrop(
            bounds = box,
            normalizedBbox = box.normalized(field),
            image = PixelImage(box.width, box.height, pixels),
        )
    }
}
