package com.deepsight.engine.decode

import com.deepsight.engine.contract.Decoder
import com.deepsight.engine.contract.DetectedObject
import com.deepsight.engine.contract.OutputSpec
import com.deepsight.engine.contract.UncertaintyMethod
import com.deepsight.engine.contract.UncertaintyResult
import com.deepsight.engine.contract.UncertaintySpec
import kotlin.math.exp

data class CropScores(
    val values: FloatArray,
    val bbox: List<Double>? = null,
)

data class DecodedField(
    val objects: List<DetectedObject>,
    val counts: Map<String, Int>,
    val imageScore: Double?,
    val uncertainty: UncertaintyResult,
)

/** Decodes classifier outputs without pack-specific labels or thresholds in code. */
object ClassifierDecoder {
    fun decode(
        predictions: List<CropScores>,
        output: OutputSpec,
        uncertainty: UncertaintySpec,
    ): DecodedField {
        require(output.decoder == Decoder.SOFTMAX || output.decoder == Decoder.SIGMOID) {
            "ClassifierDecoder supports softmax and sigmoid, not ${output.decoder}"
        }
        require(output.labels.isNotEmpty()) { "output.labels must not be empty" }
        require(output.labels.toSet().size == output.labels.size) { "output.labels must be unique" }
        val probabilities = predictions.map { prediction ->
            require(prediction.values.size == output.labels.size) {
                "Model output has ${prediction.values.size} scores for ${output.labels.size} labels"
            }
            require(prediction.values.all { it.isFinite() }) { "Model scores must be finite" }
            validateBbox(prediction.bbox)
            when (output.decoder) {
                Decoder.SOFTMAX -> softmax(prediction.values)
                Decoder.SIGMOID -> prediction.values.map { sigmoid(it.toDouble()) }.toDoubleArray()
                else -> error("checked above")
            }
        }

        val objects: List<DetectedObject> = when (output.decoder) {
            Decoder.SOFTMAX -> probabilities.mapIndexed { index, scores ->
                val labelIndex = scores.indices.maxByOrNull { scores[it] } ?: error("labels checked above")
                DetectedObject(
                    label = output.labels[labelIndex],
                    score = scores[labelIndex],
                    bbox = predictions[index].bbox,
                )
            }
            Decoder.SIGMOID -> {
                val threshold = output.scoreThreshold ?: DEFAULT_SIGMOID_THRESHOLD
                require(threshold in 0.0..1.0) { "output.score_threshold must be between 0 and 1" }
                buildList<DetectedObject> {
                    probabilities.forEachIndexed { predictionIndex, scores ->
                        scores.forEachIndexed { labelIndex, score ->
                            if (score >= threshold) {
                                add(
                                    DetectedObject(
                                        label = output.labels[labelIndex],
                                        score = score,
                                        bbox = predictions[predictionIndex].bbox,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            else -> error("checked above")
        }

        val counts = linkedMapOf<String, Int>().apply {
            output.labels.forEach { put(it, 0) }
            objects.forEach { detected -> put(detected.label, getValue(detected.label) + 1) }
        }
        val scoreIndex = output.imageScoreLabel?.let { label ->
            output.labels.indexOf(label).takeIf { it >= 0 }
                ?: throw IllegalArgumentException("output.image_score_label '$label' is not in output.labels")
        }
        val imageScore = scoreIndex?.let { index -> probabilities.maxOfOrNull { it[index] } }
        return DecodedField(
            objects = objects,
            counts = counts,
            imageScore = imageScore,
            uncertainty = uncertainty(imageScore, objects, uncertainty),
        )
    }

    private fun softmax(logits: FloatArray): DoubleArray {
        val max = logits.maxOrNull()?.toDouble() ?: throw IllegalArgumentException("Model output is empty")
        val exponentials = DoubleArray(logits.size) { exp(logits[it] - max) }
        val sum = exponentials.sum()
        return DoubleArray(logits.size) { exponentials[it] / sum }
    }

    private fun sigmoid(value: Double): Double = if (value >= 0.0) {
        1.0 / (1.0 + exp(-value))
    } else {
        val exponential = exp(value)
        exponential / (1.0 + exponential)
    }

    private fun uncertainty(
        imageScore: Double?,
        objects: List<DetectedObject>,
        spec: UncertaintySpec,
    ): UncertaintyResult {
        if (spec.method == UncertaintyMethod.NONE) return UncertaintyResult(false)

        val band = requireNotNull(spec.band) { "score_band requires uncertainty.band" }
        require(band.size == 2 && band[0] < band[1]) { "uncertainty.band must be [low, high] with low < high" }
        val maxFraction = requireNotNull(spec.maxFraction) { "score_band requires uncertainty.max_fraction" }
        require(maxFraction in 0.0..1.0) { "uncertainty.max_fraction must be between 0 and 1" }
        fun inBand(score: Double) = score >= band[0] && score <= band[1]

        if (imageScore != null && inBand(imageScore)) {
            return UncertaintyResult(true, IMAGE_SCORE_REASON)
        }
        val fraction = if (objects.isEmpty()) 0.0 else objects.count { inBand(it.score) }.toDouble() / objects.size
        return if (fraction > maxFraction) {
            UncertaintyResult(true, OBJECT_FRACTION_REASON)
        } else {
            UncertaintyResult(false)
        }
    }

    private fun validateBbox(bbox: List<Double>?) {
        if (bbox == null) return
        require(bbox.size == 4) { "bbox must contain [x, y, w, h]" }
        require(bbox.all { it.isFinite() && it in 0.0..1.0 }) { "bbox values must be finite and between 0 and 1" }
        require(bbox[0] + bbox[2] <= 1.0 + BBOX_EPSILON && bbox[1] + bbox[3] <= 1.0 + BBOX_EPSILON) {
            "bbox must stay inside the field"
        }
    }

    private const val DEFAULT_SIGMOID_THRESHOLD = 0.5
    private const val BBOX_EPSILON = 1e-9
    private const val IMAGE_SCORE_REASON = "image_score_in_band"
    private const val OBJECT_FRACTION_REASON = "object_score_fraction_in_band"
}
