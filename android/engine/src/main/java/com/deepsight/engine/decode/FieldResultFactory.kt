package com.deepsight.engine.decode

import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.UncertaintyResult

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

    /** A router mismatch/reject is a valid field observation, but pack inference must not run. */
    fun routerBlocked(
        caseId: String,
        fieldId: String,
        manifest: PackManifest,
        quality: QualityResult,
        router: RouterResult,
        timingMs: Map<String, Long> = emptyMap(),
    ): FieldResult {
        require(quality.pass) { "Router only runs after quality passes" }
        require(router.verdict != RouterVerdict.MATCH) { "Matched fields must continue to pack inference" }
        return FieldResult(
            caseId = caseId,
            fieldId = fieldId,
            packId = manifest.id,
            packVersion = manifest.version,
            quality = quality,
            router = router,
            uncertainty = UncertaintyResult(flag = false),
            timingMs = timingMs,
        )
    }
}
