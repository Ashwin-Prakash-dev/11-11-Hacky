package com.deepsight

/**
 * docs/architecture.md: "a pack that hasn't passed its golden tests on a physical phone doesn't appear in the demo".
 * Packs that load but aren't on this list are shown as not yet validated, and can't be picked.
 * malaria_thin: MalariaPackGoldenTest and AnnotatedFieldsDeviceTest on the edge 50 fusion; breast_breakhis: its 6
 * PackGoldenTest cases on the edge 50 fusion (docs/STATUS.md). "Validated" means the pipeline matches the reference, not clinical accuracy.
 * leukaemia_wbc: enabled by team decision (2026-10-03) on LeukaemiaModelsDeviceTest and LeukaemiaFieldDeviceTest (edge 50
 * fusion, moto g32); it has no PackGoldenTest case yet.
 */
object DemoPacks {
    private val validated = setOf("malaria_thin", "breast_breakhis", "leukaemia_wbc")

    fun isReady(packId: String) = packId in validated
}
