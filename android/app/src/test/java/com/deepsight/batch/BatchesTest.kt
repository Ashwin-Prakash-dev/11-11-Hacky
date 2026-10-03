package com.deepsight.batch

import com.deepsight.QueueState
import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import com.deepsight.data.SubmissionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchesTest {
    private fun case(
        id: String,
        status: CaseStatus,
        patient: String? = "P-0000-0001",
        error: String? = null,
        source: SubmissionSource = SubmissionSource.BATCH,
    ) = CaseEntity(id, "malaria_thin", createdAt = 0L, patientUid = patient, status = status, error = error, submissionSource = source)

    private val patients = mapOf("P-0000-0001" to "Ada Example")
    private val packs = mapOf("malaria_thin" to "Malaria (thin smear)")

    @Test
    fun keepsSubmitOrderAndShowsProgressOnlyForTheRunningBatch() {
        val rows = listOf(case("c1", CaseStatus.DONE), case("c2", CaseStatus.RUNNING), case("c3", CaseStatus.QUEUED), case("c4", CaseStatus.QUEUED))
        val queue = QueueState(running = "c2", progress = 2 to 4, queued = listOf("c3", "c4"))

        val items = batchesOf(rows, patients, packs, queue)

        assertEquals(listOf("c1", "c2", "c3", "c4"), items.map { it.caseId })
        assertEquals(listOf(null, 2 to 4, null, null), items.map { it.progress })
        assertEquals(listOf(null, null, 1, 2), items.map { it.position })
        assertEquals("Ada Example · P-0000-0001", items[0].patient)
        assertEquals("Malaria (thin smear)", items[0].packName)
    }

    @Test
    fun aCaseWithoutAPatientOrAKnownPackStillShows() {
        val item = batchesOf(listOf(case("c1", CaseStatus.FAILED, patient = null, error = "boom")), patients, emptyMap(), QueueState()).single()
        assertEquals(null, item.patient)
        assertEquals("malaria_thin", item.packName)
        assertEquals("boom", item.error)
    }

    @Test
    fun singleSubmissionsNeverAppearInTheBatchTab() {
        val rows = listOf(
            case("single", CaseStatus.DONE, source = SubmissionSource.SINGLE),
            case("batch", CaseStatus.DONE),
        )

        assertEquals(listOf("batch"), batchesOf(rows, patients, packs, QueueState()).map { it.caseId })
    }

    @Test
    fun statusLinesSayWhatEachBatchIsWaitingFor() {
        val base = BatchItem("c", null, "Malaria", CaseStatus.QUEUED, progress = null, position = 2, error = null)
        assertEquals("Queued · #2 in line", base.statusLine())
        assertEquals("Queued · next", base.copy(position = 1).statusLine())
        assertEquals("Loading the model…", base.copy(status = CaseStatus.RUNNING, position = null, progress = 0 to 3).statusLine())
        assertEquals("Analysing field 2 of 3…", base.copy(status = CaseStatus.RUNNING, position = null, progress = 2 to 3).statusLine())
        assertEquals("Ready for sign-off", base.copy(status = CaseStatus.DONE, position = null).statusLine())
        assertEquals("Analysis failed: boom", base.copy(status = CaseStatus.FAILED, position = null, error = "boom").statusLine())
    }
}
