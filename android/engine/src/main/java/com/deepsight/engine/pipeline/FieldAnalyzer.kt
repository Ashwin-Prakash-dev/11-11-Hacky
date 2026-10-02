package com.deepsight.engine.pipeline

import com.deepsight.engine.contract.Decoder
import com.deepsight.engine.contract.FieldResult
import com.deepsight.engine.contract.InputSource
import com.deepsight.engine.contract.TensorLayout
import com.deepsight.engine.decode.ClassifierDecoder
import com.deepsight.engine.decode.CropScores
import com.deepsight.engine.decode.FieldResultFactory
import com.deepsight.engine.onnx.OnnxModel
import com.deepsight.engine.pack.LoadedPack
import com.deepsight.engine.preprocess.TensorPreprocessor
import com.deepsight.engine.quality.PixelImage
import com.deepsight.engine.quality.QualityGate
import com.deepsight.engine.segmentation.RbcDetector
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.ln
import kotlin.math.max

/**
 * One analyzed field. [field] is null when NLM's segmentation asks for a retake. [width] and [height] are the
 * field's pixel size after EXIF rotation; the preview is the field at 1/4 size as ARGB pixels.
 */
class FieldAnalysis(
    val field: FieldResult?,
    val width: Int,
    val height: Int,
    val previewWidth: Int,
    val previewHeight: Int,
    val previewArgb: IntArray,
)

/**
 * Runs one microscope field through a `cells` pack: quality gate, NLM red-cell detector, 44x44 bicubic crops (as in
 * NLM's Cells.putInPixels), manifest preprocessing, ONNX and the classifier decoder. Not thread-safe.
 */
class FieldAnalyzer(
    pack: LoadedPack,
    accelerator: OnnxModel.Accelerator = OnnxModel.Accelerator.XNNPACK,
) : AutoCloseable {
    private val manifest = pack.manifest
    private val model = OnnxModel(pack.modelBytes, accelerator)

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV native library failed to load" }
        require(manifest.preprocess.source == InputSource.CELLS) { "FieldAnalyzer runs cells packs, not ${manifest.id}" }
        // The pack's ONNX graph ends in Softmax, so scores reach the decoder as log-probabilities (see analyze).
        require(manifest.output.decoder == Decoder.SOFTMAX) { "FieldAnalyzer supports softmax packs only" }
    }

    fun analyze(caseId: String, fieldId: String, imagePath: String): FieldAnalysis {
        val start = System.nanoTime()
        val rgb = Mat().also { rgb ->
            val bgr = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_COLOR)   // applies EXIF orientation, like NLM's app
            require(!bgr.empty()) { "Could not decode $imagePath" }
            Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB)
            bgr.release()
        }
        val width = rgb.cols()
        val height = rgb.rows()
        val preview = Mat().also { Imgproc.resize(rgb, it, Size(width / 4.0, height / 4.0), 0.0, 0.0, Imgproc.INTER_AREA) }
        fun analysis(field: FieldResult?) =
            FieldAnalysis(field, width, height, preview.cols(), preview.rows(), argb(preview)).also { rgb.release() }

        val quality = QualityGate.evaluate(pixelImage(rgb), manifest.quality)
        val qualityMs = msSince(start)
        if (!quality.pass) {
            return analysis(FieldResultFactory.rejected(caseId, fieldId, manifest, quality, mapOf("quality" to qualityMs, "total" to msSince(start))))
        }

        val segmentationStart = System.nanoTime()
        val detection = RbcDetector.detect(rgb) ?: return analysis(null)
        val segmentationMs = msSince(segmentationStart)

        val preprocessStart = System.nanoTime()
        val shape = requireNotNull(manifest.input.shape)
        val (inputHeight, inputWidth) = when (manifest.input.layout) {
            TensorLayout.NCHW -> shape[2] to shape[3]
            else -> shape[1] to shape[2]
        }
        val perCell = inputWidth * inputHeight * 3
        val batch = FloatArray(detection.cells.size * perCell)
        detection.cells.forEachIndexed { i, cell ->
            val resized = Mat().also { Imgproc.resize(cell.chip, it, Size(inputWidth.toDouble(), inputHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC) }
            TensorPreprocessor.preprocess(pixelImage(resized), manifest.input, manifest.preprocess).copyInto(batch, i * perCell)
            cell.chip.release()
        }
        val preprocessMs = msSince(preprocessStart)

        val packStart = System.nanoTime()
        val n = detection.cells.size.toLong()
        val batchShape = when (manifest.input.layout) {
            TensorLayout.NCHW -> longArrayOf(n, 3, inputHeight.toLong(), inputWidth.toLong())
            else -> longArrayOf(n, inputHeight.toLong(), inputWidth.toLong(), 3)
        }
        val probabilities = if (n == 0L) FloatArray(0) else model.run(batch, batchShape)
        val packMs = msSince(packStart)

        val labels = manifest.output.labels.size
        val predictions = detection.cells.mapIndexed { i, cell ->
            val b = cell.bounds
            CropScores(
                // softmax(log p) == p, so the decoder reports the model's own probabilities
                values = FloatArray(labels) { k -> ln(max(probabilities[i * labels + k], MIN_PROBABILITY)) },
                bbox = listOf(b.x.toDouble() / width, b.y.toDouble() / height, b.width.toDouble() / width, b.height.toDouble() / height),
            )
        }
        val decoded = ClassifierDecoder.decode(predictions, manifest.output, manifest.uncertainty)
        return analysis(
            FieldResult(
                caseId = caseId,
                fieldId = fieldId,
                packId = manifest.id,
                packVersion = manifest.version,
                quality = quality,
                router = null,                                   // no router model before G2
                objects = decoded.objects,
                counts = decoded.counts,
                imageScore = decoded.imageScore,
                uncertainty = decoded.uncertainty,
                timingMs = linkedMapOf(
                    "quality" to qualityMs,
                    "segmentation" to segmentationMs,
                    "preprocess" to preprocessMs,
                    "pack" to packMs,
                    "total" to msSince(start),
                ),
            ),
        )
    }

    override fun close() = model.close()

    private companion object {
        const val MIN_PROBABILITY = 1e-30f

        fun msSince(start: Long) = (System.nanoTime() - start) / 1_000_000

        /** RGB uint8 Mat -> ARGB ints, one row at a time to keep the Java heap small for full-size fields. */
        fun argb(rgb: Mat): IntArray {
            val width = rgb.cols()
            val out = IntArray(width * rgb.rows())
            val row = ByteArray(width * 3)
            for (y in 0 until rgb.rows()) {
                rgb.get(y, 0, row)
                for (x in 0 until width) {
                    out[y * width + x] = (0xff shl 24) or ((row[3 * x].toInt() and 0xff) shl 16) or
                        ((row[3 * x + 1].toInt() and 0xff) shl 8) or (row[3 * x + 2].toInt() and 0xff)
                }
            }
            return out
        }

        fun pixelImage(rgb: Mat) = PixelImage(rgb.cols(), rgb.rows(), argb(rgb))
    }
}
