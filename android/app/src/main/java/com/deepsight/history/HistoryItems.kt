package com.deepsight.history

import com.deepsight.HistoryItem
import com.deepsight.data.CaseEntity
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.TriageLevel

/** History and patient-profile rows. [manifests] by pack id; a pack not loaded (yet) shows its id. The level is null until triage has run. */
fun historyItemsOf(rows: List<CaseEntity>, manifests: Map<String, PackManifest>): List<HistoryItem> = rows.map { row ->
    val manifest = manifests[row.packId]
    HistoryItem(
        caseId = row.caseId, packName = manifest?.displayName ?: row.packId,
        level = row.caseResultJson?.let { runCatching { Contracts.parseCaseResult(it).triage.level }.getOrNull() },
        signedAt = row.signedAt, signedBy = row.signedBy, decision = row.decision, status = row.status,
        classificationOnly = manifest?.triage?.rules?.all { it.level == TriageLevel.NEEDS_EXPERT } == true,
        error = row.error, createdAt = row.createdAt,
    )
}
