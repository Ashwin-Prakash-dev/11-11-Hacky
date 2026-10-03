package com.deepsight.result

import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class RouterMessageTest {
    @Test
    fun matchConfirmsSelectedTest() {
        assertEquals(
            "Image matches the selected test",
            routerMessage(RouterResult(RouterVerdict.MATCH, 0.9)),
        )
    }

    @Test
    fun mismatchNamesPredictedTest() {
        assertEquals(
            "Image looks like another test: breast_breakhis",
            routerMessage(RouterResult(RouterVerdict.MISMATCH, 0.9, "breast_breakhis")),
        )
    }

    @Test
    fun rejectExplainsUnrecognisedImage() {
        assertEquals(
            "Image is not a recognised test type",
            routerMessage(RouterResult(RouterVerdict.REJECT, 0.9)),
        )
    }
}
