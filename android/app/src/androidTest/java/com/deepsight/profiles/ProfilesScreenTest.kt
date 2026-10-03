package com.deepsight.profiles

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepsight.ui.theme.DeepSightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilesScreenTest {
    @get:Rule val rule = createComposeRule()

    private val profiles = listOf(
        PatientProfile("P-1001", "Ada Example", 34, "F", 3, null),
        PatientProfile("P-1002", "Ben Sample", 61, "M", 1, null),
    )

    @Test
    fun listsProfilesAndFiltersWhileTyping() {
        rule.setContent { DeepSightTheme { ProfilesScreen(profiles) } }
        rule.onNodeWithText("Ada Example").assertExists()
        rule.onNodeWithText("Ben Sample").assertExists()

        rule.onNodeWithText("Search by name or ID").performTextInput("ben")
        rule.onNodeWithText("Ben Sample").assertExists()
        rule.onNodeWithText("Ada Example").assertDoesNotExist()
    }

    @Test
    fun noMatchShowsAnEmptyState() {
        rule.setContent { DeepSightTheme { ProfilesScreen(profiles) } }
        rule.onNodeWithText("Search by name or ID").performTextInput("zzz")
        rule.onNodeWithText("No profiles match", substring = true).assertExists()
    }

    @Test
    fun choosingAPatientSelectsThatProfile() {
        var chosen: PatientProfile? = null
        var added = false
        rule.setContent { DeepSightTheme { ProfilesScreen(profiles, onSelect = { chosen = it }, onNew = { added = true }) } }
        rule.onNodeWithText("Ben Sample").performClick()
        assertEquals(profiles[1], chosen)
        rule.onNodeWithText("New patient").performClick()
        assertTrue(added)
    }

    @Test
    fun noPatientsYetSaysHowToAddOne() {
        rule.setContent { DeepSightTheme { ProfilesScreen(emptyList()) } }
        rule.onNodeWithText("No patients yet").assertExists()
        rule.onNodeWithText("New patient").assertDoesNotExist() // only offered before a case
    }
}
