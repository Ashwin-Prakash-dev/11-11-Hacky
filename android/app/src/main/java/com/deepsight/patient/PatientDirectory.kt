package com.deepsight.patient

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** File-friendly UI record that a future CSV/text source can populate without changing screens. */
data class PatientProfile(
    val fullName: String,
    val uid: String,
    val dateOfBirth: String,
    val bloodGroup: String,
)

class PatientDirectory(initial: List<PatientProfile>) {
    private val _profiles = MutableStateFlow(initial)
    val profiles: StateFlow<List<PatientProfile>> = _profiles.asStateFlow()

    fun search(query: String): List<PatientProfile> = filterPatients(_profiles.value, query)

    fun findByUid(uid: String): PatientProfile? =
        _profiles.value.firstOrNull { it.uid.equals(uid.trim(), ignoreCase = true) }

    fun add(profile: PatientProfile): Boolean {
        if (findByUid(profile.uid) != null) return false
        _profiles.value = _profiles.value + profile
        return true
    }
}

fun filterPatients(profiles: List<PatientProfile>, query: String): List<PatientProfile> {
    val needle = query.trim()
    if (needle.isEmpty()) return profiles
    return profiles.filter {
        it.fullName.contains(needle, ignoreCase = true) || it.uid.contains(needle, ignoreCase = true)
    }
}

data class PatientFormValues(
    val name: String = "",
    val uid: String = "",
    val dateOfBirth: String = "",
    val bloodGroup: String = "",
)

data class PatientFormValidation(
    val name: String? = null,
    val uid: String? = null,
    val dateOfBirth: String? = null,
    val bloodGroup: String? = null,
    val profile: PatientProfile? = null,
) {
    val isValid: Boolean get() = profile != null
}

val supportedBloodGroups = setOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

fun validatePatient(values: PatientFormValues, existingUids: Set<String>): PatientFormValidation {
    val name = values.name.trim()
    val uid = values.uid.trim().uppercase()
    val dob = values.dateOfBirth.trim()
    val blood = values.bloodGroup.trim().uppercase()
    val nameError = if (name.isEmpty()) "Enter the patient's name" else null
    val uidError = when {
        uid.isEmpty() -> "Enter a UID"
        existingUids.any { it.equals(uid, ignoreCase = true) } -> "UID already exists"
        else -> null
    }
    val dobError = when {
        !DOB_PATTERN.matches(dob) -> "Use YYYY-MM-DD"
        !isRealDate(dob) -> "Use a real date in YYYY-MM-DD"
        else -> null
    }
    val bloodError = if (blood !in supportedBloodGroups) "Enter a valid blood group" else null
    val profile = if (listOf(nameError, uidError, dobError, bloodError).all { it == null }) {
        PatientProfile(name, uid, dob, blood)
    } else null
    return PatientFormValidation(nameError, uidError, dobError, bloodError, profile)
}

private val DOB_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

private fun isRealDate(value: String): Boolean {
    val parts = value.split('-').mapNotNull(String::toIntOrNull)
    if (parts.size != 3) return false
    val (year, month, day) = parts
    if (year !in 1900..2100 || month !in 1..12) return false
    val leap = year % 400 == 0 || year % 4 == 0 && year % 100 != 0
    val maxDay = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..maxDay
}

object DemoPatients {
    val profiles = listOf(
        PatientProfile("Asha Nair", "DS-1001", "1988-04-12", "O+"),
        PatientProfile("Ravi Kumar", "DS-1002", "1975-11-03", "B+"),
    )
}
