package com.deepsight.ui.components

import com.deepsight.engine.contract.TriageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageStyleTest {
    @Test
    fun abnormalFlagIsAnAlertForReview() {
        val style = triageStyle(TriageLevel.ABNORMAL_FLAG)
        assertEquals(TriageTone.ALERT, style.tone)
        assertEquals("A screening rule flagged this case. A clinician should review it.", style.meaning)
    }

    @Test
    fun needsExpertIsACautionToRefer() {
        val style = triageStyle(TriageLevel.NEEDS_EXPERT)
        assertEquals(TriageTone.CAUTION, style.tone)
        assertEquals("The screen could not decide. Refer this case for expert review.", style.meaning)
    }

    @Test
    fun normalScreenStillNeedsSignOff() {
        val style = triageStyle(TriageLevel.NORMAL_SCREEN)
        assertEquals(TriageTone.CLEAR, style.tone)
        assertEquals("No screening rule flagged this case. A clinician still signs off.", style.meaning)
    }

    @Test
    fun noMeaningClaimsADiagnosis() {
        TriageLevel.entries.forEach { level ->
            val meaning = triageStyle(level).meaning.lowercase()
            listOf("diagnos", "positive", "negative", "malaria", "infected").forEach { word ->
                assertTrue("$level meaning says '$word'", word !in meaning)
            }
        }
    }
}
