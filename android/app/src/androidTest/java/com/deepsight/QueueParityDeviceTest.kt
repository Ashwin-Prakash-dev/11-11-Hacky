package com.deepsight

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.capture.CaseStore
import com.deepsight.data.CaseDb
import com.deepsight.data.CaseStatus
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.pack.PackLoader
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A batch through the queue stores exactly what CaseRunner.run returns for it, on the real malaria pack. Needs
 * `annot_golden_positive.jpg` (see AnnotatedFieldsDeviceTest) in the app's external files dir; otherwise skipped.
 */
@RunWith(AndroidJUnit4::class)
class QueueParityDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun queuedBatchStoresTheSameResultAsADirectRun() = runBlocking {
        val photo = File(context.getExternalFilesDir(null), "annot_golden_positive.jpg")
        assumeTrue("annotated field not pushed", photo.isFile)
        val store = CaseStore(File(context.cacheDir, "queue-parity-test").apply { deleteRecursively() })
        repeat(2) { photo.inputStream().use { store.import("q1", it, "jpg") } }
        val runner = CaseRunner(PackLoader.fromAssets(context.assets))
        val db = Room.inMemoryDatabaseBuilder(context, CaseDb::class.java).build()
        val scope = CoroutineScope(SupervisorJob())
        try {
            val direct = runner.run("malaria_thin", "q1", store.fields("q1"))

            CaseQueue(db.dao(), store, runner::run, scope = scope).submit(null, "malaria_thin", "q1")
            val row = withTimeout(120_000) { db.dao().observe("q1").first { it?.status == CaseStatus.DONE || it?.status == CaseStatus.FAILED }!! }

            assertEquals(row.error, CaseStatus.DONE, row.status)
            assertEquals(direct.case, Contracts.parseCaseResult(row.caseResultJson!!))
            // timing_ms is a measurement, different on every run; everything else must match.
            val stored = db.dao().fields("q1").map { Contracts.parseFieldResult(it.fieldResultJson).copy(timingMs = emptyMap()) }
            assertEquals(direct.fields.map { it.copy(timingMs = emptyMap()) }, stored)
        } finally {
            scope.cancel()
            db.close()
        }
    }
}
