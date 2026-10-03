package com.deepsight.history

import com.deepsight.HistoryItem
import com.deepsight.data.CaseStatus

/**
 * Whether a history entry may be deleted. A queued or analysing case is still owned by the queue, which writes its result
 * into the stored case when it finishes, so deleting it now would break that write. Finished ones (done, failed, signed) go.
 */
fun canDelete(status: CaseStatus): Boolean = status != CaseStatus.QUEUED && status != CaseStatus.RUNNING

fun toggled(selected: Set<String>, caseId: String): Set<String> = if (caseId in selected) selected - caseId else selected + caseId

/** The selection after the list changed: cases that are gone, or have become busy, drop out. */
fun prunedSelection(selected: Set<String>, items: List<HistoryItem>): Set<String> {
    val deletable = items.filter { canDelete(it.status) }.map { it.caseId }.toSet()
    return selected.filter { it in deletable }.toSet()
}

fun deleteTitle(count: Int): String = if (count == 1) "Delete 1 case?" else "Delete $count cases?"

/** Says what is lost, and that signed-off records are among them when they are. */
fun deleteMessage(items: List<HistoryItem>): String {
    val signed = items.count { it.status == CaseStatus.SIGNED }
    val base = "This removes the result, report and images from this phone. This cannot be undone."
    return when (signed) {
        0 -> base
        else -> "$base ${if (signed == 1) "1 is signed off" else "$signed are signed off"}; its sign-off record is deleted with it."
    }
}
