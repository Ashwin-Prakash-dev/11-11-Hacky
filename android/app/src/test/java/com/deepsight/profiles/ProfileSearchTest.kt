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
