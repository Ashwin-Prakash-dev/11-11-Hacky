package com.deepsight

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.engine.pack.PackLoader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks choose test → case → back → history with the packs shipped in the APK, checking the disclaimer on each screen.
 * Result and sign-off are covered by ResultScreenTest; a real case run by CaseRunnerTest.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val disclaimer = "Screening aid. A clinician decides."

    @Test
    fun everyScreenReachable() {
        val packs = PackLoader.fromAssets(rule.activity.assets).discover().installed
        assertTrue("no pack passed PackLoader; check ml/packs", packs.isNotEmpty())
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(packs.first().displayName)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(disclaimer).assertExists()
        packs.forEach { rule.onNodeWithText(it.displayName).assertExists() }

        // Packs not yet validated on a phone are listed but can't be picked (DemoPacks); open the first one that can.
        rule.onNodeWithText(packs.first { DemoPacks.isReady(it.id) }.displayName).performClick()
        rule.onNodeWithText("Import image").assertExists()
        rule.onNodeWithText("Analyse").assertExists()
        rule.onNodeWithText(disclaimer).assertExists()

        pressBack()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Choose test").assertDoesNotExist() // history screen, whatever Room already holds
        rule.onNodeWithText(disclaimer).assertExists()
    }
}
