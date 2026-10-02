package com.deepsight.ui.components

import com.deepsight.engine.contract.TriageLevel

/** How loudly the UI presents a triage level. Colours come from the theme (`LocalTriageColors`). */
enum class TriageTone { ALERT, CAUTION, CLEAR }

/** Presentation only: the engine decides the level (contracts/README.md); this never changes it. */
data class TriageStyle(val tone: TriageTone, val meaning: String)

/** Plain-language meaning of each level, for any pack. Screening wording: no diagnosis. */
fun triageStyle(level: TriageLevel): TriageStyle = when (level) {
    TriageLevel.ABNORMAL_FLAG -> TriageStyle(TriageTone.ALERT, "A screening rule flagged this case. A clinician should review it.")
    TriageLevel.NEEDS_EXPERT -> TriageStyle(TriageTone.CAUTION, "The screen could not decide. Refer this case for expert review.")
    TriageLevel.NORMAL_SCREEN -> TriageStyle(TriageTone.CLEAR, "No screening rule flagged this case. A clinician still signs off.")
}
