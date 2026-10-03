package com.deepsight.history

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.HistoryItem
import com.deepsight.data.CaseStatus
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.ui.theme.DeepSightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Selecting history entries (press and hold, then tap), confirming, and the cases that can't be deleted yet. */
@RunWith(AndroidJUnit4::class)
class HistoryDeleteScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun item(id: String, pack: String, status: CaseStatus, signedBy: String? = null) =
        HistoryItem(id, pack, TriageLevel.ABNORMAL_FLAG, signedBy?.let { 1_000L }, signedBy, signedBy?.let { "accept" }, status, createdAt = 1_000L)

    private val signed = item("c1", "Malaria (thin smear)", CaseStatus.SIGNED, "Dr Test")
    private val done = item("c2", "Breast histology (H&E)", CaseStatus.DONE)
    private val running = item("c3", "Leukaemia (WBC)", CaseStatus.RUNNING)

    private var opened: String? = null
    private var deleted: Set<String>? = null

    private fun show() {
        opened = null
        deleted = null
        rule.setContent {
            DeepSightTheme { HistoryScreen(listOf(signed, done, running), onOpen = { opened = it }, onDelete = { deleted = it }) }
        }
    }

    private fun hold(pack: String) = rule.onNodeWithText(pack).performTouchInput { longClick() }

    @Test
    fun tappingStillOpensACaseWhenNothingIsSelected() {
        show()
        rule.onNodeWithText("Malaria (thin smear)").performClick()
        assertEquals("c1", opened)
        rule.onNodeWithText("1 selected").assertDoesNotExist()
    }

    @Test
    fun pressAndHoldSelectsThenTapsToggleAndOpeningIsOff() {
        show()
        hold("Malaria (thin smear)")
        rule.onNodeWithText("1 selected").assertExists()

        rule.onNodeWithText("Breast histology (H&E)").performClick()
        rule.onNodeWithText("2 selected").assertExists()
        assertNull("tapping while selecting must not open the case", opened)

        rule.onNodeWithText("Breast histology (H&E)").performClick()
        rule.onNodeWithText("1 selected").assertExists()
    }

    @Test
    fun cancelLeavesSelectionMode() {
        show()
        hold("Malaria (thin smear)")
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("1 selected").assertDoesNotExist()
        rule.onNodeWithText("Malaria (thin smear)").performClick()
        assertEquals("c1", opened)
    }

    @Test
    fun deleteAsksFirstAndThenReportsTheSelectedCases() {
        show()
        hold("Malaria (thin smear)")
        rule.onNodeWithText("Breast histology (H&E)").performClick()

        rule.onNodeWithTag("delete-selected").performClick()
        rule.onNodeWithText("Delete 2 cases?").assertExists()
        rule.onNodeWithText("1 is signed off", substring = true).assertExists()
        assertNull("nothing is deleted before confirming", deleted)

        rule.onNodeWithTag("confirm-delete").performClick()
        assertEquals(setOf("c1", "c2"), deleted)
        rule.onNodeWithText("2 selected").assertDoesNotExist()
    }

    @Test
    fun keepingLeavesTheCasesAlone() {
        show()
        hold("Breast histology (H&E)")
        rule.onNodeWithTag("delete-selected").performClick()
        rule.onNodeWithText("Delete 1 case?").assertExists()
        rule.onNodeWithText("Keep").performClick()
        assertNull(deleted)
        rule.onNodeWithText("Delete 1 case?").assertDoesNotExist()
        rule.onNodeWithText("1 selected").assertExists() // still selected, in case they change their mind
    }

    @Test
    fun aCaseThatIsStillAnalysingCannotBeSelected() {
        show()
        hold("Leukaemia (WBC)")
        rule.onNodeWithText("1 selected").assertDoesNotExist()
        hold("Malaria (thin smear)")
        rule.onNodeWithText("Leukaemia (WBC)").performClick()
        rule.onNodeWithText("1 selected").assertExists() // the running one did not join
    }

    @Test
    fun hintTellsHowToSelect() {
        show()
        rule.onNodeWithText("Press and hold a case to select it", substring = true).assertExists()
    }
}
