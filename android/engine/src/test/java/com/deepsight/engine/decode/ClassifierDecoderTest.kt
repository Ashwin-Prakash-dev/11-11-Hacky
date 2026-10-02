package com.deepsight.engine.decode

import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.Decoder
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.OutputSpec
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.QualityResult
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.contract.UncertaintyMethod
import com.deepsight.engine.contract.UncertaintySpec
import java.io.File
import kotlin.math.ln
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierDecoderTest {
    @Test
    fun `softmax emits one object per crop counts labels and takes max image score`() {
        val output = output(Decoder.SOFTMAX, imageScoreLabel = "positive")
        val predictions = listOf(
            CropScores(floatArrayOf(0f, ln(3.0).toFloat()), bbox = listOf(0.0, 0.0, 0.5, 0.5)),
            CropScores(floatArrayOf(ln(4.0).toFloat(), 0f), bbox = listOf(0.5, 0.5, 0.5, 0.5)),
        )

        val decoded = ClassifierDecoder.decode(predictions, output, noUncertainty())

        assertEquals(listOf("positive", "negative"), decoded.objects.map { it.label })
        assertEquals(0.75, decoded.objects[0].score, 1e-6)
        assertEquals(0.8, decoded.objects[1].score, 1e-6)
        assertEquals(mapOf("negative" to 1, "positive" to 1), decoded.counts)
        assertEquals(0.75, decoded.imageScore!!, 1e-6)
        assertFalse(decoded.uncertainty.flag)
    }

    @Test
    fun `sigmoid emits every label at or above threshold`() {
        val output = output(Decoder.SIGMOID, imageScoreLabel = "negative", scoreThreshold = 0.6)
        val predictions = listOf(
            CropScores(floatArrayOf(0f, ln(3.0).toFloat())),
            CropScores(floatArrayOf(ln(4.0).toFloat(), -ln(4.0).toFloat())),
        )

        val decoded = ClassifierDecoder.decode(predictions, output, noUncertainty())

        assertEquals(listOf("positive", "negative"), decoded.objects.map { it.label })
        assertEquals(mapOf("negative" to 1, "positive" to 1), decoded.counts)
        assertEquals(0.8, decoded.imageScore!!, 1e-6)
    }

    @Test
    fun `image score on a score band boundary is uncertain`() {
        val decoded = ClassifierDecoder.decode(
            predictions = listOf(CropScores(floatArrayOf(10f, 0f))),
            output = output(Decoder.SIGMOID, imageScoreLabel = "positive", scoreThreshold = 0.9),
            uncertainty = scoreBand(low = 0.5, high = 0.65, maxFraction = 1.0),
        )

        assertTrue(decoded.uncertainty.flag)
        assertEquals("image_score_in_band", decoded.uncertainty.reason)
    }

    @Test
    fun `object fraction must be strictly greater than maximum`() {
        val predictions = listOf(
            CropScores(floatArrayOf(ln(1.5).toFloat(), 0f)),
            CropScores(floatArrayOf(ln(9.0).toFloat(), 0f)),
        )
        val atBoundary = ClassifierDecoder.decode(
            predictions,
            output(Decoder.SOFTMAX),
            scoreBand(low = 0.55, high = 0.65, maxFraction = 0.5),
        )
        val belowBoundary = ClassifierDecoder.decode(
            predictions,
            output(Decoder.SOFTMAX),
            scoreBand(low = 0.55, high = 0.65, maxFraction = 0.49),
        )

        assertFalse(atBoundary.uncertainty.flag)
        assertTrue(belowBoundary.uncertainty.flag)
        assertEquals("object_score_fraction_in_band", belowBoundary.uncertainty.reason)
    }

    @Test
    fun `empty predictions keep zero counts and null image score`() {
        val decoded = ClassifierDecoder.decode(
            predictions = emptyList(),
            output = output(Decoder.SOFTMAX, imageScoreLabel = "positive"),
            uncertainty = scoreBand(low = 0.5, high = 0.65, maxFraction = 0.05),
        )

        assertEquals(mapOf("negative" to 0, "positive" to 0), decoded.counts)
        assertTrue(decoded.objects.isEmpty())
        assertNull(decoded.imageScore)
        assertFalse(decoded.uncertainty.flag)
    }

    @Test
    fun `rejected field clears every downstream value`() {
        val manifest = Contracts.parseManifest(example("manifest.malaria_thin.json"))
        val result = FieldResultFactory.rejected(
            caseId = "case-1",
            fieldId = "field-1",
            manifest = manifest,
            quality = QualityResult(false, 0.0, 0.0, listOf(QualityReason.BLUR)),
            timingMs = mapOf("quality" to 3L, "total" to 3L),
        )

        assertNull(result.router)
        assertTrue(result.objects.isEmpty())
        assertTrue(result.counts.isEmpty())
        assertNull(result.imageScore)
        assertNull(result.uncertainty)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects output width that differs from labels`() {
        ClassifierDecoder.decode(
            listOf(CropScores(floatArrayOf(1f))),
            output(Decoder.SOFTMAX),
            noUncertainty(),
        )
    }

    @Test
    fun `decoded field result JSON passes the frozen contract`() {
        val manifest = Contracts.parseManifest(example("manifest.malaria_thin.json"))
        val decoded = ClassifierDecoder.decode(
            predictions = listOf(CropScores(floatArrayOf(0f, 2f), bbox = listOf(0.1, 0.2, 0.3, 0.4))),
            output = manifest.output,
            uncertainty = manifest.uncertainty,
        )
        val result = FieldResult(
            caseId = "case-decoder",
            fieldId = "field-decoder",
            packId = manifest.id,
            packVersion = manifest.version,
            quality = QualityResult(true, 10.0, 0.0),
            router = RouterResult(RouterVerdict.MATCH, 1.0, manifest.id),
            objects = decoded.objects,
            counts = decoded.counts,
            imageScore = decoded.imageScore,
            uncertainty = decoded.uncertainty,
        )

        val encoded = Contracts.encode(result)
        assertEquals(result, Contracts.parseFieldResult(encoded))
        File(contractOutDir, "field_result.decoder.json").writeText(encoded)
    }

    private fun output(
        decoder: Decoder,
        imageScoreLabel: String? = null,
        scoreThreshold: Double? = null,
    ) = OutputSpec(
        decoder = decoder,
        labels = listOf("negative", "positive"),
        scoreThreshold = scoreThreshold,
        imageScoreLabel = imageScoreLabel,
    )

    private fun noUncertainty() = UncertaintySpec(UncertaintyMethod.NONE)

    private fun scoreBand(low: Double, high: Double, maxFraction: Double) = UncertaintySpec(
        method = UncertaintyMethod.SCORE_BAND,
        band = listOf(low, high),
        maxFraction = maxFraction,
    )

    private fun example(name: String) = File(contractsDir, "examples/$name").readText()

    private val contractsDir = File(checkNotNull(System.getProperty("contractsDir")) { "run through Gradle" })
    private val contractOutDir = File(checkNotNull(System.getProperty("contractOutDir")) { "run through Gradle" }).apply { mkdirs() }
}
