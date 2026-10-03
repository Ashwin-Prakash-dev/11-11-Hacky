package com.deepsight.profiles

import com.deepsight.engine.contract.TriageLevel

/**
 * A patient profile as the list shows it. UI-only for now: there is no patient table yet, so the screen is fed
 * [SampleProfiles]. [lastLevel] is the triage level of the latest case, null when the patient has none.
 */
data class PatientProfile(
    val id: String,
    val name: String,
    val ageYears: Int,
    val sex: String,
    val caseCount: Int,
    val lastLevel: TriageLevel?,
    val lastScreening: String? = null,
)

/**
 * Every whitespace-separated word of [query] must appear in the name or the ID, ignoring case; or the query is part of
 * the ID typed without its `P-` and dashes (at least 4 characters, so "Pa" doesn't match every ID with an A). Order is kept.
 */
fun searchProfiles(profiles: List<PatientProfile>, query: String): List<PatientProfile> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return profiles
    val compact = compactUid(query).takeIf { it.length >= 4 }
    return profiles.filter { p ->
        val haystack = "${p.name} ${p.id}".lowercase()
        words.all { it in haystack } || compact != null && compact in compactUid(p.id)
    }
}

private fun compactUid(s: String) = s.uppercase().filterNot { it == '-' || it.isWhitespace() }.removePrefix("P")

/** First letter of the first and last word, upper case; "?" for a blank name. */
fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (words.size) {
        0 -> "?"
        1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/** Made-up people for the UI preview. None of these is a real person; replace with Room data when profiles are stored. */
object SampleProfiles {
    val all = listOf(
        PatientProfile("P-1001", "Amara Okafor", 34, "F", 3, TriageLevel.ABNORMAL_FLAG, "2 Oct 2026"),
        PatientProfile("P-1002", "Rahul Menon", 61, "M", 2, TriageLevel.NEEDS_EXPERT, "1 Oct 2026"),
        PatientProfile("P-1003", "Lucía Fernández", 47, "F", 1, TriageLevel.NORMAL_SCREEN, "28 Sep 2026"),
        PatientProfile("P-1004", "Kofi Mensah", 29, "M", 4, TriageLevel.ABNORMAL_FLAG, "27 Sep 2026"),
        PatientProfile("P-1005", "Mei Lin Zhao", 52, "F", 2, TriageLevel.NORMAL_SCREEN, "22 Sep 2026"),
        PatientProfile("P-1006", "Tariq Hassan", 68, "M", 1, TriageLevel.NEEDS_EXPERT, "19 Sep 2026"),
        PatientProfile("P-1007", "Sofia Petrova", 41, "F", 0, null),
        PatientProfile("P-1008", "Diego Alvarez", 23, "M", 1, TriageLevel.NORMAL_SCREEN, "11 Sep 2026"),
    )
}
