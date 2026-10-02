package com.deepsight.engine.pipeline

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.deepsight.engine.contract.CellType
import com.deepsight.engine.contract.Contracts
import com.deepsight.engine.contract.InputSource
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.QualityReason
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.pack.PackLoader
import com.deepsight.engine.segmentation.CellCropper
import com.deepsight.engine.segmentation.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The `source: cells` path and a quality reject through a real Bitmap, on the phone. The CellFinder here is a test double. */
@RunWith(AndroidJUnit4::class)
class FieldPipelineCellsDeviceTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val pack = PackLoader.fromAssets(assets, packsRoot = "").load("smoke")
    private val cellsManifest: PackManifest = pack.manifest.copy(
        preprocess = pack.manifest.preprocess.copy(source = InputSource.CELLS, cellType = CellType.RBC),
    )
    private val boxes = listOf(PixelRect(0, 0, 40, 40), PixelRect(50, 10, 64, 48), PixelRect(20, 70, 90, 50))

    private fun pipeline(manifest: PackManifest, finder: CellFinder?) =
        FieldPipeline(LoadedPack(manifest, pack.modelBytes), OnnxModel.Accelerator.CPU, finder)

    @Test
    fun cellsPackBatchesCropsAndKeepsEachCropsBoxAndScore() {
        var seenCellType: CellType? = null
        val field = pipeline(cellsManifest) { image, type -> seenCellType = type; CellCropper.crop(image, boxes) }.use {
            it.analyzeField("case-1", "f1", bitmap(128, 128, 7919))
        }
        assertEquals(CellType.RBC, seenCellType)
        assertEquals(boxes.size, field.objects.size)
        assertEquals(boxes.size, field.counts.values.sum())
        assertEquals(setOf("quality", "router", "cells", "preprocess", "pack", "total"), field.timingMs.keys)
        // bbox is the contract's normalized [x, y, w, h].
        boxes.forEachIndexed { i, b ->
            assertEquals(listOf(b.x / 128.0, b.y / 128.0, b.width / 128.0, b.height / 128.0), field.objects[i].bbox)
        }

        // Batching must not mix crops up: each crop alone gives the same score as inside the batch.
        boxes.forEachIndexed { i, b ->
            val alone = pipeline(cellsManifest) { image, _ -> CellCropper.crop(image, listOf(b)) }.use { it.analyzeField("case-1", "f1", bitmap(128, 128, 7919)) }
            assertEquals("crop $i", alone.objects.single().score, field.objects[i].score, 1e-6)
            assertEquals("crop $i", alone.objects.single().label, field.objects[i].label)
        }
        assertNotEquals("crops must differ", field.objects[0].score, field.objects[1].score, 1e-9)
        assertEquals(field.objects.maxOf { if (it.label == "positive") it.score else 1 - it.score }, field.imageScore!!, 1e-6)
        Contracts.encode(field) // serialises as a contract field_result
    }

    @Test
    fun cellsPackWithNoCellsFoundGivesEmptyResult() {
        val field = pipeline(cellsManifest) { _, _ -> emptyList() }.use { it.analyzeField("case-1", "f1", bitmap(128, 128, 7919)) }
        assertTrue(field.quality.pass)
        assertTrue(field.objects.isEmpty())
        assertEquals(0, field.counts.values.sum())
        assertNull(field.imageScore)
    }

    @Test
    fun cellsPackWithoutCellFinderFailsClearly() {
        pipeline(cellsManifest, null).use {
            val error = assertThrows(IllegalStateException::class.java) { it.analyzeField("case-1", "f1", bitmap(128, 128, 7919)) }
            assertTrue(error.message!!.contains("CellFinder"))
        }
    }

    @Test
    fun qualityRejectThroughRealBitmapStopsEarly() {
        // A flat grey bitmap has zero Laplacian variance, so a positive min_blur rejects it in the real gate.
        val strict = pack.manifest.copy(quality = pack.manifest.quality.copy(minBlur = 1.0))
        val flat = Bitmap.createBitmap(IntArray(64 * 64) { 0xff808080.toInt() }, 64, 64, Bitmap.Config.ARGB_8888)
        // The cells finder must never be reached after a reject.
        val strictCells = strict.copy(preprocess = cellsManifest.preprocess)
        val field = pipeline(strictCells) { _, _ -> error("finder called after quality reject") }.use { it.analyzeField("case-1", "f1", flat) }

        assertFalse(field.quality.pass)
        assertTrue(QualityReason.BLUR in field.quality.reasons)
        assertNull(field.router)
        assertTrue(field.objects.isEmpty())
        assertTrue(field.counts.isEmpty())
        assertNull(field.imageScore)
        assertNull(field.uncertainty)
        assertEquals(setOf("quality", "total"), field.timingMs.keys)
        Contracts.encode(field)
    }

    private fun bitmap(w: Int, h: Int, seed: Int): Bitmap {
        fun byte(i: Int) = (i.toLong() * seed % 256).toInt()
        val n = w * h
        val pixels = IntArray(n) { p -> (0xff shl 24) or (byte(p) shl 16) or (byte(n + p) shl 8) or byte(2 * n + p) }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
