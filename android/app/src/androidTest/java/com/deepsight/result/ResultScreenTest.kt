package com.deepsight.result

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The result screen on the frozen contract examples: badge, reject + Recapture, sign-off. Moved here from NavigationTest. */
@RunWith(AndroidJUnit4::class)
class ResultScreenTest {
    @get:Rule val rule = createComposeRule()

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }

    @Test
    fun showsTriageBadgeRejectAndSignsOff() {
        val case = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
        val fields = listOf("field_result.malaria_thin.json", "field_result.rejected.json").map { Contracts.parseFieldResult(read(it)) }
        var recaptured: String? = null
        var signed: SignOff? = null
        rule.setContent { ResultScreen(case, fields, report = null, signOff = null, onRecapture = { recaptured = it }, onSignOff = { signed = it }) }

        rule.onNodeWithText("ABNORMAL_FLAG").assertExists()
        rule.onNodeWithText("PROVISIONAL", substring = true).assertExists()
        rule.onNodeWithText("Rejected: blur").assertExists()
        rule.onNodeWithText("Recapture").performClick()
        assertEquals(fields[1].fieldId, recaptured)

        rule.onNodeWithText("Clinician name").performScrollTo().performTextInput("Dr Test")
        rule.onNodeWithText("Sign off").performScrollTo().performClick()
        assertEquals("Dr Test", signed?.signedBy)
        assertEquals(case.caseId, signed?.caseId)
    }

    @Test
    fun aResultOpenedFromHistoryOffersNoRecapture() {
        val case = Contracts.parseCaseResult(read("case_result.malaria_thin.json"))
        val fields = listOf(Contracts.parseFieldResult(read("field_result.rejected.json")))
        rule.setContent { ResultScreen(case, fields, report = null, signOff = null, onRecapture = {}, onSignOff = {}, canRecapture = false) }
        rule.onNodeWithText("Rejected: blur").assertExists()
        rule.onNodeWithText("Recapture").assertDoesNotExist()
    }

    @Test
    fun classificationOnlyShowsClassesButNotTriageOrReport() {
        val case = Contracts.parseCaseResult(read("case_result.malaria_thin.json")).copy(
            counts = linkedMapOf("early_pre_b_like" to 0, "pre_b_like" to 0, "pro_b_like" to 0, "benign" to 0),
        )
        val fields = listOf(Contracts.parseFieldResult(read("field_result.malaria_thin.json")))

        rule.setContent {
            ResultScreen(
                case,
                fields,
                report = null,
                signOff = null,
                onRecapture = {},
                onSignOff = {},
                classificationOnly = true,
                patientLabel = "Ada Example · P-0000-0001",
            )
        }

        rule.onNodeWithText("Classification only", substring = true).assertExists()
        rule.onNodeWithText("Ada Example · P-0000-0001").assertExists()
        rule.onNodeWithText("Early pre B like").assertExists()
        rule.onAllNodesWithText("early_pre_b_like").assertCountEquals(0)
        rule.onNodeWithText("No classifications were returned", substring = true).assertExists()
        rule.onAllNodesWithText("ABNORMAL_FLAG").assertCountEquals(0)
        rule.onAllNodesWithText("Report").assertCountEquals(0)
    }
}
