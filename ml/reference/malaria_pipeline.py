"""
Reference implementation of the DeepSight malaria (thin smear) module.

This is the SPEC the mobile integration must reproduce. Every numeric constant
below is taken from NIH/NLM Malaria Screener source (github.com/nlm-malaria/MalariaScreener)
unless marked [SIMPLIFIED], where the original Java was replaced by a standard
OpenCV equivalent that exists on every mobile OpenCV binding.

Pipeline (one microscope field-of-view photo -> counts):
  1. resize photo by RV for segmentation          (CameraActivity.resizeImage)
  2. segment red blood cells -> label map          [SIMPLIFIED] (MarkerBasedWatershed)
  3. upscale labels to full-res, crop each cell,   (Cells.runCells)
     mask background to black, dilate mask 7x7
  4. resize chip to 44x44 (bicubic), RGB, /255     (Cells.putInPixels)
  5. ONNX model -> probs[:,0] = P(infected)        (TensorFlowClassifier.recongnize_batch)
  6. infected if P(infected) > TH (default 0.5)    (UtilsCustom.Th)
  7. image confidence = median P over infected     (ThinSmearProcessor.cal_image_conf)

STATUS: steps 4-7 are validated (see ml/packs/malaria_thin/README.md). Steps 1-3 (segmentation) are NOT:
on a confirmed-uninfected NIH patient (C12N) this pipeline flags ~13% of cells.
Treat segment_cells/extract_chips as a swappable stage.

Usage:
  python ml/reference/malaria_pipeline.py image1.jpg [image2.jpg ...] [--model ml/packs/malaria_thin/model.onnx] [--debug outdir]
"""
import argparse, json, os, sys
import numpy as np
import cv2
import onnxruntime as ort

# ---- constants from NIH source -------------------------------------------
REF_W, REF_H = 5312, 2988        # CameraActivity.resizeImage: reference capture size
RV_BASE = 6.0                     # CameraActivity: "float RV = 6; //resize value"
CC_AREA_TH = 2500                 # Cells: CCAreaTh, min cell area (px at reference res)
DILATE_K = 7                      # Cells: 7x7 rect dilation of cell mask before cropping
CHIP = 44                         # model input H=W=44
TH = 0.5                          # UtilsCustom.Th (user-adjustable in app settings)
MIN_BLOB_SMALL = 150              # MarkerBasedWatershed: in_min_area_size (px at segmentation res)
STRETCH_LO, STRETCH_HI = 0.01, 0.99  # MarkerBasedWatershed: histogram stretch percentiles
# ---- [SIMPLIFIED] segmentation params ----------------------------------------
DT_PEAK_REL = 0.45                # marker = distance-transform >= 0.45*local max  (tuned on NIH sample images)
CLUSTER_AREA_X = 3.0              # components > 3x median cell area are treated as clumps/WBC and skipped
MIN_SOLIDITY = 0.90               # [ADDED QC] RBCs are convex; gaps/fused cells are not
BG_MARGIN = 15                    # [ADDED QC] cell mean green must be >=15 levels darker than background
OTSU_SCALE = 1.0                  # [SIMPLIFIED] multiplier on Otsu threshold (<1 = looser cell boundary)


def compute_rv(h, w):
    """RV = 6 / sqrt(ref_area / cur_area)  (CameraActivity.resizeImage)."""
    scale_factor = np.sqrt((REF_H * REF_W) / float(h * w))
    return RV_BASE / scale_factor


def segment_cells(rgb_small):
    """[SIMPLIFIED] Otsu + distance-transform watershed on the green channel.
    Returns int32 label map (0 = background) at segmentation resolution."""
    # field-of-view mask: drop the black vignette outside the eyepiece circle
    # (MarkerBasedWatershed: mask_border = inv < 0.8, keep largest contour, 5x5 erode)
    fov = (np.max(rgb_small, axis=2) > 40).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(fov, 8)
    if n < 2:
        return np.zeros(fov.shape, np.int32)
    fov = (lab == 1 + np.argmax(st[1:, cv2.CC_STAT_AREA])).astype(np.uint8)
    fov = cv2.erode(fov, np.ones((15, 15), np.uint8))
    green = rgb_small[:, :, 1]
    lo, hi = np.quantile(green[fov > 0], [STRETCH_LO, STRETCH_HI])   # stretch using FOV pixels only
    g = np.clip((green.astype(np.float32) - lo) / max(hi - lo, 1e-6), 0, 1)
    inv = 1.0 - g                                     # cells darker than background in green -> high
    inv8 = (inv * 255).astype(np.uint8)
    inv8 = cv2.GaussianBlur(inv8, (5, 5), 1)
    t, _ = cv2.threshold(inv8[fov > 0].reshape(-1, 1), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)  # Otsu inside FOV only
    t = t * OTSU_SCALE
    fg = ((inv8 > t) & (fov > 0)).astype(np.uint8) * 255
    fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    # fill SMALL holes only (RBC central pallor). Large holes are background gaps enclosed by touching cells.
    rv_small_cell = np.pi * 20 ** 2 * (fg.shape[0] * fg.shape[1]) / (REF_H * REF_W / RV_BASE ** 2) / RV_BASE  # NIH RBC_avgArea = pi*20^2/RV, rescaled
    nh, hl, hs, _ = cv2.connectedComponentsWithStats((fg == 0).astype(np.uint8), 4)
    for i in range(1, nh):
        if hs[i, cv2.CC_STAT_AREA] < rv_small_cell:
            fg[hl == i] = 255
    # drop tiny blobs (in_min_area_size = 150)
    n, lab, st, _ = cv2.connectedComponentsWithStats(fg, 8)
    for i in range(1, n):
        if st[i, cv2.CC_STAT_AREA] <= MIN_BLOB_SMALL:
            fg[lab == i] = 0
    # markers from distance transform peaks
    dt = cv2.distanceTransform(fg, cv2.DIST_L2, 5)
    local_max = cv2.dilate(dt, np.ones((9, 9), np.uint8))
    peaks = ((dt >= DT_PEAK_REL * local_max) & (dt == local_max) & (dt > 3)).astype(np.uint8)
    peaks = cv2.dilate(peaks, np.ones((3, 3), np.uint8))
    nm, markers = cv2.connectedComponents(peaks)
    markers = markers + 1                              # background = 1
    markers[(fg > 0) & (peaks == 0)] = 0               # unknown region -> watershed decides
    markers = cv2.watershed(cv2.cvtColor(rgb_small, cv2.COLOR_RGB2BGR), markers.astype(np.int32))
    markers[markers <= 1] = 0                          # background & boundaries
    return markers


def extract_chips(rgb_full, labels_small):
    """Cells.runCells: upscale each cell mask to full res, filter by area, dilate 7x7, mask background black, crop.
    (NIH upsamples the binary mask with INTER_CUBIC; we upsample each cell's mask bilinearly and threshold 0.5,
    which keeps edges smooth instead of blocky.)"""
    H, W = rgb_full.shape[:2]
    h, w = labels_small.shape
    sy, sx = H / h, W / w
    scale = (REF_H * REF_W) / float(H * W)
    min_area = CC_AREA_TH / scale
    k = cv2.getStructuringElement(cv2.MORPH_RECT, (DILATE_K, DILATE_K))
    small_bg = cv2.resize((labels_small == 0).astype(np.uint8), (W, H), interpolation=cv2.INTER_NEAREST) > 0
    bright = rgb_full.max(axis=2) > 40                          # exclude black vignette
    bg_green = float(np.median(rgb_full[:, :, 1][small_bg & bright]))
    cand = []
    for i in np.unique(labels_small):
        if i == 0:
            continue
        ys, xs = np.where(labels_small == i)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        ms = (labels_small[y0:y1, x0:x1] == i).astype(np.float32)
        Y0, Y1, X0, X1 = int(y0 * sy), min(H, int(np.ceil(y1 * sy))), int(x0 * sx), min(W, int(np.ceil(x1 * sx)))
        m = (cv2.resize(ms, (X1 - X0, Y1 - Y0), interpolation=cv2.INTER_LINEAR) > 0.5).astype(np.uint8)
        a = int(m.sum())
        if a <= min_area:
            continue
        # [ADDED QC] reject concave shapes (background gaps between touching cells, fused pairs)
        cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        c = max(cnts, key=cv2.contourArea)
        solidity = cv2.contourArea(c) / max(cv2.contourArea(cv2.convexHull(c)), 1)
        if solidity < MIN_SOLIDITY:
            continue
        # [ADDED QC] reject regions whose colour is background, not stained cell
        if rgb_full[Y0:Y1, X0:X1, 1][m > 0].mean() > bg_green - BG_MARGIN:
            continue
        cand.append((a, (X0, Y0, X1, Y1), m))
    if not cand:
        return [], [], []
    med = np.median([c[0] for c in cand])
    chips, centers, boxes = [], [], []
    for a, (X0, Y0, X1, Y1), m in cand:
        if a > CLUSTER_AREA_X * med:
            continue                                    # [SIMPLIFIED] stands in for NIH WBC-overlap skip
        m = cv2.dilate(m, k)
        chip = rgb_full[Y0:Y1, X0:X1] * m[:, :, None]   # background -> black
        chips.append(chip); centers.append(((Y0 + Y1) // 2, (X0 + X1) // 2)); boxes.append((X0, Y0, X1, Y1))
    return chips, centers, boxes


def preprocess(chips):
    """Cells.putInPixels: bicubic resize to 44x44, RGB order, /255 -> NHWC float32."""
    x = np.stack([cv2.resize(c.astype(np.uint8), (CHIP, CHIP), interpolation=cv2.INTER_CUBIC) for c in chips])
    return (x.astype(np.float32) / 255.0)


class MalariaThin:
    def __init__(self, model_path):
        self.sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])

    def classify(self, x):
        return self.sess.run(["probs"], {"input": x})[0]   # [N,2]; col 0 = infected, col 1 = uninfected

    def run_image(self, path, th=TH, debug_dir=None):
        bgr = cv2.imread(path)
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        H, W = rgb.shape[:2]
        rv = compute_rv(H, W)
        small = cv2.resize(rgb, (int(W / rv), int(H / rv)), interpolation=cv2.INTER_CUBIC)
        labels = segment_cells(small)
        chips, centers, boxes = extract_chips(rgb, labels)
        if not chips:
            return {"image": os.path.basename(path), "cells": 0, "infected": 0, "error": "no cells found - retake"}
        p = self.classify(preprocess(chips))[:, 0]
        inf = p > th
        res = {
            "image": os.path.basename(path),
            "cells": int(len(chips)),
            "infected": int(inf.sum()),
            "parasitemia_pct": round(100.0 * inf.sum() / len(chips), 2),
            "image_conf": float(np.median(p[inf])) if inf.any() else 0.0,
            "infected_cells": [{"box_xyxy": boxes[j], "p_infected": round(float(p[j]), 4)} for j in np.where(inf)[0]],
        }
        if debug_dir:
            os.makedirs(debug_dir, exist_ok=True)
            vis = bgr.copy()
            for j, (x0, y0, x1, y1) in enumerate(boxes):
                cv2.rectangle(vis, (x0, y0), (x1, y1), (0, 0, 255) if inf[j] else (0, 200, 0), 6 if inf[j] else 2)
            cv2.imwrite(os.path.join(debug_dir, os.path.splitext(res["image"])[0] + "_overlay.jpg"),
                        cv2.resize(vis, (W // 3, H // 3)))
        return res


def aggregate(results, total_cells_needed=1000):
    """Slide-level summary. NIH app collects fields until >=1000 RBCs (CameraActivity.totalCellNeeded)."""
    cells = sum(r["cells"] for r in results); inf = sum(r["infected"] for r in results)
    return {"fields": len(results), "cells": cells, "infected": inf,
            "parasitemia_pct": round(100.0 * inf / max(cells, 1), 3),
            "enough_cells": cells >= total_cells_needed}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("images", nargs="+")
    ap.add_argument("--model", default=os.path.join(os.path.dirname(__file__), "..", "packs", "malaria_thin", "model.onnx"))
    ap.add_argument("--th", type=float, default=TH)
    ap.add_argument("--debug", default=None)
    a = ap.parse_args()
    m = MalariaThin(a.model)
    rs = [m.run_image(p, a.th, a.debug) for p in a.images]
    for r in rs:
        print(json.dumps({k: v for k, v in r.items() if k != "infected_cells"}))
    print("SLIDE:", json.dumps(aggregate(rs)))
