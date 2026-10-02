package com.deepsight.result

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.ReportUiState
import com.deepsight.ai.CaseReportText
import com.deepsight.ai.ReportSource
import com.deepsight.engine.contract.Contracts
import com.deepsight.ui.theme.DeepSightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The report card in its three states, on the frozen contract examples. */
@RunWith(AndroidJUnit4::class)
class ReportCardTest {
    @get:Rule val rule = createComposeRule()

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
    private val case = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
    private val fields = listOf(Contracts.parseFieldResult(read("field_result.malaria_thin.json")))

    private fun show(report: ReportUiState) = rule.setContent {
        DeepSightTheme { ResultScreen(case, fields, report, signOff = null, onRecapture = {}, onSignOff = {}) }
    }

    @Test
    fun writingStreamsCleanTextAndHoldsSignOff() {
        show(ReportUiState.Writing("**Note:**\nOne field passed; triage ABNORMAL"))
        rule.onNodeWithText("Writing…").assertExists()
        rule.onNodeWithText("One field passed; triage ABNORMAL").assertExists() // markdown and heading removed
        rule.onNodeWithText("Clinician name").performScrollTo().performTextInput("Dr Test")
        rule.onNodeWithText("Sign off").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun gemmaReportIsLabelledAsAiWording() {
        show(ReportUiState.Done(CaseReportText("One field passed; triage ABNORMAL_FLAG, review advised.", ReportSource.GEMMA)))
        rule.onNodeWithText("AI summary").assertExists()
        rule.onNodeWithText("One field passed; triage ABNORMAL_FLAG, review advised.").assertExists()
        rule.onNodeWithText("Gemma only words it", substring = true).assertExists()
    }

    @Test
    fun templateReportSaysWhy() {
        show(ReportUiState.Done(CaseReportText("Template text.", ReportSource.TEMPLATE, "Gemma is not installed on this phone, so the template report is shown.")))
        rule.onNodeWithText("Template").assertExists()
        rule.onNodeWithText("Gemma is not installed on this phone, so the template report is shown.").assertExists()
    }
}
