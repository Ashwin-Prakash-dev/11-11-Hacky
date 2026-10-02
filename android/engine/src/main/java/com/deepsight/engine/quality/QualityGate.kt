package com.deepsight.engine.quality

import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.QualitySpec
import kotlin.math.max

/** Computes deterministic field-quality scores and applies the active pack's thresholds. */
object QualityGate {
    fun evaluate(image: PixelImage, spec: QualitySpec): QualityResult {
        val luminance = ByteArray(image.argb.size) { luminance(image.argb[it]).toByte() }
        val blurScore = laplacianVariance(luminance, image.width, image.height)
        val darkFraction = luminance.count { (it.toInt() and 0xff) == 0 }.toDouble() / luminance.size
        val brightFraction = luminance.count { (it.toInt() and 0xff) == 255 }.toDouble() / luminance.size

        val reasons = buildList {
            if (blurScore < spec.minBlur) add(QualityReason.BLUR)
            if (darkFraction > spec.maxClippedFraction) add(QualityReason.UNDEREXPOSED)
            if (brightFraction > spec.maxClippedFraction) add(QualityReason.OVEREXPOSED)
        }

        return QualityResult(
            pass = reasons.isEmpty(),
            blurScore = blurScore,
            exposureScore = max(darkFraction, brightFraction),
            reasons = reasons,
        )
    }

    private fun luminance(argb: Int): Int {
        val red = argb ushr 16 and 0xff
        val green = argb ushr 8 and 0xff
        val blue = argb and 0xff
        return (299 * red + 587 * green + 114 * blue + 500) / 1000
    }

    private fun laplacianVariance(pixels: ByteArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0

        var count = 0
        var mean = 0.0
        var sumSquaredDifferences = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = y * width + x
                val laplacian = (
                    pixels.unsigned(center - width) +
                        pixels.unsigned(center - 1) -
                        4 * pixels.unsigned(center) +
                        pixels.unsigned(center + 1) +
                        pixels.unsigned(center + width)
                    ).toDouble()

                count += 1
                val delta = laplacian - mean
                mean += delta / count
                sumSquaredDifferences += delta * (laplacian - mean)
            }
        }
        return sumSquaredDifferences / count
    }

    private fun ByteArray.unsigned(index: Int): Int = this[index].toInt() and 0xff
}
