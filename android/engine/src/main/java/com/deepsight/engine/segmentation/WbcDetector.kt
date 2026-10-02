package com.deepsight.engine.segmentation

import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.DetectorSpec
import com.deepsight.engine.quality.PixelImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** SatellaDet field detector. It only proposes WBC crops; it never decides classification or triage. */
class WbcDetector(
    modelBytes: ByteArray,
    private val spec: DetectorSpec,
    accelerator: OnnxModel.Accelerator = OnnxModel.Accelerator.XNNPACK,
) : AutoCloseable {
    private val model = OnnxModel(modelBytes, accelerator)

    fun detect(field: PixelImage): List<PixelRect> {
        val prepared = prepare(field, spec)
        val output = model.run(prepared.tensor, spec.inputShape.map(Int::toLong).toLongArray())
        val channels = 4 + spec.labels.size
        require(output.size % channels == 0) {
            "Detector output has ${output.size} values, not [1,$channels,N]"
        }
        return WbcDetectionPostprocessor.decode(
            predictions = output,
            candidates = output.size / channels,
            fieldWidth = field.width,
            fieldHeight = field.height,
            resizedWidth = prepared.resizedWidth,
            resizedHeight = prepared.resizedHeight,
            padX = prepared.padX,
            padY = prepared.padY,
            spec = spec,
        )
    }

    override fun close() = model.close()

    private data class Prepared(
        val tensor: FloatArray,
        val resizedWidth: Int,
        val resizedHeight: Int,
        val padX: Int,
        val padY: Int,
    )

    private fun prepare(field: PixelImage, spec: DetectorSpec): Prepared {
        val targetHeight = spec.inputShape[2]
        val targetWidth = spec.inputShape[3]
        val scale = min(targetWidth.toDouble() / field.width, targetHeight.toDouble() / field.height)
        val resizedWidth = max(1, (field.width * scale).roundToInt())
        val resizedHeight = max(1, (field.height * scale).roundToInt())
        val padX = (targetWidth - resizedWidth) / 2
        val padY = (targetHeight - resizedHeight) / 2
        val plane = targetWidth * targetHeight
        val pad = (spec.letterboxValue * spec.pixelScale).toFloat()
        val tensor = FloatArray(plane * 3) { pad }
        for (y in 0 until resizedHeight) {
            val sourceY = (y + 0.5) * field.height / resizedHeight - 0.5
            for (x in 0 until resizedWidth) {
                val sourceX = (x + 0.5) * field.width / resizedWidth - 0.5
                val rgb = bilinear(field, sourceX, sourceY)
                val index = (y + padY) * targetWidth + x + padX
                for (channel in 0..2) tensor[channel * plane + index] = (rgb[channel] * spec.pixelScale).toFloat()
            }
        }
        return Prepared(tensor, resizedWidth, resizedHeight, padX, padY)
    }

    private fun bilinear(image: PixelImage, sourceX: Double, sourceY: Double): DoubleArray {
        val x = sourceX.coerceIn(0.0, (image.width - 1).toDouble())
        val y = sourceY.coerceIn(0.0, (image.height - 1).toDouble())
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val x1 = min(x0 + 1, image.width - 1)
        val y1 = min(y0 + 1, image.height - 1)
        val wx = x - x0
        val wy = y - y0
        fun channels(px: Int) = doubleArrayOf((px ushr 16 and 0xff).toDouble(), (px ushr 8 and 0xff).toDouble(), (px and 0xff).toDouble())
        val tl = channels(image.argb[y0 * image.width + x0])
        val tr = channels(image.argb[y0 * image.width + x1])
        val bl = channels(image.argb[y1 * image.width + x0])
        val br = channels(image.argb[y1 * image.width + x1])
        return DoubleArray(3) { c ->
            val top = tl[c] * (1 - wx) + tr[c] * wx
            val bottom = bl[c] * (1 - wx) + br[c] * wx
            top * (1 - wy) + bottom * wy
        }
    }
}

internal object WbcDetectionPostprocessor {
    private data class ScoredBox(val left: Double, val top: Double, val right: Double, val bottom: Double, val score: Float)

    fun decode(
        predictions: FloatArray,
        candidates: Int,
        fieldWidth: Int,
        fieldHeight: Int,
        resizedWidth: Int,
        resizedHeight: Int,
        padX: Int,
        padY: Int,
        spec: DetectorSpec,
    ): List<PixelRect> {
        require(predictions.size == (4 + spec.labels.size) * candidates)
        val wbc = spec.labels.indexOf("wbc").also { require(it >= 0) }
        val scale = min(resizedWidth.toDouble() / fieldWidth, resizedHeight.toDouble() / fieldHeight)
        val boxes = buildList {
            for (i in 0 until candidates) {
                val score = predictions[(4 + wbc) * candidates + i]
                if (!score.isFinite() || score < spec.scoreThreshold) continue
                val cx = predictions[i]
                val cy = predictions[candidates + i]
                val width = predictions[2 * candidates + i]
                val height = predictions[3 * candidates + i]
                if (!cx.isFinite() || !cy.isFinite() || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) continue
                val left = (cx - width / 2.0 - padX) / scale
                val top = (cy - height / 2.0 - padY) / scale
                val right = (cx + width / 2.0 - padX) / scale
                val bottom = (cy + height / 2.0 - padY) / scale
                val padWidth = (right - left) * spec.cropPaddingFraction
                val padHeight = (bottom - top) * spec.cropPaddingFraction
                val clippedLeft = (left - padWidth).coerceIn(0.0, fieldWidth.toDouble())
                val clippedTop = (top - padHeight).coerceIn(0.0, fieldHeight.toDouble())
                val clippedRight = (right + padWidth).coerceIn(0.0, fieldWidth.toDouble())
                val clippedBottom = (bottom + padHeight).coerceIn(0.0, fieldHeight.toDouble())
                if (clippedRight > clippedLeft && clippedBottom > clippedTop) {
                    add(ScoredBox(clippedLeft, clippedTop, clippedRight, clippedBottom, score))
                }
            }
        }.sortedByDescending(ScoredBox::score)

        val kept = mutableListOf<ScoredBox>()
        boxes.forEach { candidate ->
            if (kept.none { iou(candidate, it) > spec.iouThreshold }) kept += candidate
        }
        return kept.mapNotNull { box ->
            val left = floor(box.left).toInt().coerceIn(0, fieldWidth - 1)
            val top = floor(box.top).toInt().coerceIn(0, fieldHeight - 1)
            val right = ceil(box.right).toInt().coerceIn(left + 1, fieldWidth)
            val bottom = ceil(box.bottom).toInt().coerceIn(top + 1, fieldHeight)
            PixelRect(left, top, right - left, bottom - top)
        }
    }

    private fun iou(a: ScoredBox, b: ScoredBox): Double {
        val intersectionWidth = max(0.0, min(a.right, b.right) - max(a.left, b.left))
        val intersectionHeight = max(0.0, min(a.bottom, b.bottom) - max(a.top, b.top))
        val intersection = intersectionWidth * intersectionHeight
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - intersection
        return if (union <= 0.0) 0.0 else intersection / union
    }
}
