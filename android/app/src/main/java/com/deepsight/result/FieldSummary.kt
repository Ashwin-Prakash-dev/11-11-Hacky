package com.deepsight.result

import com.deepsight.engine.contract.DetectedObject
import kotlin.math.roundToInt

/** Whole-field packs (breast) give one object with no box: say what the model predicted, since there is nothing to draw. */
internal fun wholeFieldPrediction(objects: List<DetectedObject>): String? {
    val only = objects.singleOrNull()?.takeIf { it.bbox == null } ?: return null
    return "Model prediction: ${displayClassLabel(only.label)} · model score ${(only.score * 100).roundToInt()}%"
}

/** Manifest labels are stable machine identifiers; result screens show them as readable words. */
internal fun displayClassLabel(label: String): String = label
    .trim()
    .split(Regex("[_\\s-]+"))
    .filter(String::isNotEmpty)
    .mapIndexed { index, word ->
        when {
            word.length == 1 -> word.uppercase()
            index == 0 -> word.lowercase().replaceFirstChar(Char::uppercase)
            else -> word.lowercase()
        }
    }
    .joinToString(" ")

/** Cell packs draw one box per object; the box legend only makes sense then. */
internal fun hasBoxes(objects: List<DetectedObject>): Boolean = objects.any { it.bbox != null }
