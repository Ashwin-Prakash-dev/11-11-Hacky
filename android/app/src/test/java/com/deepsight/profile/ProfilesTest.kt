package com.deepsight.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProfilesTest {
    @Test
    fun startsEmptyWithNoActiveProfile() {
        assertEquals(emptyList<Profile>(), Profiles().all)
        assertNull(Profiles().active)
    }

    @Test
    fun addTrimsTheNameAndMakesTheFirstProfileActive() {
        val profiles = Profiles().add("  Dr Asha  ", ProfileRole.CLINICIAN)
        assertEquals("Dr Asha", profiles.all.single().name)
        assertEquals(ProfileRole.CLINICIAN, profiles.all.single().role)
        assertEquals(profiles.all.single(), profiles.active)
    }

    @Test
    fun aBlankNameAddsNothing() {
        val profiles = Profiles()
        assertSame(profiles, profiles.add("   ", ProfileRole.HEALTH_WORKER))
    }

    @Test
    fun laterProfilesDoNotChangeTheActiveOneUntilSelected() {
        val two = Profiles().add("Asha", ProfileRole.CLINICIAN).add("Ravi", ProfileRole.HEALTH_WORKER)
        assertEquals("Asha", two.active?.name)
        assertEquals(2, two.all.map { it.id }.distinct().size)

        val ravi = two.all.first { it.name == "Ravi" }
        assertEquals("Ravi", two.select(ravi.id).active?.name)
        assertSame(two, two.select("no-such-id"))
    }

    @Test
    fun initialsUseTheFirstLetterOfTheFirstTwoWords() {
        assertEquals("AK", Profile("1", "asha kumar devi", ProfileRole.CLINICIAN).initials)
        assertEquals("R", Profile("2", "Ravi", ProfileRole.HEALTH_WORKER).initials)
    }
}
