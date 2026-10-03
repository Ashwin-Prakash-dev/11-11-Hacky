package com.deepsight.profiles

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.MainActivity
import com.deepsight.data.CaseDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home → Patients → search → back, in the real activity, on patients stored in the phone's own database. */
@RunWith(AndroidJUnit4::class)
class ProfilesNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val patients = CaseDb.get(InstrumentationRegistry.getInstrumentation().targetContext).patientDao()
    private val stamp = System.currentTimeMillis()
    private val seeded = listOf("Profiles Test A $stamp", "Profiles Test B $stamp").map { name ->
        Patient(PatientUid.generate(), name, "1990-05-17", Sex.F, createdAt = stamp).also { runBlocking { patients.insert(it) } }
    }

    /** No case references them, so they can go. */
    @After
    fun removeSeeded() = runBlocking { seeded.forEach { patients.delete(it.uid) } }

    @Test
    fun profilesAreReachableFromHomeAndSearchable() {
        val (first, other) = seeded
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Patients")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Patients").performScrollTo().performClick()
        rule.onNodeWithText("Patients").assertExists() // the screen title; the Profile tab is the phone's users

        rule.onNodeWithText("Screening aid. A clinician decides.").assertExists()
        rule.onNodeWithText("Search by name or ID").performTextInput(first.uid) // the phone may hold other patients
        rule.onNodeWithText(first.name).assertExists()
        rule.onNodeWithText(other.name).assertDoesNotExist()

        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Choose test").assertExists()
    }
}
