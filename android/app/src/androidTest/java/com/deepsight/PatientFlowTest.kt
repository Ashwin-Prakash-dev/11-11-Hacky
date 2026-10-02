package com.deepsight

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.engine.pack.PackLoader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openReadyPack() {
        val packs = PackLoader.fromAssets(rule.activity.assets).discover().installed
        val ready = packs.first { DemoPacks.isReady(it.id) }
        waitFor(ready.displayName)
        rule.onNodeWithText(ready.displayName).performScrollTo().performClick()
    }

    @Test
    fun profileDirectorySearchOpensReadOnlyDetails() {
        rule.onNodeWithText("Patient profiles").performScrollTo().performClick()
        rule.onNodeWithText("Search by name or UID").performTextInput("DS-1002")
        rule.onNodeWithText("Ravi Kumar").performClick()

        rule.onNodeWithText("Ravi Kumar").assertExists()
        rule.onNodeWithText("DS-1002").assertExists()
        rule.onNodeWithText("1975-11-03").assertExists()
        rule.onNodeWithText("B+").assertExists()
    }

    @Test
    fun existingPatientIsSelectedBeforeTheCaseScreen() {
        openReadyPack()
        rule.onNodeWithText("Existing patient").performClick()
        rule.onNodeWithText("Search by name or UID").performTextInput("Asha")
        rule.onNodeWithText("Asha Nair").performClick()

        rule.onNodeWithText("New case").assertExists()
        rule.onNodeWithText("Asha Nair").assertExists()
        rule.onNodeWithText("DS-1001").assertExists()
        rule.onNodeWithText("Import image").assertExists()
        rule.onNodeWithText("Capture").assertExists()
    }

    @Test
    fun newPatientFormCreatesASessionProfileAndStartsTheCase() {
        openReadyPack()
        rule.onNodeWithText("New patient").performClick()
        rule.onNodeWithText("Full name").performTextInput("Meera Shah")
        rule.onNodeWithText("Patient UID").performTextInput("DS-1003")
        rule.onNodeWithText("Date of birth (YYYY-MM-DD)").performTextInput("1992-08-21")
        rule.onNodeWithText("Blood group").performTextInput("A-")
        rule.onNodeWithText("Continue to images").performClick()

        rule.onNodeWithText("New case").assertExists()
        rule.onNodeWithText("Meera Shah").assertExists()
        rule.onNodeWithText("DS-1003").assertExists()
        rule.onNodeWithText("Import image").assertExists()
    }
}
