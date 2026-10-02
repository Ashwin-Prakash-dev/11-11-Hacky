package com.deepsight.result

import com.deepsight.data.CaseEntity

enum class SignOffDecision { ACCEPT, OVERRIDE }

/** A clinician's decision on a case. Stored in the case row's sign-off columns; the triage stays as the engine wrote it. */
data class SignOff(
    val caseId: String,
    val signedBy: String,
    val signedAt: Long,
    val decision: SignOffDecision,
    val note: String,
) {
    init {
        require(signedBy.isNotBlank()) { "sign-off needs a name" }
        require(decision == SignOffDecision.ACCEPT || note.isNotBlank()) { "an override needs a note" }
    }

    /** Only the sign-off columns change; caseResultJson is carried over untouched. */
    fun applyTo(case: CaseEntity) = case.copy(signedBy = signedBy, signedAt = signedAt, decision = decision.name.lowercase(), note = note)
}

fun CaseEntity.signOff(): SignOff? {
    val by = signedBy ?: return null
    return SignOff(caseId, by, signedAt ?: return null, SignOffDecision.valueOf(decision!!.uppercase()), note.orEmpty())
}
