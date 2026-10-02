package com.deepsight.engine.golden

import com.deepsight.engine.contract.DetectedObject
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.UncertaintyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenComparatorTest {
    private val tol = GoldenTolerance(score = 0.02, boxPx = 2.0)

    private val expected = FieldResult(
        caseId = "golden", fieldId = "golden", packId = "p", packVersion = "0.1.0",
        quality = QualityResult(pass = true, blurScore = 400.0, exposureScore = 0.01),
        router = RouterResult(RouterVerdict.MATCH, 1.0),
        objects = listOf(
            DetectedObject("positive", 0.90, listOf(0.10, 0.20, 0.10, 0.10)),
            DetectedObject("negative", 0.80, listOf(0.50, 0.50, 0.10, 0.10)),
        ),
        counts = mapOf("positive" to 1, "negative" to 1),
        imageScore = 0.90,
        uncertainty = UncertaintyResult(flag = false),
    )

    private fun diffs(actual: FieldResult) = GoldenComparator.compare(expected, actual, tol, imageWidth = 1000, imageHeight = 1000)

    @Test
    fun `identical results match and ids, timing and numeric quality scores are ignored`() {
        val actual = expected.copy(
            caseId = "other", fieldId = "other", timingMs = mapOf("total" to 9),
            quality = expected.quality.copy(blurScore = 123.0, exposureScore = 0.5),
        )
        assertEquals(emptyList<String>(), diffs(actual))
    }

    @Test
    fun `scores within tolerance match and beyond it fail`() {
        assertTrue(diffs(expected.copy(imageScore = 0.91)).isEmpty())
        assertTrue(diffs(expected.copy(imageScore = 0.93)).single().contains("image_score"))
        val moved = expected.copy(objects = listOf(expected.objects[0].copy(score = 0.95), expected.objects[1]))
        assertTrue(diffs(moved).single().contains("objects[0].score"))
    }

    @Test
    fun `counts must match exactly`() {
        assertTrue(diffs(expected.copy(counts = mapOf("positive" to 2, "negative" to 1))).single().contains("counts"))
    }

    @Test
    fun `label change and object count change fail`() {
        assertTrue(diffs(expected.copy(objects = listOf(expected.objects[0].copy(label = "negative"), expected.objects[1]))).any { it.contains("objects[0].label") })
        assertTrue(diffs(expected.copy(objects = expected.objects.take(1))).any { it.contains("objects.size") })
    }

    @Test
    fun `bbox tolerance is in pixels of the field image`() {
        // 1000 px image: 0.002 = 2 px passes, 0.003 = 3 px fails.
        fun shifted(dx: Double) = expected.copy(objects = listOf(expected.objects[0].copy(bbox = listOf(0.10 + dx, 0.20, 0.10, 0.10)), expected.objects[1]))
        assertTrue(diffs(shifted(0.002)).isEmpty())
        assertTrue(diffs(shifted(0.003)).single().contains("objects[0].bbox"))
    }

    @Test
    fun `quality, router and uncertainty differences fail`() {
        val rejected = expected.copy(quality = expected.quality.copy(pass = false, reasons = listOf(QualityReason.BLUR)))
        assertTrue(diffs(rejected).any { it.contains("quality.pass") })
        assertTrue(diffs(expected.copy(router = RouterResult(RouterVerdict.MISMATCH, 0.1))).any { it.contains("router.verdict") })
        assertTrue(diffs(expected.copy(uncertainty = UncertaintyResult(flag = true, reason = "x"))).any { it.contains("uncertainty.flag") })
    }

    @Test
    fun `null expected image score requires null actual`() {
        val rejected = expected.copy(imageScore = null)
        assertTrue(diffs(rejected.copy(imageScore = 0.5)).single().contains("image_score"))
    }
}
