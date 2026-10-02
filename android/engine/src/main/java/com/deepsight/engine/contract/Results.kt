package com.deepsight.engine.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One captured field. Schema: contracts/result.schema.json#/$defs/field_result.
 * When quality fails, the field is rejected: router, imageScore and uncertainty stay null, objects and counts empty.
 */
@Serializable
data class FieldResult(
    @SerialName("case_id") val caseId: String,
    @SerialName("field_id") val fieldId: String,
    @SerialName("pack_id") val packId: String,
    @SerialName("pack_version") val packVersion: String,
    val quality: QualityResult,
    val router: RouterResult? = null,
    val objects: List<DetectedObject> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
    @SerialName("image_score") val imageScore: Double? = null,
    val uncertainty: UncertaintyResult? = null,
    @SerialName("timing_ms") val timingMs: Map<String, Long> = emptyMap(),
)

@Serializable
data class QualityResult(
    val pass: Boolean,
    @SerialName("blur_score") val blurScore: Double,
    @SerialName("exposure_score") val exposureScore: Double,
    val reasons: List<QualityReason> = emptyList(),
)

@Serializable
data class RouterResult(
    val verdict: RouterVerdict,
    val score: Double,
    val predicted: String? = null,
)

/** [bbox] is [x, y, w, h], normalized 0-1 to the field image, origin top-left. */
@Serializable
data class DetectedObject(
    val label: String,
    val score: Double,
    val bbox: List<Double>? = null,
)

@Serializable
data class UncertaintyResult(
    val flag: Boolean,
    val reason: String? = null,
)

/** One case after aggregation and triage. Schema: contracts/result.schema.json#/$defs/case_result. */
@Serializable
data class CaseResult(
    @SerialName("case_id") val caseId: String,
    @SerialName("pack_id") val packId: String,
    @SerialName("pack_version") val packVersion: String,
    @SerialName("field_ids") val fieldIds: List<String>,
    @SerialName("fields_passed") val fieldsPassed: Int,
    val counts: Map<String, Int>,
    @SerialName("image_score") val imageScore: Double? = null,
    val uncertainty: UncertaintyResult,
    val triage: TriageResult,
)

/** [ruleId] is a manifest rule id or one of the engine ids in [Contracts]. */
@Serializable
data class TriageResult(
    val level: TriageLevel,
    @SerialName("rule_id") val ruleId: String,
    val provisional: Boolean,
)

@Serializable
enum class QualityReason { @SerialName("blur") BLUR, @SerialName("underexposed") UNDEREXPOSED, @SerialName("overexposed") OVEREXPOSED }

@Serializable
enum class RouterVerdict { @SerialName("match") MATCH, @SerialName("mismatch") MISMATCH, @SerialName("reject") REJECT }
