package com.deepsight.engine.segmentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values come from ml/reference/nlm_segmentation.py, which matches NLM's Java bit for bit. */
class NlmHistogramTest {
    @Test
    fun retakeWhenGreenMinimumIsNotNearZero() {
        val ramp = IntArray(2010) { 30 + it % 201 }
        assertNull(NlmHistogram.stretch(ramp, 0.01, 0.99))
        ramp[0] = 0
        assertNotNull(NlmHistogram.stretch(ramp, 0.01, 0.99))
    }

    @Test
    fun stretchMatchesPythonReference() {
        val uniform = NlmHistogram.stretch(IntArray(2000) { (it * 37 + 11) % 256 }, 0.01, 0.99)!!
        assertEquals(254954, uniform.sum())
        assertEquals(2, uniform.min())
        assertEquals(253, uniform.max())
        assertArrayEquals(intArrayOf(11, 48, 85, 122, 159, 196, 233, 14, 51, 88, 125, 162), uniform.copyOf(12))
        assertEquals(88, uniform[777])
        assertEquals(246, uniform[1999])

        val skewed = NlmHistogram.stretch(IntArray(2000) { if (it == 0) 0 else 40 + (it * it) % 151 }, 0.01, 0.99)!!
        assertEquals(215963, skewed.sum())
        assertEquals(41, skewed.min())
        assertEquals(188, skewed.max())
        assertArrayEquals(intArrayOf(41, 41, 44, 49, 56, 65, 76, 89, 104, 121, 140, 161), skewed.copyOf(12))
        assertEquals(71, skewed[777])
        assertEquals(128, skewed[1999])
    }

    @Test
    fun otsuMatchesPythonReference() {
        val values = DoubleArray(3000) { (it * 7919 % 25500) / 100.0 }.also { it[0] = 0.0; it[1] = 255.0 }
        assertEquals(126, NlmHistogram.otsu(values, ByteArray(3000) { if (it % 3 == 0) 0 else 1 }))

        val bimodal = DoubleArray(4000) { if (it % 2 == 0) ((it * 13) % 60).toDouble() else (150 + (it * 7) % 90).toDouble() }
            .also { it[0] = 0.0; it[1] = 255.0 }
        assertEquals(58, NlmHistogram.otsu(bimodal, ByteArray(4000) { 1 }))
    }
}
