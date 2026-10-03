package com.deepsight.history

import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import com.deepsight.engine.contract.TriageLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryItemsTest {
    /** Gradle runs app unit tests from android/app. */
    private val caseResult = File("../../contracts/examples/case_result.malaria_thin.json").readText()

    private fun case(json: String?, status: CaseStatus = CaseStatus.DONE, error: String? = null) =
        CaseEntity("c1", "malaria_thin", createdAt = 7L, caseResultJson = json, status = status, error = error)

    @Test
    fun theTriageLevelComesFromTheStoredCaseResult() {
        val item = historyItemsOf(listOf(case(caseResult)), emptyMap()).single()
        assertEquals(TriageLevel.ABNORMAL_FLAG, item.level)
        assertEquals("malaria_thin", item.packName) // no manifest loaded: the pack id
        assertEquals(7L, item.createdAt)
    }

    @Test
    fun noLevelBeforeTriageHasRunOrWhenTheJsonIsUnreadable() {
        val items = historyItemsOf(listOf(case(null, CaseStatus.QUEUED), case("{not json")), emptyMap())
        assertNull(items[0].level)
        assertNull(items[1].level)
    }

    @Test
    fun aFailedCaseCarriesItsError() {
        val item = historyItemsOf(listOf(case(null, CaseStatus.FAILED, error = "out of memory")), emptyMap()).single()
        assertEquals(CaseStatus.FAILED, item.status)
        assertEquals("out of memory", item.error)
    }
}
