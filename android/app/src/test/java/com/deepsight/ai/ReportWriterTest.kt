package com.deepsight.ai

import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.contract.TriageResult
import com.deepsight.engine.contract.UncertaintyResult
import com.deepsight.report.gemma.GemmaStopped
import com.deepsight.report.templateReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportWriterTest {
    private val field = FieldResult(
        caseId = "c1", fieldId = "c1_field_1", packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = true, blurScore = 14.0, exposureScore = 0.1), counts = mapOf("parasitized" to 6, "uninfected" to 209),
    )
    private val case = CaseResult(
        caseId = "c1", packId = "malaria_thin", packVersion = "0.1.0", fieldIds = listOf("c1_field_1"), fieldsPassed = 1,
        counts = mapOf("parasitized" to 6, "uninfected" to 209), uncertainty = UncertaintyResult(flag = false),
        triage = TriageResult(TriageLevel.ABNORMAL_FLAG, "parasite_seen", provisional = true),
    )
    private val template = templateReport(case, listOf(field), "Malaria (thin smear)")

    private class FakeNarrator(initial: AiStatus, val reply: suspend ((String) -> Unit) -> String) : Narrator {
        override val status = MutableStateFlow(initial)
        var prompts = 0
        override suspend fun narrate(prompt: String, onText: (String) -> Unit): String { prompts++; return reply(onText) }
    }

    private fun write(narrator: Narrator, waitMs: Long = 1_000, streamed: MutableList<String> = mutableListOf()) = runBlocking {
        ReportWriter(narrator, waitForModelMs = waitMs).write(case, listOf(field), "Malaria (thin smear)") { streamed += it }
    }

    @Test
    fun goodGemmaTextIsCleanedAndKept() {
        val streamed = mutableListOf<String>()
        val narrator = FakeNarrator(AiStatus.Ready) { onText -> onText("**Note:**\n"); onText("6 of 215 cells flagged; triage ABNORMAL_FLAG."); "**Note:**\n6 of 215 cells flagged; triage ABNORMAL_FLAG." }
        assertEquals(CaseReportText("6 of 215 cells flagged; triage ABNORMAL_FLAG.", ReportSource.GEMMA), write(narrator, streamed = streamed))
        assertEquals(2, streamed.size)
    }

    @Test
    fun textFailingTheTriageCheckFallsBackToTheTemplate() {
        val narrator = FakeNarrator(AiStatus.Ready) { "This looks NORMAL_SCREEN." }
        assertEquals(
            CaseReportText(template, ReportSource.TEMPLATE, "The AI summary does not state the triage level ABNORMAL_FLAG, so the template report is shown."),
            write(narrator),
        )
    }

    @Test
    fun missingModelGivesTheTemplateWithoutAsking() {
        val narrator = FakeNarrator(AiStatus.Missing("/x/gemma.litertlm")) { error("must not be called") }
        assertEquals(CaseReportText(template, ReportSource.TEMPLATE, "Gemma is not installed on this phone, so the template report is shown."), write(narrator))
        assertEquals(0, narrator.prompts)
    }

    @Test
    fun failedStartGivesTheTemplate() {
        val narrator = FakeNarrator(AiStatus.Failed("GPU init failed")) { error("must not be called") }
        assertEquals("Gemma could not start (GPU init failed), so the template report is shown.", write(narrator).note)
    }

    @Test
    fun timeoutAndErrorsGiveTheTemplate() {
        assertEquals("The AI summary took too long, so the template report is shown.",
            write(FakeNarrator(AiStatus.Ready) { throw GemmaStopped(timedOut = true) }).note)
        assertEquals("The AI summary failed (boom), so the template report is shown.",
            write(FakeNarrator(AiStatus.Ready) { error("boom") }).note)
    }

    @Test
    fun stillLoadingAfterTheWaitGivesTheTemplate() {
        val narrator = FakeNarrator(AiStatus.Loading) { error("must not be called") }
        assertEquals(CaseReportText(template, ReportSource.TEMPLATE, "Gemma is still loading, so the template report is shown."), write(narrator, waitMs = 50))
    }

    @Test
    fun waitsForAModelThatFinishesLoading() = runBlocking {
        val narrator = FakeNarrator(AiStatus.Loading) { "Triage ABNORMAL_FLAG: review this case." }
        launch { delay(30); narrator.status.value = AiStatus.Ready }
        val report = ReportWriter(narrator, waitForModelMs = 2_000).write(case, listOf(field), "Malaria (thin smear)") {}
        assertEquals(ReportSource.GEMMA, report.source)
    }
}
