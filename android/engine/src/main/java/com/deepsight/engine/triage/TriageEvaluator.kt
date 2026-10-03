package com.deepsight.engine.triage

import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.Condition
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.ImageScoreAggregation
import com.deepsight.engine.contract.Metric
import com.deepsight.engine.contract.Op
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.contract.TriageResult
import com.deepsight.engine.contract.UncertaintyResult

/** Deterministic aggregation and triage: contracts/README.md "Per case", steps 1-6. No LLM, no I/O. */
object TriageEvaluator {
    /** The manifest's deterministic rule outcome for one slide, used to order batch review. */
    fun evaluateField(field: FieldResult, manifest: PackManifest): TriageLevel {
        if (!field.quality.pass) return TriageLevel.NEEDS_EXPERT
        when (field.router?.verdict) {
            RouterVerdict.REJECT, RouterVerdict.MISMATCH -> return TriageLevel.NEEDS_EXPERT
            else -> Unit
        }
        val rule = manifest.triage.rules.firstOrNull { candidate ->
            candidate.all.all { holds(it, field.counts, field.imageScore) }
        } ?: return TriageLevel.NEEDS_EXPERT
        if (rule.level == TriageLevel.NORMAL_SCREEN &&
            (field.uncertainty?.flag == true || manifest.aggregation.minFields > 1)
        ) {
            return TriageLevel.NEEDS_EXPERT
        }
        return rule.level
    }

    fun evaluate(caseId: String, fields: List<FieldResult>, manifest: PackManifest): CaseResult {
        val passed = fields.filter { it.quality.pass }
        val counts = passed.flatMap { it.counts.entries }
            .groupingBy { it.key }
            .fold(0) { sum, entry -> sum + entry.value }
        val imageScore = aggregateScore(passed, manifest.aggregation.imageScore)
        val uncertain = passed.firstOrNull { it.uncertainty?.flag == true }
        val uncertainty = UncertaintyResult(flag = uncertain != null, reason = uncertain?.uncertainty?.reason)

        fun decide(level: TriageLevel, ruleId: String) = CaseResult(
            caseId = caseId,
            packId = manifest.id,
            packVersion = manifest.version,
            fieldIds = fields.map { it.fieldId },
            fieldsPassed = passed.size,
            counts = counts,
            imageScore = imageScore,
            uncertainty = uncertainty,
            triage = TriageResult(level, ruleId, manifest.triage.provisional),
        )

        val verdicts = passed.mapNotNull { it.router?.verdict }
        val rule = manifest.triage.rules.firstOrNull { rule -> rule.all.all { holds(it, counts, imageScore) } }
        return when {
            passed.size < manifest.aggregation.minFields ->
                decide(TriageLevel.NEEDS_EXPERT, Contracts.RULE_INSUFFICIENT_FIELDS)
            RouterVerdict.REJECT in verdicts -> decide(TriageLevel.NEEDS_EXPERT, Contracts.RULE_ROUTER_REJECT)
            RouterVerdict.MISMATCH in verdicts -> decide(TriageLevel.NEEDS_EXPERT, Contracts.RULE_ROUTER_MISMATCH)
            rule == null -> decide(TriageLevel.NEEDS_EXPERT, Contracts.RULE_NO_MATCH)
            rule.level == TriageLevel.NORMAL_SCREEN && uncertainty.flag ->
                decide(TriageLevel.NEEDS_EXPERT, Contracts.RULE_UNCERTAIN)
            else -> decide(rule.level, rule.id)
        }
    }

    private fun aggregateScore(passed: List<FieldResult>, how: ImageScoreAggregation): Double? {
        val scores = passed.mapNotNull { it.imageScore }
        return when (how) {
            ImageScoreAggregation.MAX -> scores.maxOrNull()
            ImageScoreAggregation.MEAN -> scores.takeIf { it.isNotEmpty() }?.average()
            ImageScoreAggregation.NONE -> null
        }
    }

    private fun holds(condition: Condition, counts: Map<String, Int>, imageScore: Double?): Boolean {
        val actual = when (condition.metric) {
            Metric.COUNT -> condition.labels.sumOf { counts[it] ?: 0 }.toDouble()
            Metric.IMAGE_SCORE -> imageScore ?: return false
        }
        return when (condition.op) {
            Op.GTE -> actual >= condition.value
            Op.GT -> actual > condition.value
            Op.LTE -> actual <= condition.value
            Op.LT -> actual < condition.value
            Op.EQ -> actual == condition.value
        }
    }
}
