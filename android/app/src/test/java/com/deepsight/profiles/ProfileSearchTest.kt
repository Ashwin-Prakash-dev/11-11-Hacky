package com.deepsight.profiles

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSearchTest {
    private val ada = PatientProfile("P-1001", "Ada Example", 34, "F", 3, null)
    private val ben = PatientProfile("P-1002", "Ben Sample", 61, "M", 1, null)
    private val cara = PatientProfile("P-2003", "Cara Sample-Jones", 47, "F", 0, null)
    private val all = listOf(ada, ben, cara)

    @Test
    fun blankQueryKeepsEveryoneInOrder() {
        assertEquals(all, searchProfiles(all, ""))
        assertEquals(all, searchProfiles(all, "   "))
    }

    @Test
    fun matchesNameOrIdIgnoringCaseAndSpaces() {
        assertEquals(listOf(ada), searchProfiles(all, "  ADA "))
        assertEquals(listOf(ben), searchProfiles(all, "p-1002"))
        assertEquals(listOf(ben, cara), searchProfiles(all, "sample"))
    }

    @Test
    fun matchesPartsOfTheNameAndIdTogether() {
        assertEquals(listOf(cara), searchProfiles(all, "sample jones"))
        assertEquals(listOf(cara), searchProfiles(all, "2003 cara"))
        assertEquals(emptyList<PatientProfile>(), searchProfiles(all, "ada ben"))
    }

    @Test
    fun matchesAStoredUidTypedWithoutItsDashesOrPrefix() {
        val dan = PatientProfile("P-V9JH-XPR4", "Dan Test", 40, "M", 0, null)
        val pam = PatientProfile("P-AB12-CD34", "Pam Abel", 50, "F", 0, null)
        val people = listOf(dan, pam)
        assertEquals(listOf(dan), searchProfiles(people, "v9jhxpr4"))
        assertEquals(listOf(dan), searchProfiles(people, "P-V9JH-XPR4"))
        assertEquals(listOf(dan), searchProfiles(people, "p v9jh"))
        assertEquals(listOf(dan), searchProfiles(people, "PV9JHX"))
        assertEquals(listOf(pam), searchProfiles(people, "PAM")) // a name, not the ID "AB12…" with its P dropped
    }

    @Test
    fun shortQueriesMatchNamesNotCompactedIds() {
        val pam = PatientProfile("P-AB12-CD34", "Pam Abel", 50, "F", 0, null)
        val dan = PatientProfile("P-A000-0000", "Dan Test", 40, "M", 0, null)
        assertEquals(listOf(pam), searchProfiles(listOf(pam, dan), "Pa"))
        assertEquals(listOf(pam, dan), searchProfiles(listOf(pam, dan), "p")) // "P-" is in every ID, as before
        assertEquals(emptyList<PatientProfile>(), searchProfiles(listOf(pam, dan), "%"))
    }

    @Test
    fun noMatchGivesAnEmptyList() {
        assertEquals(emptyList<PatientProfile>(), searchProfiles(all, "zzz"))
    }

    @Test
    fun initialsUseTheFirstAndLastWord() {
        assertEquals("AE", initials("Ada Example"))
        assertEquals("CS", initials("cara Sample-Jones"))
        assertEquals("M", initials("Madonna"))
        assertEquals("AL", initials("Ada B. Lovelace"))
        assertEquals("?", initials("   "))
    }

    @Test
    fun sampleProfilesAreFictionalAndUnique() {
        assertEquals(SampleProfiles.all.size, SampleProfiles.all.map { it.id }.toSet().size)
        assert(SampleProfiles.all.size >= 6)
    }
}
