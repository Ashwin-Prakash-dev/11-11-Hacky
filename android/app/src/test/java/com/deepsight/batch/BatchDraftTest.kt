package com.deepsight.batch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDraftTest {
    private val packs = listOf("malaria_thin", "breast_breakhis")
    private fun f(name: String) = File(name)

    /** Routes by file name so tests choose the allocation. */
    private val byName = FieldRouter { file, _ ->
        when {
            file.name.startsWith("m") -> "malaria_thin"
            file.name.startsWith("b") -> "breast_breakhis"
            else -> null
        }
    }

    private fun draft() = BatchDraft().add(listOf(f("m1.jpg"), f("b1.png"), f("m2.jpg")), byName, packs).withPatient("P-1")

    @Test
    fun addingRoutesEveryImageAndKeepsTheSuggestion() {
        val d = draft()
        assertEquals(listOf("malaria_thin", "breast_breakhis", "malaria_thin"), d.images.map { it.assigned })
        assertEquals(d.images.map { it.assigned }, d.images.map { it.suggested })
        assertFalse(d.images.any { it.changedByUser })
        assertEquals(listOf(1, 2, 3), d.images.map { it.id })
    }

    @Test
    fun routerAnswersOutsideTheOfferedModulesAreTreatedAsNotRecognised() {
        val d = BatchDraft().add(listOf(f("x.jpg")), { _, _ -> "fungal" }, packs)
        assertNull(d.images.single().assigned)
    }

    @Test
    fun groupsShowWhichImagesGoToWhichModuleInModuleOrder() {
        val groups = draft().groups(packs)
        assertEquals(listOf("malaria_thin", "breast_breakhis"), groups.map { it.first })
        assertEquals(listOf("m1.jpg", "m2.jpg"), groups[0].second.map { it.file.name })
        assertEquals(listOf("b1.png"), groups[1].second.map { it.file.name })
        assertEquals(
            listOf("breast_breakhis"),
            draft().reassign(1, "breast_breakhis").reassign(3, "breast_breakhis").groups(packs).map { it.first },
        )
    }

    @Test
    fun theUserCanChangeAModuleAndTheChangeIsRemembered() {
        val d = draft().reassign(2, "malaria_thin")
        val moved = d.images.first { it.id == 2 }
        assertEquals("malaria_thin", moved.assigned)
        assertEquals("breast_breakhis", moved.suggested)
        assertTrue(moved.changedByUser)
        assertEquals(1, d.changedCount)
        assertFalse(d.reassign(2, "breast_breakhis").images.first { it.id == 2 }.changedByUser) // changing back is no change
    }

    @Test
    fun cannotSubmitUntilAHumanHasVerified() {
        val d = draft()
        assertFalse(d.canSubmit)
        assertTrue(d.setVerified(true).canSubmit)
    }

    @Test
    fun anyChangeAfterVerifyingNeedsVerifyingAgain() {
        val verified = draft().setVerified(true)
        assertFalse(verified.reassign(1, "breast_breakhis").verified)
        assertFalse(verified.remove(1).verified)
        assertFalse(verified.add(listOf(f("m3.jpg")), byName, packs).verified)
        assertTrue(verified.withPatient("P-2").verified) // the patient is not part of the allocation
    }

    @Test
    fun unassignedImagesBlockVerifyingAndSubmitting() {
        val d = BatchDraft().add(listOf(f("m1.jpg"), f("unknown.jpg")), byName, packs).withPatient("P-1")
        assertEquals(listOf("unknown.jpg"), d.unassigned.map { it.file.name })
        assertFalse(d.setVerified(true).verified)
        assertTrue(d.reassign(2, "malaria_thin").setVerified(true).canSubmit)
    }

    @Test
    fun needsAnImageAndAPatient() {
        assertFalse(BatchDraft().setVerified(true).canSubmit)
        val noPatient = BatchDraft().add(listOf(f("m1.jpg")), byName, packs).setVerified(true)
        assertFalse(noPatient.canSubmit)
        assertTrue(noPatient.withPatient("P-1").canSubmit)
    }

    @Test
    fun removingAndClearing() {
        assertEquals(listOf("m1.jpg", "m2.jpg"), draft().remove(2).images.map { it.file.name })
        assertEquals(draft().images, draft().remove(99).images)
        assertTrue(draft().clear().images.isEmpty())
        assertNull(draft().clear().patientUid)
    }

    @Test
    fun idsNeverRepeatEvenAfterRemoving() {
        val d = draft().remove(3).add(listOf(f("m9.jpg")), byName, packs)
        assertEquals(listOf(1, 2, 4), d.images.map { it.id })
    }
}
