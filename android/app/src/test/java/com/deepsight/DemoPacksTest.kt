package com.deepsight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPacksTest {
    @Test
    fun onlyPhoneValidatedPacksArePickable() {
        assertTrue(DemoPacks.isReady("malaria_thin"))
        assertTrue(DemoPacks.isReady("breast_breakhis")) // its 6 golden cases pass on a phone
        listOf("fungal", "leukaemia_wbc", "smoke").forEach { assertFalse(it, DemoPacks.isReady(it)) }
    }
}
