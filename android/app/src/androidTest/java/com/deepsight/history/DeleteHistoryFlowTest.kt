package com.deepsight.history

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.MainActivity
import com.deepsight.data.CaseDb
import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import com.deepsight.data.FieldEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app: two cases (with images) seeded in its own database, one deleted through History's long press, the
 * confirmation and the ViewModel. The row, its fields and its image folder go; the other case stays.
 */
@RunWith(AndroidJUnit4::class)
class DeleteHistoryFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val context get() = rule.activity
    private val dao get() = CaseDb.get(context).dao()
    private val doomed = "zz-delete-test-doomed"
    private val kept = "zz-delete-test-kept"
    private fun folder(id: String) = File(context.filesDir, "cases/$id")

    @Before
    fun seed() = runBlocking {
        val newest = System.currentTimeMillis() + 10_000_000 // newest first, so both rows are at the top of History
        listOf(doomed to newest, kept to newest - 1).forEach { (id, at) ->
            dao.upsert(
                CaseEntity(id, "malaria_thin", at, signedBy = "Dr Test", signedAt = at, decision = "accept", status = CaseStatus.SIGNED),
                listOf(FieldEntity("${id}_field_1", id, folder(id).path + "/field_1.jpg", "{}")),
            )
            folder(id).apply { mkdirs() }.resolve("field_1.jpg").writeText("image")
        }
    }

    @After
    fun cleanUp() = runBlocking {
        listOf(doomed, kept).forEach { dao.deleteCase(it); folder(it).deleteRecursively() }
    }

    @Test
    fun deletingFromHistoryRemovesTheCaseItsFieldsAndItsImages() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("History")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("History").performScrollTo().performClick()

        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("history-$doomed")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("history-$doomed").performTouchInput { longClick() }
        rule.onNodeWithText("1 selected").assertExists()
        rule.onNodeWithTag("delete-selected").performClick()
        rule.onNodeWithText("Delete 1 case?").assertExists()
        assertNotNull("nothing is deleted before the confirmation", runBlocking { dao.caseById(doomed) })

        rule.onNodeWithTag("confirm-delete").performClick()
        rule.waitUntil(10_000) { runBlocking { dao.caseById(doomed) } == null }

        assertNull(runBlocking { dao.caseById(doomed) })
        assertEquals(0, runBlocking { dao.fields(doomed).size })
        assertFalse("its images are deleted from the phone", folder(doomed).exists())
        assertNotNull("the other case is untouched", runBlocking { dao.caseById(kept) })
        assertEquals(1, runBlocking { dao.fields(kept).size })
        assertTrue(folder(kept).resolve("field_1.jpg").exists())
        rule.waitUntil(10_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("history-$doomed")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("history-$kept").assertExists()
    }
}
