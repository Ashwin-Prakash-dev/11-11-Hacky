// SPDX-License-Identifier: GPL-3.0-only
// Port of NLM Malaria Screener (commit c485a21) Histogram.java, OtsuThreshold.java and
// MarkerBasedWatershed.stretchHist_8bit, via ml/reference/nlm_segmentation.py. Original: Copyright 2020 The Malaria
// Screener Authors, developed under contract funded by the National Library of Medicine, GPL v3.0.
// Modified 2026-10-02 by the DeepSight team: translated to Kotlin. See LICENSING.md.
package com.deepsight.engine.segmentation

import kotlin.math.min

/** The pure-arithmetic parts of NLM's segmentation, kept free of OpenCV so JVM tests can run them. */
internal object NlmHistogram {
    /**
     * stretchHist_8bit: clips green values (0..255) to the 1%/99% histogram centers and rounds half to even.
     * Null when Histogram.runHistogram raises the retake flag: its bins ignore the minimum, so any channel whose
     * minimum is not near 0 is rejected.
     */
    fun stretch(green: IntArray, minPercent: Double, maxPercent: Double): IntArray? {
        val minValue = green.min().toDouble()
        val maxValue = green.max().toDouble()
        val range = (maxValue - minValue) / 256
        val hist = IntArray(256)
        for (v in green) {
            val h = (v / range).toInt()                     // Java (int): truncation, NaN -> 0, +inf -> MAX_VALUE
            when {
                h == 256 -> hist[255]++
                h > 256 -> return null
                else -> hist[h]++
            }
        }
        val dist = (maxValue - minValue) / (256 * 2)
        val centers = DoubleArray(256)
        centers[255] = maxValue - dist
        for (i in 1 until 256) centers[255 - i] = (maxValue - dist) - dist * 2 * i

        val cumulative = DoubleArray(256)
        var running = 0.0
        for (i in 0 until 256) {
            running += hist[i].toFloat()
            cumulative[i] = running
        }
        val total = cumulative[255]
        val lower = (0 until 256).firstOrNull { cumulative[it] / total >= minPercent } ?: 0
        val upper = (0 until 256).firstOrNull { cumulative[it] / total >= maxPercent } ?: 0
        val lo = centers[lower].toFloat()
        val hi = centers[upper].toFloat()
        return IntArray(green.size) { i ->
            val g = green[i].toFloat()
            val cl = if (g < lo) 1f else 0f
            val cu = if (g > hi) 1f else 0f
            val v = g * (1f - cl) * (1f - cu) + cl * lo + cu * hi
            Math.rint(v.toDouble()).toInt().coerceIn(0, 255)
        }
    }

    /** OtsuThreshold.runOtsuThreshold on values already normalized to 0..255, counting pixels where mask == 1. */
    fun otsu(normalized: DoubleArray, mask: ByteArray): Int {
        val histData = IntArray(256)
        var total = 0
        for (i in normalized.indices) {
            if (mask[i].toInt() == 1) {
                histData[min(normalized[i].toInt(), 255)]++
                total++
            }
        }
        var sum = 0f
        for (t in 0 until 256) sum += (t * histData[t]).toFloat()
        var sumB = 0f
        var wB = 0
        var varMax = 0f
        var threshold = 0
        for (t in 0 until 256) {
            wB += histData[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += (t * histData[t]).toFloat()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val varBetween = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }
        return threshold
    }
}
