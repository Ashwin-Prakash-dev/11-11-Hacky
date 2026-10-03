package com.deepsight.batch

import com.deepsight.capture.CaseStore

/** A case made from one module's images and handed to the queue. */
data class SubmittedBatch(val caseId: String, val packId: String, val imageCount: Int)

/**
 * Turns a verified [BatchDraft] into one case per module (the case store holds the images, the queue analyses them) so
 * the whole batch goes in together. All images are copied before anything is queued, so a file that can't be read
 * leaves no half-submitted batch behind.
 */
class BatchSubmitter(
    private val store: CaseStore,
    private val enqueue: suspend (patientUid: String, packId: String, caseId: String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun submit(draft: BatchDraft, packOrder: List<String>): List<SubmittedBatch> {
        require(draft.canSubmit) { "The allocation has not been verified, or an image has no module or the patient is missing" }
        val stamp = clock()
        val prepared = draft.groups(packOrder).mapIndexed { index, (packId, images) ->
            val caseId = "case-$stamp-${index + 1}"
            images.forEach { image -> // bytes as they are, in the order shown, like a single case's import
                image.file.inputStream().use { store.import(caseId, it, image.file.extension.ifEmpty { "jpg" }) }
            }
            SubmittedBatch(caseId, packId, images.size)
        }
        prepared.forEach { enqueue(checkNotNull(draft.patientUid), it.packId, it.caseId) }
        return prepared
    }
}
