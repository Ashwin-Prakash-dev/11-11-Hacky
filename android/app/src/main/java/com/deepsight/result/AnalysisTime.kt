package com.deepsight.result

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** "3 Oct 2026, 03:40:07": date and time to the second, in the phone's time zone unless told otherwise. */
fun formatAnalysedAt(millis: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("d MMM yyyy, HH:mm:ss", locale).apply { timeZone = zone }.format(Date(millis))

/** The line the result screen shows. Cases saved before the time was recorded have none. */
fun analysedAtLine(millis: Long?, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
    if (millis == null) "Analysis time not recorded" else "Analysed ${formatAnalysedAt(millis, zone, locale)}"
