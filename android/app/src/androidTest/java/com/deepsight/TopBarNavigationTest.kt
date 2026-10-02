package com.deepsight

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The top bar's back button appears only when there is somewhere to go back to, and goes there. */
@RunWith(AndroidJUnit4::class)
class TopBarNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backButtonReturnsToThePreviousScreen() {
        rule.onNodeWithText("Choose test").assertExists()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()

        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Choose test").assertDoesNotExist() // on History now; it may list saved cases

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Choose test").assertExists()
        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }
}
