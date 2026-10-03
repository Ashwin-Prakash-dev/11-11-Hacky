package com.deepsight

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.capture.CaseStore
import com.deepsight.capture.FieldImage
import com.deepsight.data.CaseDb
import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import com.deepsight.engine.contract.Contracts
import com.deepsight.profiles.Patient
import com.deepsight.profiles.Sex
import com.deepsight.result.SignOff
import com.deepsight.result.SignOffDecision
import com.deepsight.result.sign
import java.io.File
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The FCFS queue with a fake runner (contract example results) on an in-memory database. */
@RunWith(AndroidJUnit4::class)
class CaseQueueTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, CaseDb::class.java).build()
    private val dao = db.dao()
    private val store = CaseStore(File(instrumentation.targetContext.cacheDir, "case-queue-test").apply { deleteRecursively() })
    private val scopes = mutableListOf<CoroutineScope>()

    private val exampleField = instrumentation.context.assets.open("field_result.malaria_thin.json").use { Contracts.parseFieldResult(it.reader().readText()) }
    private val exampleCase = instrumentation.context.assets.open("case_result.malaria_thin.json").use { Contracts.parseCaseResult(it.reader().readText()) }

    /** Order the fake runner started cases in. */
    private val started = Collections.synchronizedList(mutableListOf<String>())

    private suspend fun fakeRun(packId: String, caseId: String, images: List<FieldImage>, onProgress: (Int, Int) -> Unit): CaseRun {
        started += caseId
        delay(if (caseId.endsWith("slow")) 300 else 10)
        if (caseId.startsWith("bad")) error("model exploded")
        val fields = images.map { exampleField.copy(caseId = caseId, fieldId = fieldId(caseId, it.index)) }
        return CaseRun(fields, exampleCase.copy(caseId = caseId, packId = packId, fieldIds = fields.map { it.fieldId }), analysedAt = 42L)
    }

    private fun queue() = CaseQueue(dao, store, ::fakeRun, scope = CoroutineScope(SupervisorJob()).also { scopes += it })

    private fun addFields(caseId: String, n: Int = 1) = repeat(n) { store.import(caseId, "jpeg".byteInputStream(), "jpg") }

    private suspend fun awaitFinished(caseId: String): CaseEntity = withTimeout(10_000) {
        dao.observe(caseId).first { it?.status == CaseStatus.DONE || it?.status == CaseStatus.FAILED }!!
    }

    private suspend fun patient(uid: String) = Patient(uid, "Ada Example", "1990-05-17", Sex.F, createdAt = 1L).also { db.patientDao().insert(it) }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        db.close()
    }

    @Test
    fun batchesFinishInSubmitOrderWithTheirPatient() = runBlocking {
        val ada = patient("P-0000-0001")
        val q = queue()
        listOf("c1-slow", "c2", "c3").forEach { addFields(it); q.submit(ada.uid, "malaria_thin", it) }

        listOf("c1-slow", "c2", "c3").forEach { id ->
            val row = awaitFinished(id)
            assertEquals(CaseStatus.DONE, row.status)
            assertEquals(ada.uid, row.patientUid)
            assertEquals(42L, row.analysedAt)
            assertEquals(id, Contracts.parseCaseResult(row.caseResultJson!!).caseId)
            assertEquals(listOf(fieldId(id, 1)), dao.fields(id).map { it.fieldId })
        }
        assertEquals(listOf("c1-slow", "c2", "c3"), started.toList())
    }

    @Test
    fun aFailingBatchDoesNotStopTheNext() = runBlocking {
        val q = queue()
        listOf("bad1", "c2").forEach { addFields(it); q.submit(null, "malaria_thin", it) }

        val bad = awaitFinished("bad1")
        assertEquals(CaseStatus.FAILED, bad.status)
        assertEquals("model exploded", bad.error)
        assertEquals(CaseStatus.DONE, awaitFinished("c2").status)
    }

    @Test
    fun aNewQueueRunsWhatTheLastProcessLeftQueuedOrRunning() = runBlocking {
        addFields("left-queued")
        addFields("left-running")
        dao.insertIfAbsent(CaseEntity("left-running", "malaria_thin", createdAt = 1L, status = CaseStatus.RUNNING))
        dao.insertIfAbsent(CaseEntity("left-queued", "malaria_thin", createdAt = 2L, status = CaseStatus.QUEUED))

        queue()

        assertEquals(CaseStatus.DONE, awaitFinished("left-queued").status)
        assertEquals(CaseStatus.DONE, awaitFinished("left-running").status)
        assertEquals(listOf("left-running", "left-queued"), started.toList()) // created_at order
    }

    @Test
    fun aRecaptureRunsTheWholeBatchAgainAndDropsTheDeletedField() = runBlocking {
        val q = queue()
        addFields("c1", n = 2)
        q.submit(null, "malaria_thin", "c1")
        awaitFinished("c1")
        assertEquals(2, dao.fields("c1").size)

        store.fields("c1").first().file.delete()
        q.submit(null, "malaria_thin", "c1") // commits QUEUED before it returns, so the next DONE is the re-run's
        awaitFinished("c1")

        assertEquals(listOf(fieldId("c1", 2)), dao.fields("c1").map { it.fieldId })
        assertEquals(listOf("c1", "c1"), started.toList())
    }

    @Test
    fun signOffKeepsThePatientCreationTimeAndFields() = runBlocking {
        val ada = patient("P-0000-0001")
        val q = queue()
        addFields("c1", n = 2)
        q.submit(ada.uid, "malaria_thin", "c1")
        val done = awaitFinished("c1")

        dao.sign(SignOff("c1", "Dr Who", signedAt = 99L, SignOffDecision.ACCEPT, note = ""), reportText = "Report.", reportSource = "template")

        val signed = dao.caseById("c1")!!
        assertEquals(CaseStatus.SIGNED, signed.status)
        assertEquals(ada.uid, signed.patientUid)
        assertEquals(done.createdAt, signed.createdAt)
        assertEquals(42L, signed.analysedAt)
        assertEquals("Dr Who", signed.signedBy)
        assertEquals("Report.", signed.reportText)
        assertNull(signed.error)
        assertEquals(2, dao.fields("c1").size)
    }
}
