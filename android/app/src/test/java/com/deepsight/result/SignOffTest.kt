package com.deepsight.result

import com.deepsight.data.CaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SignOffTest {
    private val case = CaseEntity("c1", "malaria_thin", 1L, """{"triage":{"level":"ABNORMAL_FLAG"}}""")

    @Test
    fun signOffNeverTouchesStoredTriage() {
        val signed = SignOff("c1", "Dr A", 5L, SignOffDecision.OVERRIDE, "smear looks clear").applyTo(case)
        assertEquals(case.caseResultJson, signed.caseResultJson)
        assertEquals(case.createdAt, signed.createdAt)
    }

    @Test
    fun signOffRoundTripsThroughEntity() {
        val signOff = SignOff("c1", "Dr A", 5L, SignOffDecision.ACCEPT, "")
        assertEquals(signOff, signOff.applyTo(case).signOff())
        assertNull(case.signOff())
    }

    @Test
    fun overrideNeedsNoteAndSignerNeedsName() {
        assertThrows(IllegalArgumentException::class.java) { SignOff("c1", "Dr A", 1L, SignOffDecision.OVERRIDE, " ") }
        assertThrows(IllegalArgumentException::class.java) { SignOff("c1", " ", 1L, SignOffDecision.ACCEPT, "") }
    }
}
