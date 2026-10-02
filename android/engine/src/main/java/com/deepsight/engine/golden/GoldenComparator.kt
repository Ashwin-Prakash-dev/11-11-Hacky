package com.deepsight.engine.golden

import com.deepsight.engine.contract.FieldResult
import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `golden/tolerance.json`. Defaults are the plan's values (docs/architecture.md). Counts are always exact. */
@Serializable
data class GoldenTolerance(
    val score: Double = 0.02,
    @SerialName("box_px") val boxPx: Double = 2.0,
)

/**
 * Compares an on-device [FieldResult] with a golden one produced by the Python reference.
 * Ignores case_id, field_id, timing_ms and the numeric blur/exposure scores (their scale is pack-specific; the
 * pass/reasons they produce are compared). Returns one line per difference; empty means the case passes.
 */
object GoldenComparator {
    private const val EPS = 1e-9

    fun compare(
        expected: FieldResult,
        actual: FieldResult,
        tol: GoldenTolerance,
        imageWidth: Int,
        imageHeight: Int,
    ): List<String> {
        val diffs = mutableListOf<String>()
        fun <T> exact(name: String, want: T, got: T) {
            if (want != got) diffs += "$name: expected $want, got $got"
        }
        fun score(name: String, want: Double?, got: Double?) {
            if (want == null || got == null) exact(name, want, got)
            else if (abs(want - got) > tol.score + EPS) diffs += "$name: expected $want, got $got (tolerance ${tol.score})"
        }

        exact("pack_id", expected.packId, actual.packId)
        exact("quality.pass", expected.quality.pass, actual.quality.pass)
        exact("quality.reasons", expected.quality.reasons, actual.quality.reasons)
        exact("router.verdict", expected.router?.verdict, actual.router?.verdict)
        exact("counts", expected.counts, actual.counts)
        score("image_score", expected.imageScore, actual.imageScore)
        exact("uncertainty.flag", expected.uncertainty?.flag, actual.uncertainty?.flag)

        exact("objects.size", expected.objects.size, actual.objects.size)
        expected.objects.zip(actual.objects).forEachIndexed { i, (want, got) ->
            exact("objects[$i].label", want.label, got.label)
            score("objects[$i].score", want.score, got.score)
            val wantBox = want.bbox
            val gotBox = got.bbox
            if (wantBox == null || gotBox == null || wantBox.size != 4 || gotBox.size != 4) {
                exact("objects[$i].bbox", wantBox, gotBox)
            } else {
                // bbox is [x, y, w, h] normalized to the field; tolerance is in pixels of that image.
                val scale = doubleArrayOf(imageWidth.toDouble(), imageHeight.toDouble(), imageWidth.toDouble(), imageHeight.toDouble())
                val worstPx = wantBox.indices.maxOf { abs(wantBox[it] - gotBox[it]) * scale[it] }
                if (worstPx > tol.boxPx + EPS) diffs += "objects[$i].bbox: expected $wantBox, got $gotBox (off by %.2f px, tolerance ${tol.boxPx})".format(worstPx)
            }
        }
        return diffs
    }
}
