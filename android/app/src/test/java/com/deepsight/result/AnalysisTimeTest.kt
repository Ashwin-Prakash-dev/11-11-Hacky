package com.deepsight.result

import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisTimeTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun formatsDateAndTimeToTheSecond() {
        assertEquals("1 Jan 1970, 00:00:00", formatAnalysedAt(0L, utc, Locale.ENGLISH))
        assertEquals("3 Oct 2026, 03:40:07", formatAnalysedAt(1_790_998_807_000L, utc, Locale.ENGLISH))
    }

    @Test
    fun usesTheGivenTimeZone() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        assertEquals("1 Jan 1970, 05:30:00", formatAnalysedAt(0L, ist, Locale.ENGLISH))
    }

    @Test
    fun missingTimeIsSaidPlainly() {
        assertEquals("Analysis time not recorded", analysedAtLine(null, utc, Locale.ENGLISH))
        assertEquals("Analysed 1 Jan 1970, 00:00:00", analysedAtLine(0L, utc, Locale.ENGLISH))
    }
}
