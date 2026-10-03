package com.deepsight.batch

import java.io.File

/**
 * One uploaded image in a [BatchDraft]. [suggested] is what the router said (null: not recognised), [assigned] is the
 * module it will be analysed in, which the user may have changed.
 */
data class DraftImage(val id: Int, val file: File, val suggested: String?, val assigned: String?) {
    val changedByUser: Boolean get() = assigned != suggested
}

/**
 * Images waiting to be allocated to test modules and submitted together. Pure state: every function returns a new draft.
 *
 * The allocation must be verified by a person ([verified]) before [canSubmit]. Anything that changes the allocation
 * (adding, removing or moving an image) clears [verified], so what was checked is always what gets submitted.
 */
data class BatchDraft(
    val images: List<DraftImage> = emptyList(),
    val patientUid: String? = null,
    val verified: Boolean = false,
    val nextId: Int = 1,
) {
    /** Routes each new file with [router] over [packIds]. An answer outside [packIds] counts as "not recognised". */
    fun add(files: List<File>, router: FieldRouter, packIds: List<String>): BatchDraft {
        var id = nextId
        val added = files.map { file ->
            val suggested = router.route(file, packIds)?.takeIf { it in packIds }
            DraftImage(id++, file, suggested, suggested)
        }
        return copy(images = images + added, verified = false, nextId = id)
    }

    /** Puts an image in [packId], or back to "not allocated" with null. */
    fun reassign(id: Int, packId: String?): BatchDraft =
        if (images.none { it.id == id }) this
        else copy(images = images.map { if (it.id == id) it.copy(assigned = packId) else it }, verified = false)

    fun remove(id: Int): BatchDraft =
        if (images.none { it.id == id }) this else copy(images = images.filterNot { it.id == id }, verified = false)

    /** The patient the batch belongs to. Not part of the allocation, so verifying survives it. */
    fun withPatient(uid: String?): BatchDraft = copy(patientUid = uid)

    /** A person confirms the allocation. Ignored while any image has no module or there are no images. */
    fun setVerified(checked: Boolean): BatchDraft = copy(verified = checked && images.isNotEmpty() && unassigned.isEmpty())

    fun clear(): BatchDraft = BatchDraft()

    val unassigned: List<DraftImage> get() = images.filter { it.assigned == null }

    /** How many images the user moved away from the router's suggestion. */
    val changedCount: Int get() = images.count { it.changedByUser }

    /** The images of each module, modules in [packOrder] first (then any other), skipping modules with no image. */
    fun groups(packOrder: List<String>): List<Pair<String, List<DraftImage>>> {
        val byPack = images.filter { it.assigned != null }.groupBy { it.assigned!! }
        return (packOrder.filter { it in byPack } + byPack.keys.filter { it !in packOrder }).map { it to byPack.getValue(it) }
    }

    /** Submittable: something to analyse, every image in a module, a patient, and a person has verified the allocation. */
    val canSubmit: Boolean get() = images.isNotEmpty() && unassigned.isEmpty() && patientUid != null && verified
}
