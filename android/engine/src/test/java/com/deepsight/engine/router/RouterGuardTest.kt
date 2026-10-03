package com.deepsight.engine.router

import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.quality.PixelImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouterGuardTest {
    private val image = PixelImage(1, 1, intArrayOf(0xff808080.toInt()))
    private val labels = listOf("malaria_thin", "breast_breakhis", "reject")

    @Test
    fun selectedPackWinningIsMatch() {
        val result = guard(0.8f, 0.1f, 0.1f).evaluate(image, "malaria_thin")

        assertEquals(RouterVerdict.MATCH, result.verdict)
        assertEquals(0.8, result.score, 1e-6)
        assertNull(result.predicted)
    }

    @Test
    fun anotherPackWinningIsMismatchWithPrediction() {
        val result = guard(0.1f, 0.7f, 0.2f).evaluate(image, "malaria_thin")

        assertEquals(RouterVerdict.MISMATCH, result.verdict)
        assertEquals(0.7, result.score, 1e-6)
        assertEquals("breast_breakhis", result.predicted)
    }

    @Test
    fun rejectClassWinningIsRejectWithoutPrediction() {
        val result = guard(0.1f, 0.2f, 0.7f).evaluate(image, "malaria_thin")

        assertEquals(RouterVerdict.REJECT, result.verdict)
        assertEquals(0.7, result.score, 1e-6)
        assertNull(result.predicted)
    }

    @Test
    fun tiesUseStableLabelOrder() {
        val result = guard(0.4f, 0.4f, 0.2f).evaluate(image, "breast_breakhis")

        assertEquals(RouterVerdict.MISMATCH, result.verdict)
        assertEquals("malaria_thin", result.predicted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun selectedPackMustBeAClassifierLabel() {
        guard(0.8f, 0.1f, 0.1f).evaluate(image, "leukaemia_wbc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun outputSizeMustMatchLabels() {
        ScoreRouterGuard(labels) { floatArrayOf(0.5f, 0.5f) }.evaluate(image, "malaria_thin")
    }

    @Test(expected = IllegalArgumentException::class)
    fun scoresMustBeFiniteProbabilities() {
        guard(Float.NaN, 0.5f, 0.5f).evaluate(image, "malaria_thin")
    }

    private fun guard(vararg scores: Float) = ScoreRouterGuard(labels) { scores }
}
