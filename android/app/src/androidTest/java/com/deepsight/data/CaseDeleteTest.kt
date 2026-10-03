package com.deepsight.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Deleting history entries in Room: finished cases go with their fields; ones the queue still owns stay. */
@RunWith(AndroidJUnit4::class)
class CaseDeleteTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "delete-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun case(id: String, status: CaseStatus) = CaseEntity(id, "malaria_thin", 1L, status = status)
    private fun field(id: String, caseId: String) = FieldEntity("${caseId}_$id", caseId, null, "{}")

    @Test
    fun deletesFinishedCasesWithTheirFieldsAndKeepsBusyOnes() = runBlocking {
        context.deleteDatabase(name)
        val db = CaseDb.build(context, name)
        val dao = db.dao()
        try {
            CaseStatus.entries.forEach { s -> dao.upsert(case(s.name.lowercase(), s), listOf(field("f1", s.name.lowercase()))) }

            val deleted = dao.deleteFinished(listOf("signed", "done", "failed", "queued", "running", "unknown"))

            assertEquals(setOf("signed", "done", "failed"), deleted.toSet())
            listOf("signed", "done", "failed").forEach {
                assertNull(dao.caseById(it))
                assertEquals("fields of $it go with it", 0, dao.fields(it).size)
            }
            listOf("queued", "running").forEach {
                assertNotNull("$it must stay while the queue owns it", dao.caseById(it))
                assertEquals(1, dao.fields(it).size)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingNothingIsHarmless() = runBlocking {
        context.deleteDatabase(name)
        val db = CaseDb.build(context, name)
        try {
            db.dao().upsert(case("a", CaseStatus.SIGNED), emptyList())
            assertEquals(emptyList<String>(), db.dao().deleteFinished(emptyList()))
            assertNotNull(db.dao().caseById("a"))
        } finally {
            db.close()
        }
    }
}
