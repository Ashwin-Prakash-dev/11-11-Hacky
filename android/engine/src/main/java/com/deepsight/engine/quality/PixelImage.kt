package com.deepsight.engine.quality

/** Android-free image boundary shared by JVM-testable preprocessing code. */
class PixelImage(
    val width: Int,
    val height: Int,
    argb: IntArray,
) {
    internal val argb: IntArray = argb.copyOf()

    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(argb.size.toLong() == width.toLong() * height) {
            "pixel count ${argb.size} does not match ${width}x$height"
        }
    }
}
