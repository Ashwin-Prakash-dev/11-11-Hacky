package com.deepsight.batch

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.data.CaseStatus
import com.deepsight.ui.theme.DeepSightTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchScreenTest {
    @get:Rule val rule = createComposeRule()

    private val running = BatchItem("c1", "Ada Example · P-0000-0001", "Malaria (thin smear)", CaseStatus.RUNNING, 2 to 4, null, null)
    private val queued = BatchItem("c2", "Ben Sample · P-0000-0002", "Malaria (thin smear)", CaseStatus.QUEUED, null, 1, null)
    private val ready = BatchItem("c3", "Cara Test · P-0000-0003", "Malaria (thin smear)", CaseStatus.DONE, null, null, null)

    @Test
    fun listsEveryUnsignedBatchAndOpensOnlyAFinishedOne() {
        val opened = mutableListOf<String>()
        rule.setContent { DeepSightTheme { BatchScreen(listOf(running, queued, ready), onOpen = { opened += it }, onUseSingle = {}) } }

        rule.onNodeWithText("Analysing field 2 of 4…").performScrollTo().assertExists()
        rule.onNodeWithText("Queued · next").performScrollTo().assertExists()
        rule.onNodeWithText("Ben Sample · P-0000-0002").performScrollTo().performClick() // still queued: nothing to open
        rule.onNodeWithText("Cara Test · P-0000-0003").performScrollTo().performClick()
        assertEquals(listOf("c3"), opened)
    }

    @Test
    fun noBatchesShowsTheEmptyState() {
        rule.setContent { DeepSightTheme { BatchScreen(emptyList(), onOpen = {}, onUseSingle = {}) } }
        rule.onNodeWithText("No batches yet").performScrollTo().assertExists()
    }
}
