package com.deepsight.engine.triage

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.AggregationSpec
import com.deepsight.engine.contract.Condition
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.ImageScoreAggregation
import com.deepsight.engine.contract.Metric
import com.deepsight.engine.contract.Op
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.TriageLevel
import com.deepsight.engine.contract.TriageRule
import com.deepsight.engine.contract.UncertaintyMethod
import com.deepsight.engine.contract.UncertaintySpec
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.pipeline.FieldPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #19 on the phone: TriageEvaluator fed real FieldResults from the real ONNX pipeline (smoke pack, random weights),
 * not hand-built fixtures. Rules are built from the observed counts so the test holds whichever label the model picks.
 */
@RunWith(AndroidJUnit4::class)
class TriageEvaluatorDeviceTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val pack = PackLoader.fromAssets(assets, packsRoot = "").load("smoke")
    private val base = pack.manifest

    private fun analyze(manifest: PackManifest, vararg seeds: Pair<String, Int>): List<FieldResult> =
        FieldPipeline(LoadedPack(manifest, pack.modelBytes), OnnxModel.Accelerator.CPU).use { p ->
            seeds.map { (id, seed) -> p.analyzeField("case-1", id, bitmap(seed)) }
        }

    private fun total(fields: List<FieldResult>) = fields.sumOf { it.counts.values.sum() }

    private fun rule(id: String, level: TriageLevel, op: Op, value: Double) =
        TriageRule(id, level, listOf(Condition(Metric.COUNT, base.output.labels, op, value)))

    private fun withRules(
        m: PackManifest,
        vararg rules: TriageRule,
        minFields: Int = 1,
        agg: ImageScoreAggregation = ImageScoreAggregation.MAX,
    ) = m.copy(
        aggregation = AggregationSpec(agg, minFields),
        triage = m.triage.copy(rules = rules.toList()),
    )

    @Test
    fun aggregatesRealFieldsAndFirstMatchingRuleWins() {
        val fields = analyze(base, "f1" to 7919, "f2" to 104729)
        fields.forEach { Log.i("DeepSightS2", "triage field ${it.fieldId} counts=${it.counts} score=${it.imageScore}") }
        val scores = fields.map { it.imageScore!! }
        assertNotEquals("fields must differ for aggregation to mean anything", scores[0], scores[1], 1e-9)

        val manifest = withRules(
            base,
            rule("never", TriageLevel.NORMAL_SCREEN, Op.GTE, 99.0),
            rule("any_cell", TriageLevel.ABNORMAL_FLAG, Op.GTE, 1.0),
            rule("also_matches", TriageLevel.NORMAL_SCREEN, Op.GTE, 0.0),
        )
        val case = TriageEvaluator.evaluate("case-1", fields, manifest)
        Log.i("DeepSightS2", "triage case=$case")

        assertEquals(2, case.fieldsPassed)
        assertEquals(total(fields), case.counts.values.sum())
        base.output.labels.forEach { l -> assertEquals(fields.sumOf { it.counts[l] ?: 0 }, case.counts.getValue(l)) }
        assertEquals(scores.max(), case.imageScore!!, 1e-12)
        assertEquals("any_cell", case.triage.ruleId)
        assertEquals(TriageLevel.ABNORMAL_FLAG, case.triage.level)
        assertTrue(case.triage.provisional)

        val any = rule("r", TriageLevel.ABNORMAL_FLAG, Op.GTE, 1.0)
        val mean = TriageEvaluator.evaluate("case-1", fields, withRules(base, any, agg = ImageScoreAggregation.MEAN))
        assertEquals(scores.average(), mean.imageScore!!, 1e-12)
        assertNull(TriageEvaluator.evaluate("case-1", fields, withRules(base, any, agg = ImageScoreAggregation.NONE)).imageScore)
    }

    @Test
    fun noRuleMatchedAndInsufficientFieldsAreNeedsExpert() {
        val fields = analyze(base, "f1" to 7919, "f2" to 104729)
        val noMatch = TriageEvaluator.evaluate("case-1", fields, withRules(base, rule("never", TriageLevel.ABNORMAL_FLAG, Op.GTE, 99.0)))
        assertEquals(Contracts.RULE_NO_MATCH, noMatch.triage.ruleId)
        assertEquals(TriageLevel.NEEDS_EXPERT, noMatch.triage.level)

        // Insufficient fields outranks a matching rule.
        val short = TriageEvaluator.evaluate("case-1", fields, withRules(base, rule("any", TriageLevel.ABNORMAL_FLAG, Op.GTE, 0.0), minFields = 3))
        assertEquals(Contracts.RULE_INSUFFICIENT_FIELDS, short.triage.ruleId)
        assertEquals(TriageLevel.NEEDS_EXPERT, short.triage.level)
    }

    @Test
    fun qualityRejectedFieldIsExcludedFromCounts() {
        val good = analyze(base, "f1" to 7919)
        // min_blur this high rejects the image, from the real QualityGate on the phone.
        val strict = base.copy(quality = base.quality.copy(minBlur = 1e12))
        val rejected = analyze(strict, "f2" to 104729).single()
        assertFalse(rejected.quality.pass)
        assertTrue(QualityReason.BLUR in rejected.quality.reasons)

        val manifest = withRules(base, rule("any", TriageLevel.ABNORMAL_FLAG, Op.GTE, 1.0))
        val case = TriageEvaluator.evaluate("case-1", good + rejected, manifest)
        assertEquals(listOf("f1", "f2"), case.fieldIds)
        assertEquals(1, case.fieldsPassed)
        assertEquals(total(good), case.counts.values.sum())
        assertEquals(good.single().imageScore!!, case.imageScore!!, 1e-12)

        // Rejected field alone: nothing passed, so min_fields=1 is not met.
        val onlyRejected = TriageEvaluator.evaluate("case-1", listOf(rejected), manifest)
        assertEquals(Contracts.RULE_INSUFFICIENT_FIELDS, onlyRejected.triage.ruleId)
        assertEquals(0, onlyRejected.fieldsPassed)
        assertNull(onlyRejected.imageScore)
    }

    @Test
    fun routerVerdictsOverrideRulesAndUncertaintyBlocksNormal() {
        val fields = analyze(base, "f1" to 7919)
        val manifest = withRules(base, rule("any", TriageLevel.ABNORMAL_FLAG, Op.GTE, 0.0))
        fun withVerdict(v: RouterVerdict) = fields.map { it.copy(router = RouterResult(v, 0.1)) }

        assertEquals(Contracts.RULE_ROUTER_MISMATCH, TriageEvaluator.evaluate("case-1", withVerdict(RouterVerdict.MISMATCH), manifest).triage.ruleId)
        assertEquals(Contracts.RULE_ROUTER_REJECT, TriageEvaluator.evaluate("case-1", withVerdict(RouterVerdict.REJECT), manifest).triage.ruleId)

        // A real field decoded with a score band covering every score is flagged uncertain by the decoder, so a
        // NORMAL_SCREEN rule must become needs_expert; an ABNORMAL_FLAG rule is unaffected.
        val uncertain = base.copy(uncertainty = UncertaintySpec(UncertaintyMethod.SCORE_BAND, band = listOf(0.0, 1.0), maxFraction = 0.0))
        val uField = analyze(uncertain, "f1" to 7919)
        assertTrue(uField.single().uncertainty!!.flag)
        val normal = TriageEvaluator.evaluate("case-1", uField, withRules(base, rule("normal", TriageLevel.NORMAL_SCREEN, Op.GTE, 0.0)))
        assertTrue(normal.uncertainty.flag)
        assertEquals(Contracts.RULE_UNCERTAIN, normal.triage.ruleId)
        assertEquals(TriageLevel.NEEDS_EXPERT, normal.triage.level)
        val abnormal = TriageEvaluator.evaluate("case-1", uField, withRules(base, rule("abn", TriageLevel.ABNORMAL_FLAG, Op.GTE, 0.0)))
        assertEquals("abn", abnormal.triage.ruleId)
    }

    // Same construction as FieldPipelineDeviceTest; the seed gives different pixels, hence different model output.
    private fun bitmap(seed: Int): Bitmap {
        val side = 64
        val channel = side * side
        fun byte(i: Int) = (i.toLong() * seed % 256).toInt()
        val pixels = IntArray(channel) { p ->
            (0xff shl 24) or (byte(p) shl 16) or (byte(channel + p) shl 8) or byte(2 * channel + p)
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
}
