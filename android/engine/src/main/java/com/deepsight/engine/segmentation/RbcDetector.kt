// SPDX-License-Identifier: GPL-3.0-only
// Port of NLM Malaria Screener (commit c485a21) MarkerBasedWatershed.java, SegmentWatershed.java and
// Cells.runCells, via ml/reference/nlm_segmentation.py. Original: Copyright 2020 The Malaria Screener Authors,
// developed under contract funded by the National Library of Medicine, GPL v3.0.
// Modified 2026-10-02 by the DeepSight team: translated to Kotlin on OpenCV 4. See LICENSING.md.
package com.deepsight.engine.segmentation

import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One cell cut from the full-resolution field: an RGB uint8 [chip] with the background set to 0. */
class DetectedCell(val bounds: PixelRect, val centerRow: Int, val centerCol: Int, val chip: Mat)

/** Segmentation-size masks ([watershed] 0/255, [wbc] 0/1) and the cells cut from the full-resolution field. */
class RbcDetection(val watershed: Mat, val wbc: Mat, val cells: List<DetectedCell>)

/**
 * NLM Malaria Screener's thin-smear red-cell detector, ported from ml/reference/nlm_segmentation.py (which matches
 * NLM's Java bit for bit; its docstring lists the quirks kept on purpose).
 *
 * Differences from NLM's OpenCV 3.4.2 that need care on OpenCV 4: float 0/0 is NaN, so Core.divide(x, x) is
 * replaced by [divSelf]. ROI views made with Mat.submat read the parent's pixels as border, like NLM's
 * `new Mat(parent, rect)`, so they are used as-is.
 */
object RbcDetector {
    private const val CC_AREA_TH = 2500.0
    private const val ORI_H = 2988.0
    private const val ORI_W = 5312.0
    private const val MIN_AREA = 150.0
    private val LOG_SIGMAS = intArrayOf(5, 6, 9)

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV native library failed to load" }
    }


    /** [ori]: the RGB uint8 field. Null when NLM would ask for a retake. */
    fun detect(ori: Mat): RbcDetection? {
        // CameraActivity.resizeImage, in float like the app
        val scaleFactor = sqrt(((2988f * 5312f) / (ori.rows().toFloat() * ori.cols().toFloat())).toDouble()).toFloat()
        val rv = 6f / scaleFactor
        val small = Mat()
        val size = Size((ori.cols().toFloat() / rv).toInt().toDouble(), (ori.rows().toFloat() / rv).toInt().toDouble())
        Imgproc.resize(ori, small, size, 0.0, 0.0, Imgproc.INTER_CUBIC)
        val (watershed, wbc) = segment(small, rv) ?: return null
        return RbcDetection(watershed, wbc, cells(watershed, wbc, ori))
    }

    /**
     * MarkerBasedWatershed.runMarkerBasedWatershed on the resized field: (watershed mask 0/255, WBC mask 0/1), or null
     * for a retake. Internal so tests can skip the resize, whose output differs by +-1 between ARM and x86 OpenCV.
     */
    internal fun segment(matImg: Mat, resizeValue: Float): Pair<Mat, Mat>? {
        val channels = ArrayList<Mat>().also { Core.split(matImg, it) }
        val green = channels[1]
        val greenBytes = ByteArray(green.total().toInt()).also { green.get(0, 0, it) }
        val stretched = NlmHistogram.stretch(IntArray(greenBytes.size) { greenBytes[it].toInt() and 0xff }, 0.01, 0.99)
            ?: return null
        val stretchMat = Mat(green.rows(), green.cols(), CvType.CV_64F)
        stretchMat.put(0, 0, *DoubleArray(stretched.size) { stretched[it].toDouble() })
        val ones64 = Mat.ones(green.size(), CvType.CV_64F)
        val normIm = Mat()
        Core.normalize(stretchMat, normIm, 0.0, 1.0, Core.NORM_MINMAX)
        Core.subtract(ones64, normIm, normIm)

        // field of view: below 0.8 in the negative image, holes filled, eroded 5x5
        val maskBorder = cmp(normIm, Scalar(0.8), Core.CMP_LT)
        fill(maskBorder, contours(maskBorder), Scalar(1.0))
        Imgproc.erode(maskBorder, maskBorder, rect(5, 5))

        // WBC mask, inside the bounding box of the largest field-of-view contour
        val fov = contours(maskBorder)
        var maxArea = 0.0
        var maxIdx = 0
        if (fov.size > 1) {
            fov.forEachIndexed { i, c ->
                val area = Imgproc.contourArea(c)
                if (area > maxArea) {
                    maxArea = area
                    maxIdx = i
                }
            }
        }
        val box = Imgproc.boundingRect(fov[maxIdx])
        val greenRoi = green.submat(box)
        val dilated = Mat().also { Imgproc.dilate(greenRoi, it, rect(3, 3)) }
        val eroded = Mat().also { Imgproc.erode(greenRoi, it, rect(3, 3)) }
        val r = Mat().also { Core.subtract(dilated, eroded, it) }
        val cropped = maskBorder.submat(box)
        Imgproc.erode(cropped, cropped, ellipse(4, 4))       // writes through to maskBorder, as in NLM
        Core.multiply(r, cropped, r)
        val rRes = Mat().also { Core.multiply(r, cmp(r, Scalar(20.0), Core.CMP_GT), it) }
        val value = 1.7 * Core.sumElems(rRes).`val`[0] / Core.countNonZero(rRes)
        val wbc = cmp(r, Scalar(scalarU8(value).toDouble()), Core.CMP_GT)
        Imgproc.dilate(wbc, wbc, ellipse(2, 2))
        fill(wbc, contours(wbc), Scalar(1.0))
        val frame = Mat.ones(wbc.size(), wbc.type())
        frame.submat(1, frame.rows() - 1, 1, frame.cols() - 1).setTo(Scalar(0.0))
        Core.subtract(wbc, morphReconstruct(frame, wbc), wbc)
        Imgproc.erode(wbc, wbc, ellipse(2, 2))
        val rbcAvgArea = Math.round(PI * 20.0.pow(2)).toDouble() / resizeValue
        val wbcContours = contours(wbc)
        wbcContours.forEachIndexed { i, c ->
            if (Imgproc.contourArea(c) <= rbcAvgArea) Imgproc.drawContours(wbc, wbcContours, i, Scalar(0.0), -1)
        }
        val wbcMask = Mat.zeros(maskBorder.size(), CvType.CV_8U)
        fill(wbcMask, contours(wbc, Point(box.x.toDouble(), box.y.toDouble())), Scalar(1.0))

        // Otsu inside the field of view
        val norm255 = Mat().also { Core.normalize(normIm, it, 0.0, 255.0, Core.NORM_MINMAX) }
        val th = NlmHistogram.otsu(
            DoubleArray(norm255.total().toInt()).also { norm255.get(0, 0, it) },
            ByteArray(maskBorder.total().toInt()).also { maskBorder.get(0, 0, it) },
        ) / 255.0
        val alpha = Mat().also { cmp(normIm, Scalar(th), Core.CMP_GT).convertTo(it, CvType.CV_64F) }
        Core.multiply(alpha, Mat().also { maskBorder.convertTo(it, CvType.CV_64F) }, alpha)

        // discard small blobs and noise: both bwareaopen passes reuse the contours of 1 - mask_alpha
        Core.subtract(ones64, alpha, alpha)
        val alphaU8 = toU8(alpha)
        val holes = contours(alphaU8)
        val smallHoles = holes.indices.filter { Imgproc.contourArea(holes[it]) <= MIN_AREA }
        val m32 = Mat().also { alphaU8.convertTo(it, CvType.CV_32F) }
        Core.multiply(m32, Scalar(255.0), m32)
        for (i in smallHoles) Imgproc.drawContours(m32, holes, i, Scalar(0.0), -1)
        val alphaF = Mat().also { Core.subtract(Mat(m32.size(), CvType.CV_32F, Scalar(255.0)), m32, it) }
        val alphaOnes = divSelf(alphaF)
        val m8 = toU8(alphaF)
        for (i in smallHoles) Imgproc.drawContours(m8, holes, i, Scalar(0.0), -1)
        val maskAlpha = divSelf(m8)

        // smooth, CLAHE, multi-scale LoG blob response
        val gk = Imgproc.getGaussianKernel(5, 1.0, CvType.CV_64F)
        val bigG = Mat().also { Core.gemm(gk, gk.t(), 1.0, Mat(), 0.0, it) }
        Core.flip(bigG, bigG, 1)
        val smooth = Mat().also { Imgproc.filter2D(normIm, it, CvType.CV_64F, bigG, Point(2.0, 2.0), 0.0, Core.BORDER_CONSTANT) }
        val smooth255 = Mat().also { Core.normalize(smooth, it, 0.0, 255.0, Core.NORM_MINMAX) }
        val i2 = Mat().also { Imgproc.createCLAHE(2.56, Size(8.0, 8.0)).apply(toU8(smooth255), it) }
        i2.convertTo(i2, CvType.CV_64F)
        Core.normalize(i2, i2, 0.0, 1.0, Core.NORM_MINMAX)
        val logs = LOG_SIGMAS.map { calculateLog(i2, it) }
        val lmin = Mat().also { Core.min(logs[0], logs[1], it) }
        Core.min(lmin, logs[2], lmin)

        // weight by distance to background (only where > 5 px), 3x3 sum, regional minima as markers
        val dist = Mat().also { Imgproc.distanceTransform(toU8(alphaOnes), it, Imgproc.DIST_L2, 5) }
        dist.convertTo(dist, CvType.CV_64F)
        Core.multiply(Mat().also { cmp(dist, Scalar(5.0), Core.CMP_GT).convertTo(it, CvType.CV_64F) }, dist, dist)
        val lm2 = Mat().also { Core.multiply(dist, lmin, it) }
        val box3 = Mat.ones(3, 3, CvType.CV_32F).also { Core.flip(it, it, 1) }
        Imgproc.filter2D(lm2, lm2, -1, box3, Point(1.0, 1.0), 0.0, Core.BORDER_CONSTANT)
        val markers = imregionalmin(lm2)
        Imgproc.dilate(markers, markers, rect(3, 3))

        val seeds = Mat().also { segmentWatershed(matImg, maskAlpha, markers).convertTo(it, CvType.CV_32S) }
        Imgproc.watershed(matImg, seeds)
        val watershed = Mat().also { Core.compare(seeds, Scalar(1.0), it, Core.CMP_GT) }
        return watershed to wbcMask
    }

    /** calculate_loG: separable Laplacian of Gaussian, borders of width 3*sigma set to 0. */
    private fun calculateLog(im: Mat, sigma: Int): Mat {
        val gLen = (sigma * 6 + 1) / 2
        val n = sigma * 3 * 2 + 1
        fun filled(v: Double) = Mat(1, n, CvType.CV_64F, Scalar(v))
        val x = Mat(1, n, CvType.CV_64F).also { it.put(0, 0, *DoubleArray(n) { i -> (i - gLen).toDouble() }) }
        val x2 = Mat().also { Core.pow(x, 2.0, it) }
        val s2 = Mat().also { Core.pow(filled(sigma.toDouble()), 2.0, it) }
        val s4 = Mat().also { Core.pow(filled(sigma.toDouble()), 4.0, it) }
        val gauss = Mat().also { Core.divide(x2, s2, it) }
        Core.multiply(gauss, filled(-0.5), gauss)
        Core.exp(gauss, gauss)
        Core.divide(gauss, filled(sqrt(2 * PI) * sigma), gauss)
        val dgxx = Mat().also { Core.subtract(x2, s2, it) }
        Core.divide(dgxx, s4, dgxx)
        Core.multiply(dgxx, gauss, dgxx)
        Core.flip(gauss, gauss, 1)
        Core.flip(dgxx, dgxx, 1)
        val gaussT = gauss.t()
        val dgxxT = dgxx.t()
        val col = Point(0.0, gLen.toDouble())
        val row = Point(gLen.toDouble(), 0.0)
        val ixx = Mat().also { Imgproc.filter2D(im, it, -1, gaussT, col, 0.0, Core.BORDER_CONSTANT) }
        Imgproc.filter2D(ixx, ixx, -1, dgxx, row, 0.0, Core.BORDER_CONSTANT)
        val iyy = Mat().also { Imgproc.filter2D(im, it, -1, dgxxT, col, 0.0, Core.BORDER_CONSTANT) }
        Imgproc.filter2D(iyy, iyy, -1, gauss, row, 0.0, Core.BORDER_CONSTANT)
        Imgproc.filter2D(ixx, ixx, -1, gaussT, col, 0.0, Core.BORDER_DEFAULT)   // NLM passed BORDER_CONSTANT as delta
        Imgproc.filter2D(ixx, ixx, -1, gauss, row, 0.0, Core.BORDER_CONSTANT)
        Imgproc.filter2D(iyy, iyy, -1, gaussT, col, 0.0, Core.BORDER_DEFAULT)   // same
        Imgproc.filter2D(iyy, iyy, -1, gauss, row, 0.0, Core.BORDER_CONSTANT)
        val lap = Mat().also { Core.add(ixx, iyy, it) }
        val pw = dgxx.cols() / 2
        lap.colRange(0, pw).setTo(Scalar(0.0))
        lap.colRange(lap.cols() - pw, lap.cols()).setTo(Scalar(0.0))
        lap.rowRange(0, pw).setTo(Scalar(0.0))
        lap.rowRange(lap.rows() - pw, lap.rows()).setTo(Scalar(0.0))
        return lap
    }

    /** SegmentWatershed.runSegmentWatershed: watershed seeds, background = 1, markers = contour index + 2. */
    private fun segmentWatershed(image: Mat, mask: Mat, marker: Mat): Mat {
        val channels = ArrayList<Mat>().also { Core.split(image, it) }
        val imin = Mat().also { Core.min(channels[1], channels[2], it) }
        imin.convertTo(imin, CvType.CV_64F)
        val kx = Mat(1, 3, CvType.CV_64F).also { it.put(0, 0, -0.5, 0.0, 0.5) }
        val dx = Mat().also { Imgproc.filter2D(imin, it, CvType.CV_64F, kx) }
        val dy = Mat().also { Imgproc.filter2D(imin, it, CvType.CV_64F, kx.t()) }
        Core.pow(dx, 2.0, dx)
        Core.pow(dy, 2.0, dy)
        val g = Mat().also { Core.add(dx, dy, it) }
        Core.sqrt(g, g)

        val maskDilated = Mat().also { Imgproc.dilate(mask, it, rect(9, 9)) }
        maskDilated.convertTo(maskDilated, CvType.CV_64F)
        val ones64 = Mat.ones(maskDilated.size(), CvType.CV_64F)
        fun filled(v: Double) = Mat(maskDilated.size(), CvType.CV_64F, Scalar(v))
        val bg = Mat().also { Core.subtract(ones64, maskDilated, it) }
        val bgCmp = Mat().also { cmp(bg, Scalar(0.0), Core.CMP_GT).convertTo(it, CvType.CV_64F) }
        Core.multiply(Mat().also { Core.subtract(ones64, bgCmp, it) }, bg, bg)
        Core.add(bg, bgCmp, bg)

        val marker32 = Mat().also { divSelf(Mat().also { m -> marker.convertTo(m, CvType.CV_64F) }).convertTo(it, CvType.CV_32F) }
        val markOrBg = Mat().also { Core.bitwise_or(Mat().also { b -> bg.convertTo(b, CvType.CV_32F) }, marker32, it) }
        val inMarkOrBg = Mat().also { Core.bitwise_xor(markOrBg, Mat.ones(markOrBg.size(), CvType.CV_32F), it) }

        // imimposemin
        val inf = Mat().also { inMarkOrBg.convertTo(it, CvType.CV_64F) }
        Core.multiply(inf, filled(-1.0), inf)
        Core.add(Mat().also { markOrBg.convertTo(it, CvType.CV_64F) }, inf, inf)
        val fm = Mat().also { Core.multiply(inf, filled(Double.POSITIVE_INFINITY), it) }
        Core.subtract(ones64, fm, fm)
        val range = Core.minMaxLoc(g).let { it.maxVal - it.minVal }
        val h = if (range == 0.0) 0.1 else range * 0.001
        val iminF = Mat().also { Core.add(g, filled(h), it) }
        Core.min(iminF, fm, iminF)
        val j = morphReconstruct(Mat().also { Core.subtract(ones64, fm, it) }, Mat().also { Core.subtract(ones64, iminF, it) })
        Core.subtract(ones64, j, j)
        val jv = DoubleArray(j.total().toInt()).also { j.get(0, 0, it) }
        for (i in jv.indices) jv[i] = if (jv[i].isInfinite()) 1.0 else 0.0
        j.put(0, 0, *jv)

        val markerContours = contours(toU8(marker32))
        for (i in markerContours.indices) Imgproc.drawContours(marker32, markerContours, i, Scalar((i + 1).toDouble()), -1)
        return Mat().also { marker32.convertTo(it, CvType.CV_64F); Core.add(it, j, it) }
    }

    /** Cells.runCells crop part: cells cut from the full-resolution RGB field, background set to 0. */
    internal fun cells(mask: Mat, wbcMask: Mat, ori: Mat): List<DetectedCell> {
        val rows = ori.rows()
        val cols = ori.cols()
        val scale = (ORI_H * ORI_W) / (rows.toDouble() * cols.toDouble())

        val newMask = Mat().also { Imgproc.resize(divSelf(mask), it, Size(cols.toDouble(), rows.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC) }
        val outline = Mat.zeros(newMask.size(), CvType.CV_8U)
        val maskContours = contours(newMask)
        for (i in maskContours.indices) Imgproc.drawContours(outline, maskContours, i, Scalar(1.0), 1)
        Core.subtract(newMask, outline, newMask)
        val labels = Mat()
        val stats = Mat()
        val n = Imgproc.connectedComponentsWithStats(newMask, labels, stats, Mat(), 4, CvType.CV_32S)

        // WBC overlap, tested on uint8 labels (so every label >= 255 is tested as 255) resized to the WBC mask
        val labelsSmall = Mat().also { small ->
            Imgproc.resize(Mat().also { labels.convertTo(it, CvType.CV_8U) }, small, wbcMask.size(), 0.0, 0.0, Imgproc.INTER_NEAREST)
        }
        val smallBytes = ByteArray(labelsSmall.total().toInt()).also { labelsSmall.get(0, 0, it) }
        val wbcBytes = ByteArray(wbcMask.total().toInt()).also { wbcMask.get(0, 0, it) }
        val onWbc = HashSet<Int>()
        for (i in smallBytes.indices) if (wbcBytes[i].toInt() != 0) onWbc += smallBytes[i].toInt() and 0xff

        val st = IntArray(n * 5).also { stats.get(0, 0, it) }
        val k7 = rect(7, 7)
        val cells = ArrayList<DetectedCell>()
        for (i in 1 until n) {
            if (min(i, 255) in onWbc) continue
            val x0 = st[i * 5]
            val y0 = st[i * 5 + 1]
            val w = st[i * 5 + 2]
            val h = st[i * 5 + 3]
            if (w.toLong() * h <= CC_AREA_TH / scale) continue                       // bounding-box area, as in NLM
            val chipLabels = IntArray(w * h).also { labels.submat(y0, y0 + h, x0, x0 + w).clone().get(0, 0, it) }
            val cleaned = Mat(h, w, CvType.CV_64F).also { it.put(0, 0, *DoubleArray(w * h) { k -> if (chipLabels[k] == i) 1.0 else 0.0 }) }
            Imgproc.dilate(cleaned, cleaned, k7)
            val cleaned8 = toU8(cleaned)
            val chipChannels = ArrayList<Mat>().also { Core.split(ori.submat(y0, y0 + h, x0, x0 + w), it) }
            for (c in chipChannels) Core.multiply(c, cleaned8, c)
            val chip = Mat().also { Core.merge(chipChannels, it) }
            cells += DetectedCell(PixelRect(x0, y0, w, h), y0 + h / 2, x0 + w / 2, chip)
        }
        return cells
    }

    private fun imregionalmin(im: Mat): Mat {
        val outside = Mat().also { Core.compare(im, Scalar(0.0), it, Core.CMP_EQ) }
        val local = Mat().also { Core.compare(Mat().also { e -> Imgproc.erode(im, e, Mat()) }, im, it, Core.CMP_EQ) }
        Core.subtract(local, outside, local)
        return local
    }

    private fun morphReconstruct(marker: Mat, mask: Mat): Mat {
        val k = rect(3, 3)
        val dst = Mat().also { Core.min(marker, mask, it) }
        Imgproc.dilate(dst, dst, k)
        Core.min(dst, mask, dst)
        val prev = Mat()
        val changed = Mat()
        do {
            dst.copyTo(prev)
            Imgproc.dilate(dst, dst, k)
            Core.min(dst, mask, dst)
            Core.compare(prev, dst, changed, Core.CMP_NE)
        } while (Core.countNonZero(changed) != 0)
        return dst
    }

    private fun rect(w: Int, h: Int) = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(w.toDouble(), h.toDouble()))

    private fun ellipse(w: Int, h: Int) = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(w.toDouble(), h.toDouble()))

    /** Core.compare followed by Core.divide(r, r, r): uint8 0/1. */
    private fun cmp(a: Mat, s: Scalar, op: Int): Mat = Mat().also {
        Core.compare(a, s, it, op)
        Core.min(it, Scalar(1.0), it)
    }

    /** Core.divide(m, m) as on NLM's OpenCV 3.4.2: 1 where m != 0, else 0, in m's type. */
    private fun divSelf(m: Mat): Mat = Mat().also { cmp(m, Scalar(0.0), Core.CMP_NE).convertTo(it, m.type()) }

    /** convertTo(CV_8U): round half to even, saturate. */
    private fun toU8(m: Mat): Mat = Mat().also { m.convertTo(it, CvType.CV_8U) }

    /** Mat.setTo(new Scalar(v)) on a CV_8U Mat. */
    private fun scalarU8(v: Double): Int = when {
        v.isNaN() -> 0
        v.isInfinite() -> if (v > 0) 255 else 0
        else -> Math.rint(v).toInt().coerceIn(0, 255)
    }

    private fun contours(img: Mat, offset: Point = Point(0.0, 0.0)): List<MatOfPoint> = ArrayList<MatOfPoint>().also {
        Imgproc.findContours(img.clone(), it, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE, offset)
    }

    private fun fill(img: Mat, contours: List<MatOfPoint>, color: Scalar) {
        for (i in contours.indices) Imgproc.drawContours(img, contours, i, color, -1)
    }
}
