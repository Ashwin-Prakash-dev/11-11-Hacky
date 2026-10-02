package com.deepsight.ui.theme

import androidx.compose.ui.graphics.Color

// DeepSight palette: a teal primary, Material 3 tonal values (tone 40 for light, 80 for dark).
val Teal40 = Color(0xFF006A6A)
val Teal90 = Color(0xFF9CF1F0)
val Teal80 = Color(0xFF80D5D4)
val Teal20 = Color(0xFF003737)
val Teal30 = Color(0xFF004F4F)

val SlateTeal40 = Color(0xFF4A6363)
val SlateTeal80 = Color(0xFFB0CCCB)
val Indigo40 = Color(0xFF4B607C)
val Indigo80 = Color(0xFFB3C8E8)

/** Container and content colours for one triage tone. */
data class TonePair(val container: Color, val content: Color)

/** One pair per triage tone. Text always says the level too, so colour is never the only signal. */
data class TriageColors(val alert: TonePair, val caution: TonePair, val clear: TonePair)

val LightTriageColors = TriageColors(
    alert = TonePair(Color(0xFFFFDAD6), Color(0xFF410002)),
    caution = TonePair(Color(0xFFFFDEA6), Color(0xFF271900)),
    clear = TonePair(Color(0xFFC3EFCB), Color(0xFF00210E)),
)

val DarkTriageColors = TriageColors(
    alert = TonePair(Color(0xFF93000A), Color(0xFFFFDAD6)),
    caution = TonePair(Color(0xFF5E4200), Color(0xFFFFDEA6)),
    clear = TonePair(Color(0xFF0E5130), Color(0xFFC3EFCB)),
)
