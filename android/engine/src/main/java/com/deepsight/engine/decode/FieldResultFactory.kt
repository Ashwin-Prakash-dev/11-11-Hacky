package com.deepsight.engine.decode

import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.QualityResult

object FieldResultFactory {
    /** Contract step 2: quality rejection clears all router and pack outputs. */
    fun rejected(
        caseId: String,
        fieldId: String,
        manifest: PackManifest,
        quality: QualityResult,
        timingMs: Map<String, Long> = emptyMap(),
    ): FieldResult {
        require(!quality.pass) { "Rejected field requires quality.pass false" }
        return FieldResult(
            caseId = caseId,
            fieldId = fieldId,
            packId = manifest.id,
            packVersion = manifest.version,
            quality = quality,
            timingMs = timingMs,
        )
    }
}
