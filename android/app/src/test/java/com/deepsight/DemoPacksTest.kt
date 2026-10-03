package com.deepsight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPacksTest {
    @Test
    fun onlyPhoneValidatedPacksArePickable() {
        assertTrue(DemoPacks.isReady("malaria_thin"))
        assertTrue(DemoPacks.isReady("breast_breakhis")) // its 6 golden cases pass on a phone
        assertTrue(DemoPacks.isReady("leukaemia_wbc")) // its detector + classifier cascade passes on a phone
        assertFalse(DemoPacks.isReady("smoke"))
    }
}
