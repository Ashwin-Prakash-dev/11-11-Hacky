package com.deepsight.profiles

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home → Profiles → search → back, in the real activity. */
@RunWith(AndroidJUnit4::class)
class ProfilesNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun profilesAreReachableFromHomeAndSearchable() {
        val first = SampleProfiles.all.first()
        val other = SampleProfiles.all.last()
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Profiles")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Profiles").performScrollTo().performClick()

        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText("Screening aid. A clinician decides.").assertExists()
        rule.onNodeWithText("Search by name or ID").performTextInput(first.id)
        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText(other.name).assertDoesNotExist()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Choose test").assertExists()
    }
}
