package com.deepsight.engine.pipeline

import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.quality.PixelImage
import com.deepsight.engine.router.RouterGuard
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
    private val sharp = PixelImage(8, 8, IntArray(64) { index ->
        val gray = if ((index % 8 + index / 8) % 2 == 0) 64 else 192
        (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
    })

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

    @Test
    fun routerMismatchStopsBeforeCellsAndPackModel() {
        val router = RouterGuard { _, _ -> RouterResult(RouterVerdict.MISMATCH, 0.9, "breast_breakhis") }
        val result = FieldPipeline(
            LoadedPack(manifest, ByteArray(0)),
            cellFinder = { _, _ -> error("cell finder must not run after router mismatch") },
            routerGuard = router,
        ).analyze("case-1", "field-1", sharp)

        assertTrue(result.quality.pass)
        assertEquals(RouterVerdict.MISMATCH, result.router?.verdict)
        assertEquals("breast_breakhis", result.router?.predicted)
        assertTrue(result.objects.isEmpty())
        assertTrue(result.counts.isEmpty())
        assertNull(result.imageScore)
        assertEquals(false, result.uncertainty?.flag)
        assertEquals(setOf("quality", "router", "total"), result.timingMs.keys)
        File(outDir, "field_result.pipeline_mismatch.json").writeText(Contracts.encode(result))
    }

    @Test
    fun routerRejectStopsBeforeCellsAndPackModel() {
        val router = RouterGuard { _, _ -> RouterResult(RouterVerdict.REJECT, 0.8) }
        val result = FieldPipeline(
            LoadedPack(manifest, ByteArray(0)),
            cellFinder = { _, _ -> error("cell finder must not run after router reject") },
            routerGuard = router,
        ).analyze("case-1", "field-1", sharp)

        assertTrue(result.quality.pass)
        assertEquals(RouterVerdict.REJECT, result.router?.verdict)
        assertTrue(result.objects.isEmpty())
        assertTrue(result.counts.isEmpty())
        assertNull(result.imageScore)
        assertEquals(false, result.uncertainty?.flag)
        assertEquals(setOf("quality", "router", "total"), result.timingMs.keys)
        File(outDir, "field_result.pipeline_router_reject.json").writeText(Contracts.encode(result))
    }
}
