package com.deepsight.engine.triage

import com.deepsight.engine.contract.AggregationSpec
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.Condition
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.ImageScoreAggregation
import com.deepsight.engine.contract.Metric
import com.deepsight.engine.contract.Op
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.contract.TriageRule
import com.deepsight.engine.contract.UncertaintyResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageEvaluatorTest {
    private val contractsDir = checkNotNull(System.getProperty("contractsDir")) { "run through Gradle" }
    private val outDir = File(checkNotNull(System.getProperty("contractOutDir")) { "run through Gradle" }).apply { mkdirs() }
    private val base: PackManifest =
        Contracts.parseManifest(File(contractsDir, "examples/manifest.malaria_thin.json").readText())

    private fun manifest(
        rules: List<TriageRule>,
        minFields: Int = 1,
        aggregation: ImageScoreAggregation = ImageScoreAggregation.MAX,
        provisional: Boolean = true,
    ) = base.copy(
        aggregation = AggregationSpec(imageScore = aggregation, minFields = minFields),
        triage = base.triage.copy(provisional = provisional, rules = rules),
    )

    private fun count(label: String, op: Op, value: Double) = Condition(Metric.COUNT, listOf(label), op, value)
    private fun score(op: Op, value: Double) = Condition(Metric.IMAGE_SCORE, emptyList(), op, value)
    private fun rule(id: String, level: TriageLevel, vararg all: Condition) = TriageRule(id, level, all.toList())

    private fun field(
        id: String,
        counts: Map<String, Int> = emptyMap(),
        imageScore: Double? = null,
        verdict: RouterVerdict = RouterVerdict.MATCH,
        uncertain: Boolean = false,
    ) = FieldResult(
        caseId = "case-1", fieldId = id, packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = true, blurScore = 100.0, exposureScore = 0.0),
        router = RouterResult(verdict, 0.9, predicted = if (verdict == RouterVerdict.MISMATCH) "other_test" else null),
        counts = counts, imageScore = imageScore,
        uncertainty = UncertaintyResult(flag = uncertain, reason = if (uncertain) "score_band" else null),
    )

    private fun rejected(id: String) = FieldResult(
        caseId = "case-1", fieldId = id, packId = "malaria_thin", packVersion = "0.1.0",
        quality = QualityResult(pass = false, blurScore = 1.0, exposureScore = 0.0, reasons = listOf(QualityReason.BLUR)),
    )

    private fun evaluate(m: PackManifest, vararg fields: FieldResult): CaseResult =
        TriageEvaluator.evaluate("case-1", fields.toList(), m)

    private val flagIfParasite = rule("parasite_seen", TriageLevel.ABNORMAL_FLAG, count("parasitized", Op.GTE, 1.0))
    private val clearIfNone = rule("clear", TriageLevel.NORMAL_SCREEN, count("parasitized", Op.EQ, 0.0))

    // Step 1
    @Test
    fun tooFewPassedFieldsNeedsExpert() {
        val result = evaluate(manifest(listOf(flagIfParasite), minFields = 2), field("f1", mapOf("parasitized" to 5)), rejected("f2"))
        assertEquals(TriageLevel.NEEDS_EXPERT, result.triage.level)
        assertEquals(Contracts.RULE_INSUFFICIENT_FIELDS, result.triage.ruleId)
        assertEquals(1, result.fieldsPassed)
        assertEquals(listOf("f1", "f2"), result.fieldIds)
    }

    @Test
    fun noFieldsAtAllNeedsExpert() {
        val result = evaluate(manifest(listOf(flagIfParasite)))
        assertEquals(Contracts.RULE_INSUFFICIENT_FIELDS, result.triage.ruleId)
        assertEquals(0, result.fieldsPassed)
        assertNull(result.imageScore)
    }

    // Step 2
    @Test
    fun routerRejectBeatsMismatchAndRules() {
        val result = evaluate(
            manifest(listOf(flagIfParasite)),
            field("f1", mapOf("parasitized" to 5), verdict = RouterVerdict.MISMATCH),
            field("f2", verdict = RouterVerdict.REJECT),
        )
        assertEquals(Contracts.RULE_ROUTER_REJECT, result.triage.ruleId)
        assertEquals(TriageLevel.NEEDS_EXPERT, result.triage.level)
    }

    @Test
    fun routerMismatchNeedsExpert() {
        val result = evaluate(manifest(listOf(flagIfParasite)), field("f1", mapOf("parasitized" to 5), verdict = RouterVerdict.MISMATCH))
        assertEquals(Contracts.RULE_ROUTER_MISMATCH, result.triage.ruleId)
    }

    @Test
    fun rejectedFieldRouterVerdictIsIgnored() {
        // A rejected field has router == null and is never triaged; it must not trip step 2.
        val result = evaluate(manifest(listOf(flagIfParasite)), field("f1", mapOf("parasitized" to 1)), rejected("f2"))
        assertEquals("parasite_seen", result.triage.ruleId)
    }

    @Test
    fun insufficientFieldsComesBeforeRouterReject() {
        val result = evaluate(manifest(listOf(flagIfParasite), minFields = 2), field("f1", verdict = RouterVerdict.REJECT))
        assertEquals(Contracts.RULE_INSUFFICIENT_FIELDS, result.triage.ruleId)
    }

    // Step 3
    @Test
    fun countsAreSummedAcrossPassedFieldsOnly() {
        val result = evaluate(
            manifest(listOf(clearIfNone)),
            field("f1", mapOf("uninfected" to 10, "parasitized" to 0)),
            field("f2", mapOf("uninfected" to 5)),
            rejected("f3"),
        )
        assertEquals(mapOf("uninfected" to 15, "parasitized" to 0), result.counts)
        assertEquals(2, result.fieldsPassed)
    }

    @Test
    fun imageScoreMaxAndMeanIgnoreNullScores() {
        val fields = arrayOf(field("f1", imageScore = 0.2), field("f2", imageScore = 0.6), field("f3", imageScore = null))
        assertEquals(0.6, evaluate(manifest(listOf(clearIfNone)), *fields).imageScore!!, 1e-9)
        assertEquals(0.4, evaluate(manifest(listOf(clearIfNone), aggregation = ImageScoreAggregation.MEAN), *fields).imageScore!!, 1e-9)
    }

    @Test
    fun imageScoreIsNullWhenAggregationIsNoneOrNoFieldHasOne() {
        val scored = field("f1", imageScore = 0.9)
        assertNull(evaluate(manifest(listOf(clearIfNone), aggregation = ImageScoreAggregation.NONE), scored).imageScore)
        assertNull(evaluate(manifest(listOf(clearIfNone)), field("f1")).imageScore)
    }

    @Test
    fun anyUncertainFieldMakesCaseUncertain() {
        val result = evaluate(manifest(listOf(clearIfNone)), field("f1"), field("f2", uncertain = true))
        assertTrue(result.uncertainty.flag)
        assertFalse(evaluate(manifest(listOf(clearIfNone)), field("f1"), field("f2")).uncertainty.flag)
    }

    // Step 4: every op
    private fun levelFor(op: Op, value: Double, parasitized: Int): TriageLevel {
        val m = manifest(listOf(rule("hit", TriageLevel.ABNORMAL_FLAG, count("parasitized", op, value))))
        val result = evaluate(m, field("f1", mapOf("parasitized" to parasitized)))
        return if (result.triage.ruleId == "hit") result.triage.level else TriageLevel.NEEDS_EXPERT
    }

    @Test
    fun everyOpAtBoundary() {
        // (op, value=5): below, equal, above
        val expected = mapOf(
            Op.GTE to listOf(false, true, true),
            Op.GT to listOf(false, false, true),
            Op.LTE to listOf(true, true, false),
            Op.LT to listOf(true, false, false),
            Op.EQ to listOf(false, true, false),
        )
        for ((op, hits) in expected) {
            val actual = listOf(4, 5, 6).map { levelFor(op, 5.0, it) == TriageLevel.ABNORMAL_FLAG }
            assertEquals(op.name, hits, actual)
        }
    }

    @Test
    fun missingLabelCountsAsZero() {
        val eq0 = rule("none_seen", TriageLevel.NORMAL_SCREEN, count("parasitized", Op.EQ, 0.0))
        assertEquals("none_seen", evaluate(manifest(listOf(eq0)), field("f1", mapOf("uninfected" to 3))).triage.ruleId)
    }

    @Test
    fun countConditionSumsItsLabels() {
        val both = rule("any_cell", TriageLevel.ABNORMAL_FLAG, Condition(Metric.COUNT, listOf("uninfected", "parasitized"), Op.GTE, 10.0))
        val result = evaluate(manifest(listOf(both)), field("f1", mapOf("uninfected" to 6, "parasitized" to 4)))
        assertEquals("any_cell", result.triage.ruleId)
    }

    @Test
    fun imageScoreConditionUsesCaseScoreAndIsFalseWhenNull() {
        val high = rule("high", TriageLevel.ABNORMAL_FLAG, score(Op.GTE, 0.5))
        val m = manifest(listOf(high))
        assertEquals("high", evaluate(m, field("f1", imageScore = 0.7)).triage.ruleId)
        assertEquals(Contracts.RULE_NO_MATCH, evaluate(m, field("f1", imageScore = null)).triage.ruleId)
    }

    @Test
    fun allConditionsMustHold() {
        val both = rule("both", TriageLevel.ABNORMAL_FLAG, count("parasitized", Op.GTE, 1.0), score(Op.GTE, 0.9))
        val m = manifest(listOf(both))
        assertEquals("both", evaluate(m, field("f1", mapOf("parasitized" to 1), imageScore = 0.95)).triage.ruleId)
        assertEquals(Contracts.RULE_NO_MATCH, evaluate(m, field("f1", mapOf("parasitized" to 1), imageScore = 0.5)).triage.ruleId)
    }

    @Test
    fun firstMatchingRuleWins() {
        val m = manifest(listOf(flagIfParasite, clearIfNone, rule("late", TriageLevel.NEEDS_EXPERT, count("parasitized", Op.GTE, 0.0))))
        assertEquals("parasite_seen", evaluate(m, field("f1", mapOf("parasitized" to 2))).triage.ruleId)
    }

    // Step 5
    @Test
    fun noRuleMatchedNeedsExpert() {
        val result = evaluate(manifest(listOf(flagIfParasite)), field("f1", mapOf("parasitized" to 0)))
        assertEquals(TriageLevel.NEEDS_EXPERT, result.triage.level)
        assertEquals(Contracts.RULE_NO_MATCH, result.triage.ruleId)
    }

    // Step 6
    @Test
    fun uncertainNormalScreenBecomesNeedsExpert() {
        val result = evaluate(manifest(listOf(clearIfNone)), field("f1", mapOf("parasitized" to 0), uncertain = true))
        assertEquals(TriageLevel.NEEDS_EXPERT, result.triage.level)
        assertEquals(Contracts.RULE_UNCERTAIN, result.triage.ruleId)
        assertTrue(result.uncertainty.flag)
    }

    @Test
    fun uncertainAbnormalFlagIsKept() {
        val result = evaluate(manifest(listOf(flagIfParasite)), field("f1", mapOf("parasitized" to 3), uncertain = true))
        assertEquals(TriageLevel.ABNORMAL_FLAG, result.triage.level)
        assertEquals("parasite_seen", result.triage.ruleId)
    }

    @Test
    fun provisionalFollowsManifest() {
        assertTrue(evaluate(manifest(listOf(clearIfNone), provisional = true), field("f1")).triage.provisional)
        assertFalse(evaluate(manifest(listOf(clearIfNone), provisional = false), field("f1")).triage.provisional)
        assertFalse(evaluate(manifest(listOf(clearIfNone), provisional = false), field("f1", verdict = RouterVerdict.REJECT)).triage.provisional)
    }

    // Hand-computed case. Passed: f1 (600 uninfected, 0 parasitized, 0.2), f2 (500, 0, 0.4); f3 rejected.
    // Sum: uninfected 1100, parasitized 0. Mean score (0.2 + 0.4) / 2 = 0.3. Rules: parasite_seen no, enough_cells_clear yes.
    @Test
    fun handComputedMultiFieldCase() {
        val result = evaluate(
            manifest(base.triage.rules, aggregation = ImageScoreAggregation.MEAN),
            field("f1", mapOf("uninfected" to 600, "parasitized" to 0), imageScore = 0.2),
            field("f2", mapOf("uninfected" to 500, "parasitized" to 0), imageScore = 0.4),
            rejected("f3"),
        )
        assertEquals("case-1", result.caseId)
        assertEquals("malaria_thin", result.packId)
        assertEquals("0.1.0", result.packVersion)
        assertEquals(listOf("f1", "f2", "f3"), result.fieldIds)
        assertEquals(2, result.fieldsPassed)
        assertEquals(mapOf("uninfected" to 1100, "parasitized" to 0), result.counts)
        assertEquals(0.3, result.imageScore!!, 1e-9)
        assertFalse(result.uncertainty.flag)
        assertEquals(TriageLevel.NORMAL_SCREEN, result.triage.level)
        assertEquals("enough_cells_clear", result.triage.ruleId)
        assertTrue(result.triage.provisional)
        File(outDir, "case_result.triage_evaluator.json").writeText(Contracts.encode(result))
    }

    @Test
    fun earlyExitResultAlsoWrittenForValidator() {
        val result = evaluate(manifest(listOf(flagIfParasite), minFields = 2), field("f1", mapOf("parasitized" to 1)))
        File(outDir, "case_result.triage_insufficient.json").writeText(Contracts.encode(result))
    }
}
