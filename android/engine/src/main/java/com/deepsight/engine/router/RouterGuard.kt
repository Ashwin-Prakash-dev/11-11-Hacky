package com.deepsight.engine.router

import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.quality.PixelImage

/** Verifies that a field belongs to the pack selected by the user; it never selects the pack. */
fun interface RouterGuard {
    fun evaluate(field: PixelImage, selectedPackId: String): RouterResult
}

/** Temporary fallback used until #22 supplies a trained, golden-tested router model. */
object AlwaysMatchRouterGuard : RouterGuard {
    override fun evaluate(field: PixelImage, selectedPackId: String) =
        RouterResult(verdict = RouterVerdict.MATCH, score = 1.0)
}

/**
 * Converts a router classifier's probabilities into the frozen [RouterResult] contract.
 * [scoreField] is the later ONNX integration seam; labels come from #22's `labels.json`.
 */
class ScoreRouterGuard(
    private val labels: List<String>,
    private val scoreField: (PixelImage) -> FloatArray,
) : RouterGuard {
    init {
        require(labels.isNotEmpty()) { "Router labels must not be empty" }
        require(labels.distinct().size == labels.size) { "Router labels must be unique" }
        require(REJECT_LABEL in labels) { "Router labels must include '$REJECT_LABEL'" }
    }

    override fun evaluate(field: PixelImage, selectedPackId: String): RouterResult {
        require(selectedPackId != REJECT_LABEL && selectedPackId in labels) {
            "Selected pack '$selectedPackId' is not a router label"
        }
        val scores = scoreField(field)
        require(scores.size == labels.size) {
            "Router returned ${scores.size} scores for ${labels.size} labels"
        }
        scores.forEachIndexed { index, score ->
            require(score.isFinite() && score in 0.0f..1.0f) {
                "Router score for '${labels[index]}' must be a finite probability"
            }
        }

        var best = 0
        for (index in 1 until scores.size) {
            if (scores[index] > scores[best]) best = index
        }
        val predicted = labels[best]
        val verdict = when (predicted) {
            REJECT_LABEL -> RouterVerdict.REJECT
            selectedPackId -> RouterVerdict.MATCH
            else -> RouterVerdict.MISMATCH
        }
        return RouterResult(
            verdict = verdict,
            score = scores[best].toDouble(),
            predicted = predicted.takeIf { verdict == RouterVerdict.MISMATCH },
        )
    }

    private companion object {
        const val REJECT_LABEL = "reject"
    }
}
