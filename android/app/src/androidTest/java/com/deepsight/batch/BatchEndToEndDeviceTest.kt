package com.deepsight.batch

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.CaseQueue
import com.deepsight.CaseRunner
import com.deepsight.capture.CaseStore
import com.deepsight.data.CaseDb
import com.deepsight.data.CaseStatus
import com.deepsight.data.create
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.pack.PackLoader
import com.deepsight.profiles.Sex
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A verified, mixed batch through the real submitter, queue, database and both real packs: images for two modules go in
 * together, come out as one analysed case per module, and carry the patient. Needs these in the app's external files dir
 * (see AnnotatedFieldsDeviceTest and BreastCaseDeviceTest): annot_golden_positive.jpg, annot_demo_sparse.jpg,
 * breast_SOB_M_DC-14-12312-400-010.png, breast_SOB_B_TA-14-16184-400-014.png. Otherwise skipped. Logs: DeepSightBatch
 */
@RunWith(AndroidJUnit4::class)
class BatchEndToEndDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "batch-e2e.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun mixedBatchRunsOneCasePerModuleThroughTheQueue() = runBlocking {
        val dir = context.getExternalFilesDir(null)
        val files = listOf(
            "annot_golden_positive.jpg", "breast_SOB_M_DC-14-12312-400-010.png",
            "annot_demo_sparse.jpg", "breast_SOB_B_TA-14-16184-400-014.png",
        ).map { File(dir, it) }
        assumeTrue("test images not pushed", files.all(File::isFile))

        context.deleteDatabase(dbName)
        val db = CaseDb.build(context, dbName)
        val patient = db.patientDao().create("Ada Example", "1990-05-01", Sex.F, System.currentTimeMillis())
        val store = CaseStore(File(context.cacheDir, "batch-e2e").apply { deleteRecursively() })
        val runner = CaseRunner(PackLoader.fromAssets(context.assets))
        val queue = CaseQueue(db.dao(), store, runner::run)
        val packIds = listOf("malaria_thin", "breast_breakhis")
        assertEquals("both modules are offered", packIds, runner.packs().map { it.id }.filter { it in packIds })

        // A stand-in that knows the truth by file name, so the test checks the mechanism, not the placeholder's luck.
        val router = FieldRouter { f, _ -> if (f.name.startsWith("annot")) "malaria_thin" else "breast_breakhis" }
        val draft = BatchDraft().add(files, router, packIds).withPatient(patient.uid)
        assertEquals(false, draft.canSubmit) // not verified yet
        val verified = draft.setVerified(true)

        val submitted = BatchSubmitter(store, { uid, pack, caseId -> queue.submit(uid, pack, caseId) }).submit(verified, packIds)
        assertEquals(listOf("malaria_thin" to 2, "breast_breakhis" to 2), submitted.map { it.packId to it.imageCount })

        for (batch in submitted) {
            val row = withTimeout(180_000) { db.dao().observe(batch.caseId).first { it?.status == CaseStatus.DONE || it?.status == CaseStatus.FAILED } }!!
            val case = Contracts.parseCaseResult(row.caseResultJson ?: error("no result: ${row.error}"))
            Log.i(TAG, "${batch.packId}: ${row.status} triage ${case.triage.level} ${case.triage.ruleId} counts ${case.counts} fields ${case.fieldIds}")
            assertEquals(CaseStatus.DONE, row.status)
            assertEquals(patient.uid, row.patientUid)
            assertEquals(batch.packId, row.packId)
            assertEquals(batch.imageCount, case.fieldIds.size)
            assertEquals(batch.imageCount, case.fieldsPassed)
            when (batch.packId) {
                "malaria_thin" -> assertEquals(TriageLevel.ABNORMAL_FLAG to "parasite_seen", case.triage.level to case.triage.ruleId)
                // one malignant and one benign prediction: malignant_seen fires
                else -> assertEquals(mapOf("benign" to 1, "malignant" to 1), case.counts)
            }
        }
    }

    private companion object {
        const val TAG = "DeepSightBatch"
    }
}
