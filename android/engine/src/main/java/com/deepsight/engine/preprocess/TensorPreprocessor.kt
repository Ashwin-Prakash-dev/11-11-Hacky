package com.deepsight.engine.preprocess

import com.deepsight.engine.contract.ColorOrder
import com.deepsight.engine.contract.InputSpec
import com.deepsight.engine.contract.PreprocessSpec
import com.deepsight.engine.contract.Resize
import com.deepsight.engine.contract.StainNormalization
import com.deepsight.engine.contract.TensorDtype
import com.deepsight.engine.contract.TensorLayout
import com.deepsight.engine.quality.PixelImage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Converts an Android-free pixel image to the float tensor described by a pack manifest. */
object TensorPreprocessor {
    fun preprocess(image: PixelImage, input: InputSpec, spec: PreprocessSpec): FloatArray {
        require(input.dtype == TensorDtype.FLOAT32) { "Float preprocessing requires input.dtype float32" }
        require(spec.stainNormalization == StainNormalization.NONE) {
            "Stain normalization ${spec.stainNormalization} is not implemented"
        }
        val dimensions = dimensions(input)
        val means = channelValues(spec.mean, "mean")
        val standardDeviations = channelValues(spec.std, "std")
        require(standardDeviations.all { it > 0.0 }) { "std values must be positive" }
        require(spec.scale > 0.0) { "scale must be positive" }

        val transform = transform(image, dimensions.width, dimensions.height, spec.resize)
        val channelOrder = when (input.color) {
            ColorOrder.RGB -> intArrayOf(0, 1, 2)
            ColorOrder.BGR -> intArrayOf(2, 1, 0)
        }
        val tensor = FloatArray(dimensions.width * dimensions.height * CHANNELS)
        for (y in 0 until dimensions.height) {
            for (x in 0 until dimensions.width) {
                val rgb = transform.sample(x, y)
                for (channel in 0 until CHANNELS) {
                    val value = (rgb[channelOrder[channel]] * spec.scale - means[channel]) / standardDeviations[channel]
                    val index = when (dimensions.layout) {
                        TensorLayout.NCHW -> channel * dimensions.width * dimensions.height + y * dimensions.width + x
                        TensorLayout.NHWC -> (y * dimensions.width + x) * CHANNELS + channel
                    }
                    tensor[index] = value.toFloat()
                }
            }
        }
        return tensor
    }

    private fun dimensions(input: InputSpec): Dimensions {
        val shape = requireNotNull(input.shape) { "input.shape is required" }
        val layout = requireNotNull(input.layout) { "input.layout is required" }
        require(shape.size == 4) { "input.shape must have 4 dimensions" }
        val (batch, channels, height, width) = when (layout) {
            TensorLayout.NCHW -> listOf(shape[0], shape[1], shape[2], shape[3])
            TensorLayout.NHWC -> listOf(shape[0], shape[3], shape[1], shape[2])
        }
        require(batch == 1) { "Only batch size 1 is supported" }
        require(channels == CHANNELS) { "Only 3-channel inputs are supported" }
        require(width > 0 && height > 0) { "Tensor width and height must be positive" }
        return Dimensions(width, height, layout)
    }

    private fun channelValues(values: List<Double>, name: String): DoubleArray = when (values.size) {
        1 -> DoubleArray(CHANNELS) { values.single() }
        CHANNELS -> values.toDoubleArray()
        else -> throw IllegalArgumentException("$name must contain 1 or 3 values")
    }

    private fun transform(image: PixelImage, targetWidth: Int, targetHeight: Int, resize: Resize): Transform {
        return when (resize) {
            Resize.STRETCH -> Transform(image, targetWidth, targetHeight, 0, 0, false)
            Resize.LETTERBOX -> {
                val scale = min(targetWidth.toDouble() / image.width, targetHeight.toDouble() / image.height)
                val width = max(1, (image.width * scale).roundToInt())
                val height = max(1, (image.height * scale).roundToInt())
                Transform(image, width, height, (targetWidth - width) / 2, (targetHeight - height) / 2, true)
            }
            Resize.CENTER_CROP -> {
                val scale = max(targetWidth.toDouble() / image.width, targetHeight.toDouble() / image.height)
                val width = max(1, (image.width * scale).roundToInt())
                val height = max(1, (image.height * scale).roundToInt())
                Transform(image, width, height, Math.floorDiv(targetWidth - width, 2), Math.floorDiv(targetHeight - height, 2), false)
            }
            Resize.NONE -> {
                require(image.width == targetWidth && image.height == targetHeight) {
                    "resize none requires ${targetWidth}x$targetHeight pixels, got ${image.width}x${image.height}"
                }
                Transform(image, targetWidth, targetHeight, 0, 0, false)
            }
        }
    }

    private data class Dimensions(val width: Int, val height: Int, val layout: TensorLayout)

    private class Transform(
        private val image: PixelImage,
        private val resizedWidth: Int,
        private val resizedHeight: Int,
        private val offsetX: Int,
        private val offsetY: Int,
        private val padOutside: Boolean,
    ) {
        fun sample(targetX: Int, targetY: Int): DoubleArray {
            val x = targetX - offsetX
            val y = targetY - offsetY
            if (padOutside && (x !in 0 until resizedWidth || y !in 0 until resizedHeight)) {
                return DoubleArray(CHANNELS)
            }
            val sourceX = (x + 0.5) * image.width / resizedWidth - 0.5
            val sourceY = (y + 0.5) * image.height / resizedHeight - 0.5
            return bilinear(image, sourceX, sourceY)
        }
    }

    private fun bilinear(image: PixelImage, sourceX: Double, sourceY: Double): DoubleArray {
        val x = sourceX.coerceIn(0.0, (image.width - 1).toDouble())
        val y = sourceY.coerceIn(0.0, (image.height - 1).toDouble())
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = min(x0 + 1, image.width - 1)
        val y1 = min(y0 + 1, image.height - 1)
        val xWeight = x - x0
        val yWeight = y - y0

        val topLeft = channels(image.argb[y0 * image.width + x0])
        val topRight = channels(image.argb[y0 * image.width + x1])
        val bottomLeft = channels(image.argb[y1 * image.width + x0])
        val bottomRight = channels(image.argb[y1 * image.width + x1])
        return DoubleArray(CHANNELS) { channel ->
            val top = topLeft[channel] * (1.0 - xWeight) + topRight[channel] * xWeight
            val bottom = bottomLeft[channel] * (1.0 - xWeight) + bottomRight[channel] * xWeight
            top * (1.0 - yWeight) + bottom * yWeight
        }
    }

    private fun channels(argb: Int) = doubleArrayOf(
        (argb ushr 16 and 0xff).toDouble(),
        (argb ushr 8 and 0xff).toDouble(),
        (argb and 0xff).toDouble(),
    )

    private const val CHANNELS = 3
}
