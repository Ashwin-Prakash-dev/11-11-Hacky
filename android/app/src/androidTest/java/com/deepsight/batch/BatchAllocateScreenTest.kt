package com.deepsight.batch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.Contracts
import com.deepsight.profiles.Patient
import com.deepsight.profiles.Sex
import com.deepsight.ui.theme.DeepSightTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The allocation screen on real BatchDraft logic: what goes where, changing it, and the human check before submit. */
@RunWith(AndroidJUnit4::class)
class BatchAllocateScreenTest {
    @get:Rule val rule = createComposeRule()

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val malaria = Contracts.parseManifest(assets.open("manifest.malaria_thin.json").bufferedReader().use { it.readText() })
    private val breast = malaria.copy(id = "breast_breakhis", displayName = "Breast histology (H&E)")
    private val packs = listOf(malaria, breast)
    private val ada = Patient("P-0000-0001", "Ada Example", "1990-05-01", Sex.F, 0L)

    private val byName = FieldRouter { file, _ ->
        when {
            file.name.startsWith("m") -> "malaria_thin"
            file.name.startsWith("b") -> "breast_breakhis"
            else -> null
        }
    }

    private var submitted = 0

    private fun show(initial: BatchDraft, patients: List<Patient> = listOf(ada)) = rule.setContent {
        DeepSightTheme {
            var draft by remember { mutableStateOf(initial) }
            BatchAllocateScreen(
                draft = draft, packs = packs, patients = patients, busy = false, error = null,
                onReassign = { id, pack -> draft = draft.reassign(id, pack) },
                onRemove = { draft = draft.remove(it) },
                onPatient = { draft = draft.withPatient(it) },
                onVerified = { draft = draft.setVerified(it) },
                onSubmit = { submitted++ },
                onClear = { draft = draft.clear() },
            )
        }
    }

    private fun draftOf(vararg names: String, patient: String? = ada.uid) =
        BatchDraft().add(names.map(::File), byName, packs.map { it.id }).withPatient(patient)

    @Test
    fun everyImageIsListedUnderItsModule() {
        show(draftOf("m1.jpg", "b1.png", "unknown.jpg"))
        rule.onNodeWithText("Malaria (thin smear) · 1 image").assertExists()
        rule.onNodeWithText("Breast histology (H&E) · 1 image").assertExists()
        rule.onNodeWithText("Not allocated · 1 image").assertExists()
        rule.onNodeWithText("m1.jpg").assertExists()
        rule.onNodeWithText("b1.png").assertExists()
        rule.onNodeWithText("unknown.jpg").assertExists()
    }

    @Test
    fun theUserCanMoveAnImageToAnotherModule() {
        show(draftOf("m1.jpg", "b1.png"))
        rule.onNodeWithText("Changed by you").assertDoesNotExist()

        rule.onNodeWithTag("change-2").performScrollTo().performClick()
        rule.onNodeWithTag("choose-2-malaria_thin").performClick()

        rule.onNodeWithText("Malaria (thin smear) · 2 images").assertExists()
        rule.onNodeWithText("Breast histology (H&E) · 1 image").assertDoesNotExist()
        rule.onNodeWithText("Changed by you").assertExists()
    }

    @Test
    fun submitWaitsForAHumanToVerifyAndAnyChangeUndoesIt() {
        submitted = 0
        show(draftOf("m1.jpg", "b1.png"))
        val submit = rule.onNodeWithText("Analyse 2 images in 2 modules")
        submit.performScrollTo().assertIsNotEnabled()

        rule.onNodeWithText("I checked that every image is in the right module").performScrollTo().performClick()
        rule.onNodeWithText("Analyse 2 images in 2 modules").performScrollTo().assertIsEnabled()

        rule.onNodeWithTag("change-2").performScrollTo().performClick() // a change after verifying...
        rule.onNodeWithTag("choose-2-malaria_thin").performClick()
        rule.onNodeWithText("Analyse 2 images in 1 module").performScrollTo().assertIsNotEnabled() // ...asks again

        rule.onNodeWithText("I checked that every image is in the right module").performScrollTo().performClick()
        rule.onNodeWithText("Analyse 2 images in 1 module").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, submitted)
    }

    @Test
    fun anUnallocatedImageBlocksVerifying() {
        show(draftOf("m1.jpg", "unknown.jpg"))
        rule.onNodeWithText("I checked that every image is in the right module").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Choose a module for every image first", substring = true).assertExists()

        rule.onNodeWithTag("change-2").performScrollTo().performClick()
        rule.onNodeWithTag("choose-2-breast_breakhis").performClick()
        rule.onNodeWithText("I checked that every image is in the right module").performScrollTo().assertIsEnabled()
    }

    @Test
    fun aPatientIsNeededToSubmit() {
        show(draftOf("m1.jpg", patient = null).setVerified(true))
        rule.onNodeWithText("Choose a patient").assertExists()
        rule.onNodeWithText("Analyse 1 image in 1 module").performScrollTo().assertIsNotEnabled()

        rule.onNodeWithTag("patient-menu").performScrollTo().performClick()
        rule.onNodeWithTag("patient-${ada.uid}").performClick()
        rule.onNodeWithText("Analyse 1 image in 1 module").performScrollTo().assertIsEnabled()
    }

    @Test
    fun emptyDraftSaysSo() {
        show(BatchDraft())
        rule.onNodeWithText("No images selected").assertExists()
    }

    @Test
    fun saysThatTheRouterIsAPlaceholder() {
        show(draftOf("m1.jpg"))
        rule.onAllNodesWithText("placeholder", substring = true)[0].assertExists()
    }
}
