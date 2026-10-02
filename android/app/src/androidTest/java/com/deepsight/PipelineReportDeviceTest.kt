package com.deepsight

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.ai.AiStatus
import com.deepsight.ai.ReportService
import com.deepsight.ai.ReportSource
import com.deepsight.ai.ReportWriter
import com.deepsight.capture.CaseStore
import com.deepsight.engine.pack.PackLoader
import com.deepsight.report.checkNarrative
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole pipeline in the app process: a field photo → the real malaria pack (CaseRunner) → Gemma on LiteRT-LM writes
 * the report, which must state the exact triage level. Needs the Gemma model and `annot_golden_positive.jpg` (see
 * AnnotatedFieldsDeviceTest) in the app's external files dir; otherwise skipped. Results: adb logcat -s DeepSightPipeline
 */
@RunWith(AndroidJUnit4::class)
class PipelineReportDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun photoToModelToGemmaReport() = runBlocking {
        val photo = File(context.getExternalFilesDir(null), "annot_golden_positive.jpg")
        assumeTrue("Gemma model not on the phone", File(context.getExternalFilesDir(null), ReportService.MODEL_FILE).isFile)
        assumeTrue("annotated field not pushed", photo.isFile)
        val store = CaseStore(File(context.cacheDir, "pipeline-report-test").apply { deleteRecursively() })
        photo.inputStream().use { store.import("p1", it, "jpg") }

        val started = System.nanoTime()
        val run = CaseRunner(PackLoader.fromAssets(context.assets)).run("malaria_thin", "p1", store.fields("p1"))
        val analysed = System.nanoTime()

        val narrator = ReportService.narrator(context)
        val status = withTimeout(180_000) { narrator.status.first { it != AiStatus.Loading } }
        assertEquals("Gemma did not load", AiStatus.Ready, status)
        val loaded = System.nanoTime()

        var pieces = 0
        val report = ReportWriter(narrator).write(run.case, run.fields, "Malaria (thin smear)") { pieces++ }
        val written = System.nanoTime()

        Log.i(TAG, "triage ${run.case.triage.level} ${run.case.triage.ruleId}, counts ${run.case.counts}; " +
            "analysis ${(analysed - started) / 1_000_000} ms, Gemma ready after ${(loaded - analysed) / 1_000_000} ms more, " +
            "report ${(written - loaded) / 1_000_000} ms in $pieces pieces, source ${report.source}${report.note?.let { " ($it)" } ?: ""}")
        Log.i(TAG, "report: ${report.text}")
        assertTrue(report.text, checkNarrative(report.text, run.case.triage.level).ok)
        assertEquals("fell back: ${report.note}", ReportSource.GEMMA, report.source)
        assertTrue("did not stream ($pieces pieces)", pieces > 1)
    }

    private companion object {
        const val TAG = "DeepSightPipeline"
    }
}
