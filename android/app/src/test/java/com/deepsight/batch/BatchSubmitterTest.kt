package com.deepsight.batch

import com.deepsight.capture.CaseStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BatchSubmitterTest {
    private lateinit var dir: File
    private lateinit var store: CaseStore
    private val queued = mutableListOf<Triple<String, String, String>>()
    private val packs = listOf("malaria_thin", "breast_breakhis")

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("batch-submit").toFile()
        store = CaseStore(File(dir, "cases"))
        queued.clear()
    }

    private fun image(name: String, content: String) = File(dir, name).apply { writeText(content) }

    private fun draft(vararg files: File, patient: String? = "P-1"): BatchDraft {
        val byName = FieldRouter { f, _ -> if (f.name.startsWith("m")) "malaria_thin" else "breast_breakhis" }
        return BatchDraft().add(files.toList(), byName, packs).let { d -> patient?.let(d::withPatient) ?: d }.setVerified(true)
    }

    private fun submitter() = BatchSubmitter(store, { uid, pack, caseId -> queued += Triple(uid, pack, caseId) }, clock = { 1_000L })

    @Test
    fun oneCasePerModuleWithItsImagesCopiedUnchanged() = runBlocking {
        val m1 = image("m1.jpg", "malaria-one")
        val b1 = image("b1.png", "breast-one")
        val m2 = image("m2.jpg", "malaria-two")

        val submitted = submitter().submit(draft(m1, b1, m2), packs)

        assertEquals(listOf("malaria_thin", "breast_breakhis"), submitted.map { it.packId })
        assertEquals(listOf(2, 1), submitted.map { it.imageCount })
        assertEquals(2, submitted.map { it.caseId }.toSet().size)
        assertEquals(submitted.map { Triple("P-1", it.packId, it.caseId) }, queued)
        val malaria = store.fields(submitted[0].caseId)
        assertEquals(listOf("malaria-one", "malaria-two"), malaria.map { it.file.readText() })
        assertEquals(listOf("jpg", "jpg"), malaria.map { it.file.extension })
        assertEquals("breast-one", store.fields(submitted[1].caseId).single().file.readText())
        assertEquals("png", store.fields(submitted[1].caseId).single().file.extension)
    }

    @Test
    fun refusesAnUnverifiedDraftAndTouchesNothing() {
        val d = draft(image("m1.jpg", "x")).setVerified(false)
        val error = runCatching { runBlocking { submitter().submit(d, packs) } }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(queued.isEmpty())
        assertFalse(File(dir, "cases").exists())
    }

    @Test
    fun aFileThatCannotBeReadQueuesNothing() {
        val ok = image("m1.jpg", "x")
        val gone = File(dir, "b1.png") // never created
        val error = runCatching { runBlocking { submitter().submit(draft(ok, gone), packs) } }.exceptionOrNull()
        assertTrue(error != null)
        assertTrue("no half-submitted batch", queued.isEmpty())
    }
}
