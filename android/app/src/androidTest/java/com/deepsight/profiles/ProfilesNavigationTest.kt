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
import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home → Patients → search → back, in the real activity, on patients stored in the phone's own database. */
@RunWith(AndroidJUnit4::class)
class ProfilesNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val db = CaseDb.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val patients = db.patientDao()
    private val stamp = System.currentTimeMillis()
    private val seeded = listOf("Profiles Test A $stamp", "Profiles Test B $stamp").map { name ->
        Patient(PatientUid.generate(), name, "1990-05-17", Sex.F, createdAt = stamp).also { runBlocking { patients.insert(it) } }
    }

    /** A failed test for the first patient: the queue never re-runs FAILED, and it carries its error. */
    private val failedCase = CaseEntity(
        "case-profiles-test-$stamp", "malaria_thin", stamp, patientUid = seeded[0].uid, status = CaseStatus.FAILED, error = "Seeded failure $stamp",
    ).also { runBlocking { db.dao().insertIfAbsent(it) } }

    /** The case first: patients don't cascade to their cases. */
    @After
    fun removeSeeded() = runBlocking {
        db.dao().deleteCase(failedCase.caseId)
        seeded.forEach { patients.delete(it.uid) }
    }

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

    @Test
    fun aPatientOpensWithTheirTestsAndItsStatus() {
        val (first, other) = seeded
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Patients")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Patients").performScrollTo().performClick()
        rule.onNodeWithText("Search by name or ID").performTextInput(first.uid.replace("-", "").drop(1)) // typed off the slip without dashes
        rule.onNodeWithText(other.name).assertDoesNotExist()
        rule.onNodeWithText(first.name).performClick()

        rule.waitUntil(10_000) { rule.onAllNodes(hasText(failedCase.error!!)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Patient").assertExists() // the screen title
        rule.onNodeWithText(first.uid, substring = true).assertExists()
        rule.onNodeWithText("Analysis failed").assertExists()
    }
}
