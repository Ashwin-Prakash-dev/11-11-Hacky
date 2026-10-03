package com.deepsight.result

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.ui.theme.DeepSightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The result screen says when the analysis ran, or that the time was not recorded (cases saved before this existed). */
@RunWith(AndroidJUnit4::class)
class AnalysedAtTest {
    @get:Rule val rule = createComposeRule()

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
    private val case = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
    private val fields = listOf(Contracts.parseFieldResult(read("field_result.malaria_thin.json")))

    private fun show(analysedAt: Long?) = rule.setContent {
        DeepSightTheme { ResultScreen(case, fields, report = null, signOff = null, onRecapture = {}, onSignOff = {}, analysedAt = analysedAt) }
    }

    @Test
    fun showsTheAnalysisTime() {
        show(1_790_998_807_000L)
        rule.onNodeWithText(analysedAtLine(1_790_998_807_000L)).assertExists()
    }

    @Test
    fun saysWhenTheTimeWasNotRecorded() {
        show(null)
        rule.onNodeWithText("Analysis time not recorded").assertExists()
    }
}
