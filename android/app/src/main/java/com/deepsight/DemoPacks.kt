package com.deepsight

/**
 * docs/architecture.md: "a pack that hasn't passed its golden tests on a physical phone doesn't appear in the demo".
 * Packs that load but aren't on this list are shown as not yet validated, and can't be picked.
 * malaria_thin: MalariaPackGoldenTest and AnnotatedFieldsDeviceTest on the edge 50 fusion (docs/STATUS.md).
 */
object DemoPacks {
    private val validated = setOf("malaria_thin")

    fun isReady(packId: String) = packId in validated
}
