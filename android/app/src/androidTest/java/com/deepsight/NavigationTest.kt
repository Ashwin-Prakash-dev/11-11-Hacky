package com.deepsight

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks every screen with the fake engine and checks the disclaimer is always visible. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun step(click: String, expect: String) {
        rule.onNodeWithText(click).performClick()
        rule.onNodeWithText(expect).assertExists()
        rule.onNodeWithText("Screening aid. A clinician decides.").assertExists()
    }

    @Test
    fun everyScreenReachable() {
        rule.onNodeWithText("Screening aid. A clinician decides.").assertExists()
        step("Malaria (thin smear)", "Rejected: blur")
        step("Show result", "Triage: ABNORMAL_FLAG")
        step("Review and sign off", "Review")
        step("Sign off", "case-0001: ABNORMAL_FLAG")
    }
}
