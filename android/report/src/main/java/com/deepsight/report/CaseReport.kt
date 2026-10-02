package com.deepsight.report

import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.TriageLevel

// The report only words what the engine decided (AGENTS.md: the LLM narrates, it never decides or changes triage).

/** Plain-language meaning of each triage level, for any pack. Screening wording: never a diagnosis. */
fun triageMeaning(level: TriageLevel): String = when (level) {
    TriageLevel.ABNORMAL_FLAG -> "A screening rule flagged this case. A clinician should review it."
    TriageLevel.NEEDS_EXPERT -> "The screen could not decide. Refer this case for expert review."
    TriageLevel.NORMAL_SCREEN -> "No screening rule flagged this case. A clinician still signs off."
}

/** The engine's own rule ids (contracts/README.md, triage steps), in words. */
private val ENGINE_RULES = mapOf(
    "engine.insufficient_fields" to "not enough fields passed the quality check",
    "engine.router_reject" to "an image was not recognised as this test",
    "engine.router_mismatch" to "an image looked like another test",
    "engine.no_rule_matched" to "no screening rule matched",
    "engine.uncertain" to "the model was uncertain",
)

private fun fieldsLine(case: CaseResult, fields: List<FieldResult>): String {
    val rejected = fields.filterNot { it.quality.pass }
    val reasons = rejected.flatMap { it.quality.reasons }.map { it.name.lowercase() }.distinct()
    val rejectedPart = if (rejected.isEmpty()) "" else "; ${rejected.size} was rejected (${reasons.joinToString().ifEmpty { "quality" }})"
    return "${case.fieldsPassed} of ${case.fieldIds.size} fields passed the image-quality check$rejectedPart"
}

private fun countsLine(case: CaseResult): String =
    if (case.counts.isEmpty()) "No passed field, so nothing was counted"
    else "Model counts across the passed fields: ${case.counts.entries.joinToString { "${it.key} ${it.value}" }}"

private fun ruleLine(case: CaseResult): String {
    val rule = case.triage.ruleId
    val words = ENGINE_RULES[rule]
    return "Triage: ${case.triage.level.name} (${if (words != null) "$rule: $words" else "rule $rule"})"
}

/** The deterministic report: the fallback whenever Gemma is missing, slow, or fails [checkNarrative]. */
fun templateReport(case: CaseResult, fields: List<FieldResult>, testName: String): String = buildList {
    add("$testName screen.")
    add("${fieldsLine(case, fields)}.")
    add("${countsLine(case)}.")
    add("${ruleLine(case)}.")
    add(triageMeaning(case.triage.level))
    if (case.uncertainty.flag) add("The model was uncertain on part of this case${case.uncertainty.reason?.let { " ($it)" } ?: ""}.")
    if (case.triage.provisional) add("The thresholds are provisional and not clinically validated.")
    add("This is screening support, not a diagnosis.")
}.joinToString(" ")

/** What Gemma is asked: facts only, the exact level, plain prose. Other level names are never shown to it. */
fun gemmaPrompt(case: CaseResult, fields: List<FieldResult>, testName: String): String {
    val level = case.triage.level.name
    return """
        Write the summary of a microscopy screening report for the clinician who will review it.
        Rules:
        - Use only the facts below. Do not add findings, causes or treatment advice.
        - This is screening support, not a diagnosis. Do not say the patient has or does not have a disease.
        - State the triage level in exactly this form: "triage level $level". Do not name any other triage level.
        - Say what the triage level means for the clinician, using the meaning given.
        - Write 2 or 3 plain sentences. Use digits for numbers. No heading, no list, no markdown.

        Facts:
        - Test: $testName
        - ${fieldsLine(case, fields)}
        - ${countsLine(case)}
        - Triage level: $level, set by the fixed screening rule ${case.triage.ruleId}${if (case.triage.provisional) " (provisional thresholds, not clinically validated)" else ""}
        - Meaning: ${triageMeaning(case.triage.level)}${if (case.uncertainty.flag) "\n- The model was uncertain on part of this case" else ""}
    """.trimIndent()
}

data class NarrativeCheck(val ok: Boolean, val reason: String? = null)

private fun hasToken(text: String, token: String) = Regex("(?<![A-Z_])$token(?![A-Z_])").containsMatchIn(text)

/** docs/architecture.md, Report: the text must state the exact triage level and no different level. */
fun checkNarrative(text: String, level: TriageLevel): NarrativeCheck {
    if (text.isBlank()) return NarrativeCheck(false, "is empty")
    if (!hasToken(text, level.name)) return NarrativeCheck(false, "does not state the triage level ${level.name}")
    TriageLevel.entries.firstOrNull { it != level && hasToken(text, it.name) }?.let {
        return NarrativeCheck(false, "names another triage level: ${it.name}")
    }
    return NarrativeCheck(true)
}

/** Gemma's text as one plain paragraph: markdown marks, list bullets and a leading "Heading:" line removed. */
fun cleanNarrative(raw: String): String {
    val lines = raw.lines()
        .map { it.replace("**", "").replace("__", "").trim().removePrefix("#").trimStart('#', ' ') }
        .map { it.removePrefix("* ").removePrefix("- ").trim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    val first = lines.firstOrNull()
    if (first != null && lines.size > 1 && first.length <= 40 && (first.endsWith(":") || !first.contains('.'))) lines.removeAt(0)
    return lines.joinToString(" ").replace(Regex("\\s+"), " ").trim()
}
