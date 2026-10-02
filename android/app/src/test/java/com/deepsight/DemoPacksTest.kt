package com.deepsight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPacksTest {
    @Test
    fun onlyPhoneValidatedPacksArePickable() {
        assertTrue(DemoPacks.isReady("malaria_thin"))
        listOf("fungal", "leukaemia_wbc", "breast_breakhis", "smoke").forEach { assertFalse(it, DemoPacks.isReady(it)) }
    }
}
