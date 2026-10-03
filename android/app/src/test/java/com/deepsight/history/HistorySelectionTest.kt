package com.deepsight.history

import com.deepsight.HistoryItem
import com.deepsight.data.CaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySelectionTest {
    private fun item(id: String, status: CaseStatus) = HistoryItem(id, "Malaria (thin smear)", null, null, null, null, status)

    @Test
    fun onlyFinishedCasesCanBeDeleted() {
        assertTrue(canDelete(CaseStatus.SIGNED))
        assertTrue(canDelete(CaseStatus.DONE))
        assertTrue(canDelete(CaseStatus.FAILED))
        // The queue is still working on these and writes its result into the stored case when it finishes.
        assertFalse(canDelete(CaseStatus.QUEUED))
        assertFalse(canDelete(CaseStatus.RUNNING))
    }

    @Test
    fun togglingAddsAndRemoves() {
        assertEquals(setOf("a"), toggled(emptySet(), "a"))
        assertEquals(setOf("a", "b"), toggled(setOf("a"), "b"))
        assertEquals(setOf("b"), toggled(setOf("a", "b"), "a"))
    }

    @Test
    fun selectionDropsCasesThatAreGoneOrBusy() {
        val items = listOf(item("a", CaseStatus.SIGNED), item("b", CaseStatus.RUNNING), item("c", CaseStatus.DONE))
        assertEquals(setOf("a", "c"), prunedSelection(setOf("a", "b", "c", "gone"), items))
        assertEquals(emptySet<String>(), prunedSelection(emptySet(), items))
    }

    @Test
    fun confirmationNamesTheCountAndWarnsAboutSignedCases() {
        assertEquals(
            "Delete 1 case?",
            deleteTitle(1),
        )
        assertEquals("Delete 3 cases?", deleteTitle(3))
        val one = deleteMessage(listOf(item("a", CaseStatus.FAILED)))
        assertTrue(one, "result, report and images" in one && "cannot be undone" in one)
        assertFalse(one, "signed-off" in one)
        val mixed = deleteMessage(listOf(item("a", CaseStatus.SIGNED), item("b", CaseStatus.SIGNED), item("c", CaseStatus.DONE)))
        assertTrue(mixed, "2 are signed off" in mixed)
        assertTrue(deleteMessage(listOf(item("a", CaseStatus.SIGNED))).contains("1 is signed off"))
    }
}
