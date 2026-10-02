package com.deepsight.report

import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.contract.TriageResult
import com.deepsight.engine.contract.UncertaintyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Values from contracts/examples: one passed field, one rejected for blur, ABNORMAL_FLAG by parasite_seen. */
class CaseReportTest {
    private val passed = FieldResult(
        caseId = "case-0001", fieldId = "field-01", packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = true, blurScore = 412.7, exposureScore = 0.012),
        counts = mapOf("parasitized" to 1, "uninfected" to 2),
    )
    private val rejected = FieldResult(
        caseId = "case-0001", fieldId = "field-02", packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = false, blurScore = 37.2, exposureScore = 0.004, reasons = listOf(QualityReason.BLUR)),
    )
    private val case = CaseResult(
        caseId = "case-0001", packId = "malaria_thin", packVersion = "0.1.0", fieldIds = listOf("field-01", "field-02"),
        fieldsPassed = 1, counts = mapOf("parasitized" to 1, "uninfected" to 2), imageScore = 0.93,
        uncertainty = UncertaintyResult(flag = false),
        triage = TriageResult(TriageLevel.ABNORMAL_FLAG, "parasite_seen", provisional = true),
    )
    private val otherLevels = listOf("NORMAL_SCREEN", "NEEDS_EXPERT")

    @Test
    fun templateStatesTheFactsAndTheExactLevel() {
        assertEquals(
            "Malaria (thin smear) screen. 1 of 2 fields passed the image-quality check; 1 was rejected (blur). " +
                "Model counts across the passed fields: parasitized 1, uninfected 2. " +
                "Triage: ABNORMAL_FLAG (rule parasite_seen). A screening rule flagged this case. A clinician should review it. " +
                "The thresholds are provisional and not clinically validated. This is screening support, not a diagnosis.",
            templateReport(case, listOf(passed, rejected), "Malaria (thin smear)"),
        )
    }

    @Test
    fun templatePassesItsOwnCheckForEveryLevel() {
        TriageLevel.entries.forEach { level ->
            val text = templateReport(case.copy(triage = case.triage.copy(level = level)), listOf(passed), "Test")
            assertTrue("$level: $text", checkNarrative(text, level).ok)
        }
    }

    @Test
    fun templateExplainsEngineRulesAndUncertainty() {
        val expert = case.copy(
            fieldsPassed = 0, counts = emptyMap(), uncertainty = UncertaintyResult(flag = true, reason = "score band"),
            triage = TriageResult(TriageLevel.NEEDS_EXPERT, "engine.insufficient_fields", provisional = true),
        )
        val text = templateReport(expert, listOf(rejected), "Malaria (thin smear)")
        assertTrue(text, "0 of 2 fields passed the image-quality check; 1 was rejected (blur)." in text)
        assertTrue(text, "No passed field, so nothing was counted." in text)
        assertTrue(text, "Triage: NEEDS_EXPERT (engine.insufficient_fields: not enough fields passed the quality check)." in text)
        assertTrue(text, "The model was uncertain on part of this case (score band)." in text)
    }

    @Test
    fun promptHoldsOnlyTheFactsAndTheExactLevel() {
        val prompt = gemmaPrompt(case, listOf(passed, rejected), "Malaria (thin smear)")
        listOf(
            "State the triage level in exactly this form: \"triage level ABNORMAL_FLAG\".",
            "Model counts across the passed fields: parasitized 1, uninfected 2",
            "1 of 2 fields passed the image-quality check; 1 was rejected (blur)",
            "not a diagnosis",
            "No heading, no list, no markdown.",
        ).forEach { assertTrue("missing '$it' in:\n$prompt", it in prompt) }
        otherLevels.forEach { assertFalse("prompt names $it", it in prompt) }
    }

    @Test
    fun checkNeedsTheExactLevelAndNoOther() {
        assertTrue(checkNarrative("Triage is ABNORMAL_FLAG; a clinician should review.", TriageLevel.ABNORMAL_FLAG).ok)
        assertEquals("does not state the triage level ABNORMAL_FLAG",
            checkNarrative("A clinician should review this abnormal case.", TriageLevel.ABNORMAL_FLAG).reason)
        assertEquals("names another triage level: NORMAL_SCREEN",
            checkNarrative("ABNORMAL_FLAG, though it could be NORMAL_SCREEN.", TriageLevel.ABNORMAL_FLAG).reason)
        assertEquals("is empty", checkNarrative("  ", TriageLevel.ABNORMAL_FLAG).reason)
        // ABNORMAL_FLAG contains "NORMAL" but is not NORMAL_SCREEN; tokens must match whole.
        assertTrue(checkNarrative("Flag: ABNORMAL_FLAG.", TriageLevel.ABNORMAL_FLAG).ok)
        assertFalse(checkNarrative("XABNORMAL_FLAG", TriageLevel.ABNORMAL_FLAG).ok)
    }

    @Test
    fun cleanDropsMarkdownAndHeadings() {
        assertEquals(
            "Test: malaria thin smear. Triage: ABNORMAL_FLAG (rule parasite_seen).",
            cleanNarrative("**Screening Note:**\n\nTest: malaria thin smear.\n* Triage: **ABNORMAL_FLAG** (rule parasite_seen).\n"),
        )
        assertEquals("One sentence.", cleanNarrative("# Note\nOne sentence."))
    }

    @Test
    fun meaningsNeverDiagnose() {
        TriageLevel.entries.forEach { level ->
            val meaning = triageMeaning(level).lowercase()
            listOf("diagnos", "positive", "negative", "malaria", "infected").forEach { assertFalse("$level: $meaning", it in meaning) }
        }
    }
}
