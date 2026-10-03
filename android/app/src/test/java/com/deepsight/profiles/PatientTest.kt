package com.deepsight.profiles

import java.util.Random
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientTest {
    private fun patient(uid: String = "P-0123-4567", name: String = "Ada Example", dob: String = "1990-05-17") =
        Patient(uid, name, dob, Sex.F, createdAt = 0L)

    @Test
    fun validPatientIsAccepted() {
        assertEquals("Ada Example", patient().name)
    }

    @Test
    fun blankNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { patient(name = "  ") }
    }

    @Test
    fun futureDateOfBirthIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { patient(dob = "2999-01-01") }
    }

    @Test
    fun malformedOrImpossibleDatesAreRejected() {
        listOf("1990-1-1", "17/05/1990", "1990-02-30", "1990-13-01", "").forEach { dob ->
            assertThrows(dob, IllegalArgumentException::class.java) { patient(dob = dob) }
        }
    }

    @Test
    fun badUidsAreRejected() {
        // I, L, O and U are not Crockford base32; lower case, missing prefix or wrong grouping are not the shown format.
        listOf("P-0123-456I", "P-0123-456L", "P-0123-456O", "P-0123-456U", "p-0123-4567", "0123-4567", "P-01234567", "P-0123-45678")
            .forEach { uid -> assertThrows(uid, IllegalArgumentException::class.java) { patient(uid = uid) } }
    }

    @Test
    fun generatedUidsHaveTheSlipFormatAndCrockfordAlphabet() {
        val random = Random(7)
        repeat(1_000) {
            val uid = PatientUid.generate(random)
            assertTrue(uid, Regex("P-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}").matches(uid))
            assertTrue(PatientUid.isValid(uid))
        }
        assertFalse(PatientUid.isValid("P-0123-456I"))
    }

    @Test
    fun generatedUidsUseTheWholeAlphabet() {
        val random = Random(11)
        val seen = (1..2_000).flatMap { PatientUid.generate(random).filter { it != '-' }.drop(1).toList() }.toSet()
        assertEquals(PatientUid.ALPHABET.toSet(), seen)
    }

    @Test
    fun ageCountsWholeYearsUpToTheBirthday() {
        val utc = TimeZone.getTimeZone("UTC")
        val p = patient(dob = "1990-05-17")
        assertEquals(35, p.ageOn(millis("2026-05-16", utc), utc))
        assertEquals(36, p.ageOn(millis("2026-05-17", utc), utc))
    }

    private fun millis(date: String, zone: TimeZone) =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = zone }.parse(date)!!.time
}
