package com.deepsight.ui.components

import com.deepsight.engine.contract.TriageLevel
import com.deepsight.report.triageMeaning

/** How loudly the UI presents a triage level. Colours come from the theme (`LocalTriageColors`). */
enum class TriageTone { ALERT, CAUTION, CLEAR }

/** Presentation only: the engine decides the level (contracts/README.md); this never changes it. */
data class TriageStyle(val tone: TriageTone, val meaning: String)

/** The same plain-language meaning the report uses (`:report`), so screen and report never disagree. */
fun triageStyle(level: TriageLevel): TriageStyle = when (level) {
    TriageLevel.ABNORMAL_FLAG -> TriageStyle(TriageTone.ALERT, triageMeaning(level))
    TriageLevel.NEEDS_EXPERT -> TriageStyle(TriageTone.CAUTION, triageMeaning(level))
    TriageLevel.NORMAL_SCREEN -> TriageStyle(TriageTone.CLEAR, triageMeaning(level))
}
