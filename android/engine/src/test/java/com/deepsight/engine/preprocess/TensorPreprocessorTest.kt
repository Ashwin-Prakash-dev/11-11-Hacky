package com.deepsight.engine.preprocess

import com.deepsight.engine.contract.ColorOrder
import com.deepsight.engine.contract.InputSource
import com.deepsight.engine.contract.InputSpec
import com.deepsight.engine.contract.PreprocessSpec
import com.deepsight.engine.contract.Resize
import com.deepsight.engine.contract.TensorDtype
import com.deepsight.engine.contract.TensorLayout
import com.deepsight.engine.quality.PixelImage
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TensorPreprocessorTest {
    @Test
    fun `applies BGR scale mean and std in NCHW order`() {
        val image = image(1, 1, rgb(10, 20, 30))
        val input = input(shape = listOf(1, 3, 1, 1), layout = TensorLayout.NCHW, color = ColorOrder.BGR)
        val preprocess = preprocess(scale = 0.5, mean = listOf(1.0, 2.0, 3.0), std = listOf(1.0, 2.0, 4.0))

        val tensor = TensorPreprocessor.preprocess(image, input, preprocess)

        assertArrayEquals(floatArrayOf(14f, 4f, 0.5f), tensor, 0f)
    }

    @Test
    fun `writes channels interleaved for NHWC`() {
        val image = image(2, 1, rgb(1, 2, 3), rgb(4, 5, 6))
        val input = input(shape = listOf(1, 1, 2, 3), layout = TensorLayout.NHWC)

        val tensor = TensorPreprocessor.preprocess(image, input, preprocess())

        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), tensor, 0f)
    }

    @Test
    fun `stretch uses bilinear sampling at pixel centers`() {
        val image = image(2, 1, rgb(0, 0, 0), rgb(255, 255, 255))
        val input = input(shape = listOf(1, 3, 1, 1), layout = TensorLayout.NCHW)

        val tensor = TensorPreprocessor.preprocess(image, input, preprocess(resize = Resize.STRETCH))

        assertArrayEquals(floatArrayOf(127.5f, 127.5f, 127.5f), tensor, 1e-5f)
    }

    @Test
    fun `letterbox centers resized pixels and pads with black`() {
        val image = image(2, 1, rgb(255, 0, 0), rgb(0, 0, 255))
        val input = input(shape = listOf(1, 3, 3, 2), layout = TensorLayout.NCHW)

        val tensor = TensorPreprocessor.preprocess(image, input, preprocess(resize = Resize.LETTERBOX))

        assertArrayEquals(
            floatArrayOf(
                0f, 0f, 255f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 255f, 0f, 0f,
            ),
            tensor,
            0f,
        )
    }

    @Test
    fun `center crop keeps the centered region`() {
        val image = image(3, 1, rgb(255, 0, 0), rgb(0, 255, 0), rgb(0, 0, 255))
        val input = input(shape = listOf(1, 3, 1, 1), layout = TensorLayout.NCHW)

        val tensor = TensorPreprocessor.preprocess(image, input, preprocess(resize = Resize.CENTER_CROP))

        assertArrayEquals(floatArrayOf(0f, 255f, 0f), tensor, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `none rejects a source size that differs from the tensor`() {
        val image = image(2, 1, rgb(0, 0, 0), rgb(0, 0, 0))
        val input = input(shape = listOf(1, 3, 2, 2), layout = TensorLayout.NCHW)

        TensorPreprocessor.preprocess(image, input, preprocess(resize = Resize.NONE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `float preprocessor rejects uint8 tensors`() {
        val image = image(1, 1, rgb(0, 0, 0))
        val input = input(shape = listOf(1, 3, 1, 1), layout = TensorLayout.NCHW).copy(dtype = TensorDtype.UINT8)

        TensorPreprocessor.preprocess(image, input, preprocess())
    }

    private fun input(shape: List<Int>, layout: TensorLayout, color: ColorOrder = ColorOrder.RGB) = InputSpec(
        tensor = "input",
        shape = shape,
        layout = layout,
        dtype = TensorDtype.FLOAT32,
        color = color,
    )

    private fun preprocess(
        resize: Resize = Resize.STRETCH,
        scale: Double = 1.0,
        mean: List<Double> = listOf(0.0, 0.0, 0.0),
        std: List<Double> = listOf(1.0, 1.0, 1.0),
    ) = PreprocessSpec(
        source = InputSource.FIELD,
        resize = resize,
        scale = scale,
        mean = mean,
        std = std,
    )

    private fun image(width: Int, height: Int, vararg pixels: Int) = PixelImage(width, height, pixels)

    private fun rgb(red: Int, green: Int, blue: Int) =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}
