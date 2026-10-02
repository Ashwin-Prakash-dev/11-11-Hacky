package com.deepsight.engine.pipeline

import android.graphics.Bitmap
import com.deepsight.engine.contract.CaseResult
import com.deepsight.engine.contract.CellType
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.InputSource
import com.deepsight.engine.contract.RouterResult
import com.deepsight.engine.contract.RouterVerdict
import com.deepsight.engine.decode.ClassifierDecoder
import com.deepsight.engine.decode.CropScores
import com.deepsight.engine.decode.FieldResultFactory
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.preprocess.TensorPreprocessor
import com.deepsight.engine.quality.PixelImage
import com.deepsight.engine.quality.QualityGate
import com.deepsight.engine.segmentation.CellCrop
import com.deepsight.engine.triage.TriageEvaluator

/**
 * Finds and cuts out the cells for packs with `preprocess.source = cells`. Returns crops rather than boxes because a
 * detector may need to mask or resize them (NLM's sets the background to black and resizes bicubic); a box-only
 * finder can return `CellCropper.crop(field, boxes)`. [CellFinders.forPack] picks the engine's finder for a pack.
 */
fun interface CellFinder {
    fun find(field: PixelImage, cellType: CellType?): List<CellCrop>
}

/**
 * The engine entry point the app calls: quality → router → cells → preprocess → model → decode.
 * One instance per pack; the ONNX session is created on first use and freed by [close]. Not thread-safe.
 */
class FieldPipeline(
    private val pack: LoadedPack,
    private val accelerator: OnnxModel.Accelerator = OnnxModel.Accelerator.XNNPACK,
    private val cellFinder: CellFinder? = null,
) : AutoCloseable {
    private val manifest = pack.manifest

    // Lazy so a quality reject (and JVM tests) never load the native runtime.
    private val model by lazy { OnnxModel(pack.modelBytes, accelerator) }
    private var modelCreated = false

    fun analyzeField(caseId: String, fieldId: String, bitmap: Bitmap): FieldResult {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return analyze(caseId, fieldId, PixelImage(bitmap.width, bitmap.height, pixels))
    }

    fun closeCase(caseId: String, fields: List<FieldResult>): CaseResult =
        TriageEvaluator.evaluate(caseId, fields, manifest)

    internal fun analyze(caseId: String, fieldId: String, image: PixelImage): FieldResult {
        val timing = linkedMapOf<String, Long>()
        val start = System.nanoTime()
        fun <T> timed(name: String, block: () -> T): T {
            val t = System.nanoTime()
            return block().also { timing[name] = (System.nanoTime() - t) / 1_000_000 }
        }

        val quality = timed("quality") { QualityGate.evaluate(image, manifest.quality) }
        if (!quality.pass) {
            timing["total"] = (System.nanoTime() - start) / 1_000_000
            return FieldResultFactory.rejected(caseId, fieldId, manifest, quality, timing)
        }

        // ponytail: router stub, always match until #23 lands the real router guard.
        val router = timed("router") { RouterResult(RouterVerdict.MATCH, 1.0) }

        val input = manifest.input
        val crops = when (manifest.preprocess.source) {
            InputSource.FIELD -> listOf(image to null)
            InputSource.CELLS -> {
                val finder = checkNotNull(cellFinder) { "Pack '${manifest.id}' uses cells but no CellFinder was provided" }
                timed("cells") { finder.find(image, manifest.preprocess.cellType) }.map { it.image to it.normalizedBbox }
            }
        }
        val tensors = timed("preprocess") {
            crops.map { (img, _) -> TensorPreprocessor.preprocess(img, input, manifest.preprocess) }
        }

        val labels = manifest.output.labels.size
        val scores = timed("pack") {
            if (tensors.isEmpty()) {
                FloatArray(0)
            } else {
                val batch = FloatArray(tensors.sumOf { it.size })
                var offset = 0
                tensors.forEach { it.copyInto(batch, offset); offset += it.size }
                modelCreated = true
                model.run(batch, longArrayOf(tensors.size.toLong()) + requireNotNull(input.shape).drop(1).map(Int::toLong))
            }
        }
        val decoded = ClassifierDecoder.decode(
            predictions = crops.mapIndexed { i, (_, bbox) -> CropScores(scores.copyOfRange(i * labels, (i + 1) * labels), bbox) },
            output = manifest.output,
            uncertainty = manifest.uncertainty,
        )

        timing["total"] = (System.nanoTime() - start) / 1_000_000
        return FieldResult(
            caseId = caseId,
            fieldId = fieldId,
            packId = manifest.id,
            packVersion = manifest.version,
            quality = quality,
            router = router,
            objects = decoded.objects,
            counts = decoded.counts,
            imageScore = decoded.imageScore,
            uncertainty = decoded.uncertainty,
            timingMs = timing,
        )
    }

    override fun close() {
        if (modelCreated) model.close()
    }
}
