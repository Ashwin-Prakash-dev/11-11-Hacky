package com.deepsight.engine.pipeline

import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.quality.PixelImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldPipelineTest {
    private val contractsDir = checkNotNull(System.getProperty("contractsDir")) { "run through Gradle" }
    private val outDir = File(checkNotNull(System.getProperty("contractOutDir")) { "run through Gradle" }).apply { mkdirs() }
    private val manifest = Contracts.parseManifest(File(contractsDir, "examples/manifest.malaria_thin.json").readText())

    /** A flat grey field has zero Laplacian variance, so any positive min_blur rejects it. */
    private val flat = PixelImage(8, 8, IntArray(64) { 0xff808080.toInt() })

    @Test
    fun qualityRejectStopsBeforeRouterAndModel() {
        val strict = manifest.copy(quality = manifest.quality.copy(minBlur = 1.0))
        // Empty model bytes: the ONNX session is lazy, so touching it here would throw.
        val result = FieldPipeline(LoadedPack(strict, ByteArray(0))).analyze("case-1", "field-1", flat)

        assertFalse(result.quality.pass)
        assertTrue(QualityReason.BLUR in result.quality.reasons)
        assertNull(result.router)
        assertTrue(result.objects.isEmpty())
        assertTrue(result.counts.isEmpty())
        assertNull(result.imageScore)
        assertNull(result.uncertainty)
        assertEquals(setOf("quality", "total"), result.timingMs.keys)
        File(outDir, "field_result.pipeline_rejected.json").writeText(Contracts.encode(result))
    }
}
