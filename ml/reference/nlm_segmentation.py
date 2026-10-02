# SPDX-License-Identifier: GPL-3.0-only
"""Python port of NIH/NLM Malaria Screener's thin-smear cell segmentation and chip extraction.

Ported from https://github.com/nlm-malaria/MalariaScreener, commit c485a211230c9acfe3f67280f7bb2831c4fd15e5,
app/src/main/java/gov/nih/nlm/malaria_screener/imageProcessing/:
    Segmentation/MarkerBasedWatershed.java   -> marker_based_watershed() and helpers
    Segmentation/SegmentWatershed.java       -> segment_watershed()
    Segmentation/OtsuThreshold.java          -> otsu_threshold()
    Segmentation/Histogram.java              -> _histogram()
    Cells.java, runCells()                   -> run_cells() (crop part only; SVM features and classifier dropped)

Original notice: "Copyright 2020 The Malaria Screener Authors. All Rights Reserved. This software was developed
under contract funded by the National Library of Medicine [...] Licensed under GNU General Public License v3.0".
This file is a derivative work and is licensed under the GNU General Public License v3.0 only; see
LICENSES/GPL-3.0.txt and LICENSING.md. Modified 2026-10-02 by the DeepSight team: translated from Java on
OpenCV 3.4.2 to Python on OpenCV 4.x; logging, timing, memory releases and classification removed.

Behaviour reproduced on purpose (each looks like a bug, but it is what the app does):
- Images are RGB: CameraActivity reads with imread and converts COLOR_BGR2RGB, so the "b g r" comments in the
  Java are wrong. Channel 1 is green, channel 2 is blue.
- Core.divide(x, x) gives 1 where x != 0 and 0 where x == 0. That is OpenCV 3.4.2 (openCVLibrary342), where
  float 0/0 is 0; OpenCV 4 returns NaN, so _div_self() is used instead of cv2.divide.
- Core.compare gives uint8 0/255. Mat.setTo(Scalar(v)) on uint8 rounds half to even and saturates.
- bitwise_or / bitwise_xor run on the bit patterns of float32 0/1 masks, which acts as logical OR / NOT.
- A ROI made with new Mat(parent, rect) is filtered with the parent's pixels as its border, and the in-place
  erode of the mask_border ROI writes through to mask_border (_morph_roi()).
- calculate_loG passes Core.BORDER_CONSTANT as filter2D's `delta` in two calls, so those use BORDER_DEFAULT.
- Histogram bins ignore the minimum (h = v / ((max - min) / 256)), so any green channel whose minimum is not
  near 0 raises the retake flag and the image is rejected.
- runCells converts component labels to uint8 before the WBC test, so every label >= 255 is tested as 255.
"""
import math

import cv2
import numpy as np

CC_AREA_TH = 2500                 # Cells.CCAreaTh
ORI_H, ORI_W = 2988.0, 5312.0     # Cells.ori_height, ori_width
MIN_AREA = 150                    # MarkerBasedWatershed: in_min_area_size
LOG_SIGMAS = (5, 6, 9)            # MarkerBasedWatershed: in_sigma


def _rect(w, h):
    return cv2.getStructuringElement(cv2.MORPH_RECT, (w, h))


def _ellipse(w, h):
    return cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (w, h))


def _div_self(m):
    """Core.divide(m, m) on OpenCV 3.4.2: 1 where m != 0, else 0, same dtype."""
    out = np.zeros_like(m)
    out[m != 0] = 1
    return out


def _mask(cond):
    """Core.compare(...) followed by Core.divide(r, r, r): uint8 0/1."""
    return cond.astype(np.uint8)


def _to_u8(m):
    """convertTo(CV_8U) from float: round half to even, saturate."""
    return np.clip(np.rint(m), 0, 255).astype(np.uint8)


def _scalar_u8(v):
    """Mat.setTo(new Scalar(v)) on a CV_8U Mat: saturate_cast<uchar>(v)."""
    if math.isnan(v):
        return 0
    if math.isinf(v):
        return 255 if v > 0 else 0
    return int(np.clip(np.rint(v), 0, 255))


def _contours(img, offset=(0, 0)):
    return cv2.findContours(np.ascontiguousarray(img), cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE, offset=offset)[0]


def _fill(img, contours, color):
    """for (i...) Imgproc.drawContours(img, contours, i, color, -1)."""
    for i in range(len(contours)):
        cv2.drawContours(img, contours, i, color, -1)


def _morph_roi(op, img, rect, kernel):
    """op(new Mat(img, rect)): OpenCV reads the parent's pixels outside the ROI as border."""
    x, y, w, h = rect
    m = max(kernel.shape)
    x0, y0 = max(x - m, 0), max(y - m, 0)
    x1, y1 = min(x + w + m, img.shape[1]), min(y + h + m, img.shape[0])
    out = op(np.ascontiguousarray(img[y0:y1, x0:x1]), kernel)
    return out[y - y0:y - y0 + h, x - x0:x - x0 + w]


def _morph_reconstruct(marker, mask):
    """morphReconstruct() in MarkerBasedWatershed and SegmentWatershed (identical)."""
    k = _rect(3, 3)
    dst = np.minimum(marker, mask)
    dst = np.minimum(cv2.dilate(dst, k), mask)
    while True:
        prev = dst.copy()
        dst = np.minimum(cv2.dilate(dst, k), mask)
        if not (prev != dst).any():
            return dst


# ---- Histogram.java / MarkerBasedWatershed.stretchHist_8bit ----------------------------------------------

def _histogram(im):
    """Histogram.runHistogram(im, 256) -> (hist float32[256], retake flag)."""
    im = im.astype(np.float64)
    rng = (im.max() - im.min()) / 256
    with np.errstate(divide="ignore", invalid="ignore"):
        q = im / rng
    # Java (int) cast: truncation toward zero, NaN -> 0, +inf and large values -> Integer.MAX_VALUE
    h = np.where(np.isnan(q), 0, np.minimum(q, 2 ** 31 - 1)).astype(np.int64)
    retake = bool((h > 256).any())
    counted = np.minimum(h[h <= 256], 255)            # h == 256 goes in the last bin
    return np.bincount(counted, minlength=256).astype(np.float32), retake


def _hist_centers(arr, bins=256):
    mn, mx = float(arr.min()), float(arr.max())
    dist = (mx - mn) / (bins * 2)
    centers = np.zeros(bins)
    centers[bins - 1] = mx - dist
    for i in range(1, bins):
        centers[bins - 1 - i] = (mx - dist) - dist * 2 * i
    return centers


def stretch_hist_8bit(green, min_percent, max_percent):
    """Clip the green channel to the 1%/99% histogram centers. None when Histogram sets the retake flag."""
    g64 = green.astype(np.float64)
    centers = _hist_centers(g64)
    h, retake = _histogram(g64)
    if retake:
        return None
    ch = np.cumsum(h.astype(np.float64))
    ch = ch / ch[-1]
    lower = next((i for i, v in enumerate(ch) if v >= min_percent), 0)
    upper = next((i for i, v in enumerate(ch) if v >= max_percent), 0)
    lo, hi = np.float32(centers[lower]), np.float32(centers[upper])
    g32 = green.astype(np.float32)
    cl = (g32 < lo).astype(np.float32)
    cu = (g32 > hi).astype(np.float32)
    new = g32 * (np.float32(1) - cl) * (np.float32(1) - cu)
    new = new + cl * lo
    new = new + cu * hi
    return _to_u8(new)


# ---- OtsuThreshold.java ------------------------------------------------------------------------------------

def otsu_threshold(image, mask):
    """OtsuThreshold.runOtsuThreshold: threshold in 0..255 over pixels where mask == 1, float32 arithmetic."""
    new = cv2.normalize(image, None, 0, 255, cv2.NORM_MINMAX)
    hist = np.bincount(new[mask == 1].astype(np.int64), minlength=256)[:256]
    total = int(hist.sum())
    f = np.float32
    s = f(0)
    for t in range(256):
        s = f(s + f(t * int(hist[t])))
    sum_b, w_b, var_max, threshold = f(0), 0, f(0), 0
    for t in range(256):
        w_b += int(hist[t])
        if w_b == 0:
            continue
        w_f = total - w_b
        if w_f == 0:
            break
        sum_b = f(sum_b + f(t * int(hist[t])))
        m_b = f(sum_b / f(w_b))
        m_f = f((s - sum_b) / f(w_f))
        d = f(m_b - m_f)
        var_between = f(f(f(f(w_b) * f(w_f)) * d) * d)
        if var_between > var_max:
            var_max, threshold = var_between, t
    return threshold


# ---- MarkerBasedWatershed.java helpers ---------------------------------------------------------------------

def _imregionalmin(im):
    outside = _mask(im == 0) * np.uint8(255)
    local = _mask(cv2.erode(im, None) == im) * np.uint8(255)
    return cv2.subtract(local, outside)


def calculate_log(im, sigma):
    """calculate_loG: separable Laplacian of Gaussian, borders of width 3*sigma set to 0."""
    g_len = (sigma * 6 + 1) // 2
    n = sigma * 3 * 2 + 1
    x = np.arange(-g_len, -g_len + n, dtype=np.float64).reshape(1, n)
    x2 = cv2.pow(x, 2)
    s2 = cv2.pow(np.full((1, n), float(sigma)), 2)
    s4 = cv2.pow(np.full((1, n), float(sigma)), 4)
    d = math.sqrt(2 * math.pi) * sigma
    gauss = cv2.exp((x2 / s2) * -0.5) / d
    dgxx = (x2 - s2) / s4 * gauss
    gauss, dgxx = cv2.flip(gauss, 1), cv2.flip(dgxx, 1)
    gauss_t, dgxx_t = np.ascontiguousarray(gauss.T), np.ascontiguousarray(dgxx.T)
    const, default = cv2.BORDER_CONSTANT, cv2.BORDER_DEFAULT
    col, row = (0, g_len), (g_len, 0)
    ixx = cv2.filter2D(im, -1, gauss_t, anchor=col, delta=0, borderType=const)
    ixx = cv2.filter2D(ixx, -1, dgxx, anchor=row, delta=0, borderType=const)
    iyy = cv2.filter2D(im, -1, dgxx_t, anchor=col, delta=0, borderType=const)
    iyy = cv2.filter2D(iyy, -1, gauss, anchor=row, delta=0, borderType=const)
    ixx = cv2.filter2D(ixx, -1, gauss_t, anchor=col, delta=0, borderType=default)  # Java passed BORDER_CONSTANT as delta
    ixx = cv2.filter2D(ixx, -1, gauss, anchor=row, delta=0, borderType=const)
    iyy = cv2.filter2D(iyy, -1, gauss_t, anchor=col, delta=0, borderType=default)  # same
    iyy = cv2.filter2D(iyy, -1, gauss, anchor=row, delta=0, borderType=const)
    lap = ixx + iyy
    pw = dgxx.shape[1] // 2
    lap[:, :pw] = 0
    lap[:, lap.shape[1] - pw:] = 0
    lap[:pw, :] = 0
    lap[lap.shape[0] - pw:, :] = 0
    return lap


# ---- SegmentWatershed.java ---------------------------------------------------------------------------------

def segment_watershed(image, mask, marker):
    """Watershed seed image: background = 1, markers = contour index + 2, unknown = 0 (float64)."""
    imin = np.minimum(image[:, :, 1], image[:, :, 2]).astype(np.float64)
    kx = np.array([[-0.5, 0.0, 0.5]])
    dx = cv2.pow(cv2.filter2D(imin, cv2.CV_64F, kx), 2)
    dy = cv2.pow(cv2.filter2D(imin, cv2.CV_64F, np.ascontiguousarray(kx.T)), 2)
    g = cv2.sqrt(dx + dy)

    mask_dilated = cv2.dilate(mask, _rect(9, 9)).astype(np.float64)
    bg = 1.0 - mask_dilated
    bg_cmp = _mask(bg > 0).astype(np.float64)
    bg = (1.0 - bg_cmp) * bg
    bg = bg + bg_cmp

    marker = _div_self(marker.astype(np.float64)).astype(np.float32)
    one_bits = np.float32(1).view(np.uint32)
    mark_or_bg = (bg.astype(np.float32).view(np.uint32) | marker.view(np.uint32)).view(np.float32)
    in_mark_or_bg = (mark_or_bg.view(np.uint32) ^ one_bits).view(np.float32)

    # imimposemin
    inf_mask = mark_or_bg.astype(np.float64) + in_mark_or_bg.astype(np.float64) * -1.0
    fm = 1.0 - inf_mask * np.inf
    rng = float(g.max() - g.min())
    h = 0.1 if rng == 0 else rng * 0.001
    imin = np.minimum(g + h, fm)
    j = _morph_reconstruct(1.0 - fm, 1.0 - imin)
    j = 1.0 - j
    j = np.where(np.isinf(j), 1.0, 0.0)

    contours = _contours(marker.astype(np.uint8))
    for i in range(len(contours)):
        cv2.drawContours(marker, contours, i, float(i + 1), -1)
    return marker.astype(np.float64) + j


# ---- MarkerBasedWatershed.runMarkerBasedWatershed ----------------------------------------------------------

def marker_based_watershed(mat_img, resize_value):
    """mat_img: RGB uint8 at segmentation size (CameraActivity.resizeImage); resize_value: RV.

    Returns (watershed_result uint8 0/255, wbc_mask uint8 0/1), both at segmentation size, or None when the
    app would ask for a retake.
    """
    resize_value = float(np.float32(resize_value))       # Java float parameter
    green = np.ascontiguousarray(mat_img[:, :, 1])

    stretch = stretch_hist_8bit(green, 0.01, 0.99)
    if stretch is None:
        return None
    norm_im = 1.0 - cv2.normalize(stretch.astype(np.float64), None, 0, 1, cv2.NORM_MINMAX)

    # field-of-view mask: below 0.8 in the negative image, holes filled, eroded 5x5
    mask_border = _mask(norm_im < 0.8)
    _fill(mask_border, _contours(mask_border), 1)
    mask_border = cv2.erode(mask_border, _rect(5, 5))

    # WBC mask, computed inside the bounding box of the largest field-of-view contour
    contours = _contours(mask_border)
    max_area, max_idx = 0.0, 0
    if len(contours) > 1:
        for i, c in enumerate(contours):
            area = cv2.contourArea(c)
            if area > max_area:
                max_area, max_idx = area, i
    rect = cv2.boundingRect(contours[max_idx])           # IndexError if no field of view, as in Java
    x, y, w, h = rect
    r = cv2.subtract(_morph_roi(cv2.dilate, green, rect, _rect(3, 3)), _morph_roi(cv2.erode, green, rect, _rect(3, 3)))
    mask_border[y:y + h, x:x + w] = _morph_roi(cv2.erode, mask_border, rect, _ellipse(4, 4))
    r = cv2.multiply(r, mask_border[y:y + h, x:x + w].copy())
    r_res = cv2.multiply(r, _mask(r > 20))
    total, count = float(r_res.sum()), int(np.count_nonzero(r_res))
    with np.errstate(divide="ignore", invalid="ignore"):
        value = float(np.float64(1.7) * total / count) if count else (math.inf if total > 0 else math.nan)
    wbc = _mask(r > _scalar_u8(value))
    wbc = cv2.dilate(wbc, _ellipse(2, 2))
    _fill(wbc, _contours(wbc), 1)
    frame = np.ones_like(wbc)                            # imclearborder
    frame[1:-1, 1:-1] = 0
    wbc = cv2.subtract(wbc, _morph_reconstruct(frame, wbc))
    wbc = cv2.erode(wbc, _ellipse(2, 2))
    rbc_avg_area = math.floor(math.pi * math.pow(20, 2) + 0.5) / resize_value   # Math.round, then size adjust
    wbc3 = cv2.merge([wbc, wbc, wbc])
    contours = _contours(wbc)
    for i, c in enumerate(contours):
        if cv2.contourArea(c) <= rbc_avg_area:
            cv2.drawContours(wbc3, contours, i, (0, 0, 0), -1)
    wbc = _div_self(np.ascontiguousarray(wbc3[:, :, 0]))
    wbc_mask = np.zeros(mask_border.shape, np.uint8)
    _fill(wbc_mask, _contours(wbc, offset=(x, y)), (1, 1, 1))

    # Otsu inside the field of view
    th = otsu_threshold(norm_im, mask_border) / 255
    mask_alpha = _mask(norm_im > th).astype(np.float64) * mask_border.astype(np.float64)

    # discard small blobs and noise: both bwareaopen passes reuse the contours of 1 - mask_alpha
    mask_alpha = _to_u8(1.0 - mask_alpha)
    contours = _contours(mask_alpha)
    small = [i for i, c in enumerate(contours) if cv2.contourArea(c) <= MIN_AREA]
    m3 = cv2.merge([mask_alpha.astype(np.float32) * np.float32(255)] * 3)
    for i in small:
        cv2.drawContours(m3, contours, i, (0, 0, 0), -1)
    mask_alpha_f = np.float32(255) - m3[:, :, 0]
    mask_alpha_ones = _div_self(mask_alpha_f)
    m3 = cv2.merge([_to_u8(mask_alpha_f)] * 3)
    for i in small:
        cv2.drawContours(m3, contours, i, (0, 0, 0), -1)
    mask_alpha = _div_self(np.ascontiguousarray(m3[:, :, 0]))

    # smooth, CLAHE, multi-scale LoG blob response
    gk = cv2.getGaussianKernel(5, 1, cv2.CV_64F)
    big_g = cv2.flip(gk @ gk.T, 1)
    smooth = cv2.filter2D(norm_im, cv2.CV_64F, big_g, anchor=(2, 2), delta=0, borderType=cv2.BORDER_CONSTANT)
    clahe = cv2.createCLAHE(clipLimit=2.56, tileGridSize=(8, 8))
    i2 = clahe.apply(_to_u8(cv2.normalize(smooth, None, 0, 255, cv2.NORM_MINMAX))).astype(np.float64)
    i2 = cv2.normalize(i2, None, 0, 1, cv2.NORM_MINMAX)
    logs = [calculate_log(i2, s) for s in LOG_SIGMAS]
    lmin = np.minimum(np.minimum(logs[0], logs[1]), logs[2])

    # weight by distance to background (only where > 5 px), 3x3 sum, regional minima as markers
    dist = cv2.distanceTransform(_to_u8(mask_alpha_ones), cv2.DIST_L2, 5).astype(np.float64)
    dist = _mask(dist > 5).astype(np.float64) * dist
    lm2 = dist * lmin
    lm2 = cv2.filter2D(lm2, -1, cv2.flip(np.ones((3, 3), np.float32), 1), anchor=(1, 1), delta=0,
                       borderType=cv2.BORDER_CONSTANT)
    markers = cv2.dilate(_imregionalmin(lm2), _rect(3, 3))

    seeds = segment_watershed(mat_img, mask_alpha, markers)
    seeds = np.rint(seeds).astype(np.int32)
    cv2.watershed(np.ascontiguousarray(mat_img, dtype=np.uint8), seeds)
    return _mask(seeds > 1) * np.uint8(255), wbc_mask


# ---- Cells.runCells ----------------------------------------------------------------------------------------

def run_cells(mask, wbc_mask, ori):
    """Crop cells from the full-resolution RGB image `ori`.

    mask: watershed_result (uint8 0/255) and wbc_mask (uint8 0/1), both at segmentation size.
    Returns (chips, centers, boxes) in extract_chips' format: RGB uint8 chips with background set to 0,
    centers as (row, col), boxes as (x0, y0, x1, y1) in full-resolution pixels.
    """
    rows, cols = ori.shape[:2]
    scale = (ORI_H * ORI_W) / (float(rows) * float(cols))

    new_mask = cv2.resize(_div_self(mask), (cols, rows), interpolation=cv2.INTER_CUBIC)
    outline = np.zeros(new_mask.shape, np.uint8)
    contours = _contours(new_mask)
    for i in range(len(contours)):
        cv2.drawContours(outline, contours, i, 1, 1)
    new_mask = cv2.subtract(new_mask, outline)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(new_mask, connectivity=4, ltype=cv2.CV_32S)

    # WBC overlap, tested on uint8 labels resized (nearest) to the WBC mask
    labels_small = cv2.resize(np.clip(labels, 0, 255).astype(np.uint8), (wbc_mask.shape[1], wbc_mask.shape[0]),
                              interpolation=cv2.INTER_NEAREST)
    on_wbc = set(np.unique(labels_small[wbc_mask != 0]).tolist())

    k7 = _rect(7, 7)
    chips, centers, boxes = [], [], []
    for i in range(1, n):
        if min(i, 255) in on_wbc:                         # numMat.setTo(i) saturates at 255
            continue
        x0, y0, w, h = (int(v) for v in stats[i, :4])
        chip_labels = labels[y0:y0 + h, x0:x0 + w].astype(np.float64)
        if chip_labels.size <= CC_AREA_TH / scale:        # bounding-box area, not pixel count
            continue
        cleaned = np.where((chip_labels != i) & (chip_labels != 0), 0.0, chip_labels)
        cleaned = _to_u8(cv2.dilate(_div_self(cleaned), k7))
        chip = cv2.merge([cv2.multiply(c, cleaned) for c in cv2.split(np.ascontiguousarray(ori[y0:y0 + h, x0:x0 + w]))])
        centers.append((y0 + h // 2, x0 + w // 2))
        boxes.append((x0, y0, x0 + w, y0 + h))
        chips.append(chip)
    return chips, centers, boxes
