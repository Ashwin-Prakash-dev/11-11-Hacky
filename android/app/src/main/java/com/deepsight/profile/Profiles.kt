package com.deepsight.profile

enum class ProfileRole(val label: String) { HEALTH_WORKER("Health worker"), CLINICIAN("Clinician") }

data class Profile(val id: String, val name: String, val role: ProfileRole) {
    val initials: String get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

/** The people who use this phone. In memory only for now: nothing is saved, so the list clears when the app closes. */
data class Profiles(val all: List<Profile> = emptyList(), val activeId: String? = null) {
    val active: Profile? get() = all.firstOrNull { it.id == activeId }

    /** A blank name adds nothing. The first profile added becomes the active one. */
    fun add(name: String, role: ProfileRole): Profiles {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return this
        val profile = Profile(id = "profile-${all.size + 1}", name = trimmed, role = role)
        return copy(all = all + profile, activeId = activeId ?: profile.id)
    }

    fun select(id: String): Profiles = if (all.any { it.id == id }) copy(activeId = id) else this
}
