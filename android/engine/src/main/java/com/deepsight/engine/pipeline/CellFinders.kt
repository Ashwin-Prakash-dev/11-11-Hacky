package com.deepsight.engine.pipeline

import android.util.Log
import com.deepsight.engine.contract.CellType
import com.deepsight.engine.contract.InputSource
import com.deepsight.engine.contract.PackManifest
import com.deepsight.engine.contract.TensorLayout
import com.deepsight.engine.quality.PixelImage
import com.deepsight.engine.segmentation.CellCrop
import com.deepsight.engine.segmentation.RbcDetector
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** The engine's cell finders. Callers pass `CellFinders.forPack(pack.manifest)` to [FieldPipeline]. */
object CellFinders {
    /** The finder for a `cells` pack's cell type, or null when the pack takes whole fields or no finder exists yet (WBC). */
    fun forPack(manifest: PackManifest): CellFinder? {
        if (manifest.preprocess.source != InputSource.CELLS || manifest.preprocess.cellType != CellType.RBC) return null
        val shape = requireNotNull(manifest.input.shape) { "Pack '${manifest.id}' has no input.shape" }
        return when (manifest.input.layout) {
            TensorLayout.NCHW -> RbcCellFinder(cropWidth = shape[3], cropHeight = shape[2])
            else -> RbcCellFinder(cropWidth = shape[2], cropHeight = shape[1])
        }
    }
}

/**
 * Red cells by NLM Malaria Screener's detector ([RbcDetector]), cut out as NLM does: background set to black and resized
 * to the model input with bicubic interpolation (Cells.putInPixels), so FieldPipeline's own resize does nothing.
 * Returns no cells when NLM would ask for a retake (logged), which triage turns into NEEDS_EXPERT.
 */
class RbcCellFinder(private val cropWidth: Int, private val cropHeight: Int) : CellFinder {
    init {
        check(OpenCVLoader.initLocal()) { "OpenCV native library failed to load" }
    }

    override fun find(field: PixelImage, cellType: CellType?): List<CellCrop> {
        val rgb = rgbMat(field)
        val detection = RbcDetector.detect(rgb).also { rgb.release() }
        if (detection == null) {
            Log.w(TAG, "NLM segmentation asked for a retake: the green channel never gets near black")
            return emptyList()
        }
        val size = Size(cropWidth.toDouble(), cropHeight.toDouble())
        return detection.cells.map { cell ->
            val resized = Mat().also { Imgproc.resize(cell.chip, it, size, 0.0, 0.0, Imgproc.INTER_CUBIC) }
            cell.chip.release()
            CellCrop(cell.bounds, cell.bounds.normalized(field), pixelImage(resized))
        }
    }

    private companion object {
        const val TAG = "DeepSightField"

        /** ARGB pixels -> RGB uint8 Mat, one row at a time to keep the Java heap small for full-size fields. */
        fun rgbMat(image: PixelImage): Mat {
            val mat = Mat(image.height, image.width, CvType.CV_8UC3)
            val row = ByteArray(image.width * 3)
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val p = image.argb[y * image.width + x]
                    row[3 * x] = (p ushr 16).toByte()
                    row[3 * x + 1] = (p ushr 8).toByte()
                    row[3 * x + 2] = p.toByte()
                }
                mat.put(y, 0, row)
            }
            return mat
        }

        fun pixelImage(rgb: Mat): PixelImage {
            val bytes = ByteArray(rgb.cols() * rgb.rows() * 3).also { rgb.get(0, 0, it) }
            return PixelImage(rgb.cols(), rgb.rows(), IntArray(rgb.cols() * rgb.rows()) { i ->
                (0xff shl 24) or ((bytes[3 * i].toInt() and 0xff) shl 16) or ((bytes[3 * i + 1].toInt() and 0xff) shl 8) or
                    (bytes[3 * i + 2].toInt() and 0xff)
            })
        }
    }
}
