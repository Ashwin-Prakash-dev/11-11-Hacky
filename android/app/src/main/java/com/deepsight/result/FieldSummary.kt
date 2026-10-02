package com.deepsight.result

import com.deepsight.engine.contract.DetectedObject
import kotlin.math.roundToInt

/** Whole-field packs (breast) give one object with no box: say what the model predicted, since there is nothing to draw. */
internal fun wholeFieldPrediction(objects: List<DetectedObject>): String? {
    val only = objects.singleOrNull()?.takeIf { it.bbox == null } ?: return null
    return "Model prediction: ${only.label} (${(only.score * 100).roundToInt()}%)"
}

/** Cell packs draw one box per object; the box legend only makes sense then. */
internal fun hasBoxes(objects: List<DetectedObject>): Boolean = objects.any { it.bbox != null }
