package com.deepsight.batch

import com.deepsight.QueueState
import com.deepsight.data.CaseEntity
import com.deepsight.data.CaseStatus

/**
 * One batch (a patient's fields for one test) not yet signed off. [progress] is (field started, total) while it runs;
 * [position] is its place in the queue while it waits, 1 = next.
 */
data class BatchItem(
    val caseId: String,
    val patient: String?,
    val packName: String,
    val status: CaseStatus,
    val progress: Pair<Int, Int>?,
    val position: Int?,
    val error: String?,
) {
    fun statusLine(): String = when (status) {
        CaseStatus.QUEUED -> if (position == 1) "Queued · next" else "Queued · #${position ?: "?"} in line"
        CaseStatus.RUNNING -> progress?.takeIf { it.first > 0 }?.let { (done, total) -> "Analysing field $done of $total…" } ?: "Loading the model…"
        CaseStatus.DONE -> "Ready for sign-off"
        CaseStatus.FAILED -> "Analysis failed: ${error ?: "unknown error"}"
        CaseStatus.SIGNED -> "Signed off"
    }
}

/** The Batch tab's list: [unsigned] cases in submit order, with the live queue's progress and positions. */
fun batchesOf(unsigned: List<CaseEntity>, patientNames: Map<String, String>, packNames: Map<String, String>, queue: QueueState): List<BatchItem> =
    unsigned.map { row ->
        BatchItem(
            caseId = row.caseId,
            patient = row.patientUid?.let { uid -> patientNames[uid]?.let { "$it · $uid" } ?: uid },
            packName = packNames[row.packId] ?: row.packId,
            status = row.status,
            progress = if (queue.running == row.caseId) queue.progress ?: (0 to 0) else null,
            position = queue.queued.indexOf(row.caseId).takeIf { it >= 0 }?.plus(1),
            error = row.error,
        )
    }
