package com.deepsight.engine.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A pack's manifest.json. Schema: contracts/manifest.schema.json. Semantics: contracts/README.md. */
@Serializable
data class PackManifest(
    @SerialName("contract_version") val contractVersion: String,
    val id: String,
    val version: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("task_type") val taskType: TaskType,
    val runtime: PackRuntime,
    val compute: Compute,
    val model: ModelSpec,
    val input: InputSpec,
    val preprocess: PreprocessSpec,
    val output: OutputSpec,
    val quality: QualitySpec,
    val aggregation: AggregationSpec,
    val triage: TriageSpec,
    val uncertainty: UncertaintySpec,
    val provenance: Provenance,
) {
    /** Cross-field checks the JSON Schema can't express (mirrors contracts/validate.py). Empty means usable. */
    fun problems(): List<String> = buildList {
        if (!contractVersion.startsWith("1.")) add("contract_version $contractVersion is not 1.x")
        if (!ID.matches(id)) add("id '$id' must be lower_snake_case")
        if ((runtime == PackRuntime.HUB) != (compute == Compute.HUB)) add("runtime and compute must both be hub, or neither")
        if (runtime == PackRuntime.HUB && model.endpoint == null) add("hub packs need model.endpoint")
        if (runtime != PackRuntime.HUB && model.file == null) add("phone packs need model.file")

        val labels = output.labels.toSet()
        val scoreLabel = output.imageScoreLabel
        if (scoreLabel != null && scoreLabel !in labels) add("output.image_score_label '$scoreLabel' is not in output.labels")
        if (scoreLabel == null && aggregation.imageScore != ImageScoreAggregation.NONE) {
            add("aggregation.image_score must be none when output.image_score_label is not set")
        }

        if (triage.rules.isEmpty()) add("triage.rules is empty")
        if (triage.rules.map { it.id }.toSet().size != triage.rules.size) add("triage.rules has duplicate ids")
        for (rule in triage.rules) {
            if (!ID.matches(rule.id)) add("rule id '${rule.id}' must be lower_snake_case")
            for (condition in rule.all) {
                condition.labels.filter { it !in labels }.forEach { add("rule ${rule.id}: unknown label '$it'") }
                when (condition.metric) {
                    Metric.COUNT -> if (condition.labels.isEmpty()) add("rule ${rule.id}: count needs labels")
                    Metric.IMAGE_SCORE -> {
                        if (condition.labels.isNotEmpty()) add("rule ${rule.id}: image_score takes no labels")
                        if (scoreLabel == null) add("rule ${rule.id}: image_score needs output.image_score_label")
                    }
                }
            }
        }

        if (uncertainty.method == UncertaintyMethod.SCORE_BAND) {
            val band = uncertainty.band
            if (band == null || band.size != 2 || band[0] >= band[1]) add("uncertainty.band must be [low, high] with low < high")
            if (uncertainty.maxFraction == null) add("uncertainty.max_fraction is required for score_band")
        }
    }
}

private val ID = Regex("[a-z][a-z0-9_]*")

@Serializable
data class ModelSpec(
    val file: String? = null,
    val sha256: String? = null,
    val endpoint: String? = null,
)

@Serializable
data class InputSpec(
    val tensor: String? = null,
    val shape: List<Int>? = null,
    val layout: TensorLayout? = null,
    val dtype: TensorDtype? = null,
    val color: ColorOrder,
    @SerialName("max_side") val maxSide: Int? = null,
)

@Serializable
data class PreprocessSpec(
    val source: InputSource,
    @SerialName("cell_type") val cellType: CellType? = null,
    val resize: Resize = Resize.STRETCH,
    val scale: Double = 1.0,
    val mean: List<Double> = listOf(0.0, 0.0, 0.0),
    val std: List<Double> = listOf(1.0, 1.0, 1.0),
    @SerialName("stain_normalization") val stainNormalization: StainNormalization = StainNormalization.NONE,
)

@Serializable
data class OutputSpec(
    val decoder: Decoder,
    val labels: List<String>,
    @SerialName("score_threshold") val scoreThreshold: Double? = null,
    @SerialName("iou_threshold") val iouThreshold: Double? = null,
    @SerialName("image_score_label") val imageScoreLabel: String? = null,
)

@Serializable
data class QualitySpec(
    @SerialName("min_blur") val minBlur: Double,
    @SerialName("max_clipped_fraction") val maxClippedFraction: Double,
)

@Serializable
data class AggregationSpec(
    @SerialName("image_score") val imageScore: ImageScoreAggregation,
    @SerialName("min_fields") val minFields: Int,
)

@Serializable
data class TriageSpec(
    val provisional: Boolean,
    val source: String,
    val rules: List<TriageRule>,
)

/** Matches when every condition in [all] holds. Rules are tried in order; the first match wins. */
@Serializable
data class TriageRule(
    val id: String,
    val level: TriageLevel,
    val all: List<Condition>,
)

/** count: summed case counts of [labels]. image_score: the case image score; [labels] must be empty. */
@Serializable
data class Condition(
    val metric: Metric,
    val labels: List<String> = emptyList(),
    val op: Op,
    val value: Double,
)

@Serializable
data class UncertaintySpec(
    val method: UncertaintyMethod,
    val band: List<Double>? = null,
    @SerialName("max_fraction") val maxFraction: Double? = null,
)

@Serializable
data class Provenance(
    val source: String,
    val licence: String,
    @SerialName("training_data") val trainingData: List<String>,
    val notes: String? = null,
)

@Serializable
enum class TaskType { @SerialName("detector") DETECTOR, @SerialName("segmenter") SEGMENTER, @SerialName("classifier") CLASSIFIER, @SerialName("vlm") VLM }

@Serializable
enum class PackRuntime { @SerialName("onnx") ONNX, @SerialName("litert-lm") LITERT_LM, @SerialName("hub") HUB }

@Serializable
enum class Compute { @SerialName("phone") PHONE, @SerialName("hub") HUB }

@Serializable
enum class TensorLayout { NCHW, NHWC }

@Serializable
enum class TensorDtype { @SerialName("float32") FLOAT32, @SerialName("uint8") UINT8 }

@Serializable
enum class ColorOrder { RGB, BGR }

@Serializable
enum class InputSource { @SerialName("field") FIELD, @SerialName("cells") CELLS }

@Serializable
enum class CellType { @SerialName("rbc") RBC, @SerialName("wbc") WBC }

@Serializable
enum class Resize { @SerialName("stretch") STRETCH, @SerialName("letterbox") LETTERBOX, @SerialName("center_crop") CENTER_CROP, @SerialName("none") NONE }

@Serializable
enum class StainNormalization { @SerialName("none") NONE, @SerialName("reinhard") REINHARD, @SerialName("macenko") MACENKO }

@Serializable
enum class Decoder { @SerialName("softmax") SOFTMAX, @SerialName("sigmoid") SIGMOID, @SerialName("yolov8") YOLOV8, @SerialName("mask") MASK, @SerialName("json") JSON }

@Serializable
enum class ImageScoreAggregation { @SerialName("max") MAX, @SerialName("mean") MEAN, @SerialName("none") NONE }

@Serializable
enum class UncertaintyMethod { @SerialName("none") NONE, @SerialName("score_band") SCORE_BAND }

@Serializable
enum class Metric { @SerialName("count") COUNT, @SerialName("image_score") IMAGE_SCORE }

@Serializable
enum class Op { @SerialName(">=") GTE, @SerialName(">") GT, @SerialName("<=") LTE, @SerialName("<") LT, @SerialName("==") EQ }

@Serializable
enum class TriageLevel { ABNORMAL_FLAG, NORMAL_SCREEN, NEEDS_EXPERT }
