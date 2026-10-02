package com.deepsight.patient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientDirectoryTest {
    private val asha = PatientProfile("Asha Nair", "DS-1001", "1988-04-12", "O+")
    private val ravi = PatientProfile("Ravi Kumar", "DS-1002", "1975-11-03", "B+")
    private val directory = PatientDirectory(listOf(asha, ravi))

    @Test
    fun searchMatchesNameOrUidIgnoringCaseAndWhitespace() {
        assertEquals(listOf(asha), directory.search("  ASHA "))
        assertEquals(listOf(ravi), directory.search("1002"))
        assertEquals(listOf(asha, ravi), directory.search("  "))
        assertTrue(directory.search("missing").isEmpty())
    }

    @Test
    fun uidLookupAndUniquenessIgnoreCase() {
        assertEquals(asha, directory.findByUid("ds-1001"))
        assertFalse(directory.add(asha.copy(fullName = "Someone Else", uid = "ds-1001")))
        assertEquals(2, directory.profiles.value.size)

        val newPatient = PatientProfile("Meera Shah", "DS-1003", "1992-08-21", "A-")
        assertTrue(directory.add(newPatient))
        assertEquals(newPatient, directory.findByUid("DS-1003"))
    }

    @Test
    fun formValidationRequiresThePocFieldsAndValidFormats() {
        val blank = validatePatient(PatientFormValues(), setOf("DS-1001"))
        assertEquals("Enter the patient's name", blank.name)
        assertEquals("Enter a UID", blank.uid)
        assertEquals("Use YYYY-MM-DD", blank.dateOfBirth)
        assertEquals("Enter a valid blood group", blank.bloodGroup)

        val duplicate = validatePatient(
            PatientFormValues("Asha Nair", "ds-1001", "1988-04-12", "o+"),
            setOf("DS-1001"),
        )
        assertEquals("UID already exists", duplicate.uid)
        assertNull(duplicate.name)
        assertNull(duplicate.dateOfBirth)
        assertNull(duplicate.bloodGroup)

        val invalidDate = validatePatient(
            PatientFormValues("New Patient", "DS-1004", "2024-02-31", "AB+"),
            emptySet(),
        )
        assertEquals("Use a real date in YYYY-MM-DD", invalidDate.dateOfBirth)

        val valid = validatePatient(
            PatientFormValues("  Meera Shah ", " ds-1003 ", "1992-08-21", "a-"),
            setOf("DS-1001"),
        )
        assertTrue(valid.isValid)
        assertEquals(PatientProfile("Meera Shah", "DS-1003", "1992-08-21", "A-"), valid.profile)
    }
}
