"""Desktop reference for the breast pack's whole-field pipeline: what the engine must reproduce on the phone.

It mirrors the Kotlin engine on purpose (numpy only, no OpenCV or Pillow, so any CI job can import it):
- `preprocess`: Preprocessor's STRETCH resize: bilinear on half-pixel centres, edge-clamped, no anti-aliasing, in float64
  with no uint8 rounding, then pixel/255. Pillow's `resize` anti-aliases when shrinking and gives slightly different
  tensors, so numbers measured with Pillow do not carry over to the app (see the pack README).
- `quality`: QualityGate's rounded luminance, 3x3 Laplacian variance over interior pixels, and dark/bright clipping.
- `field_result`: the contract `field_result` (contracts/result.schema.json) for a one-object whole-field classifier.
"""
import numpy as np

LABELS = ("benign", "malignant")
IMAGE_SCORE_LABEL = "malignant"


def preprocess(rgb, width=224, height=224):
    """RGB uint8 [H, W, 3] -> float32 [1, 3, height, width] in 0..1."""
    rgb = np.asarray(rgb)
    if rgb.ndim != 3 or rgb.shape[2] != 3:
        raise ValueError(f"expected an RGB image [H, W, 3], got shape {rgb.shape}")
    h, w = rgb.shape[:2]
    src = rgb.astype(np.float64)

    def axis(out_size, in_size):
        pos = (np.arange(out_size) + 0.5) * in_size / out_size - 0.5
        lo = np.floor(pos).astype(np.int64)
        frac = pos - lo
        return np.clip(lo, 0, in_size - 1), np.clip(lo + 1, 0, in_size - 1), frac

    y0, y1, fy = axis(height, h)
    x0, x1, fx = axis(width, w)
    rows = src[y0] * (1 - fy)[:, None, None] + src[y1] * fy[:, None, None]
    out = rows[:, x0] * (1 - fx)[None, :, None] + rows[:, x1] * fx[None, :, None]
    return (out.transpose(2, 0, 1)[None] / 255.0).astype(np.float32)


def quality(rgb, spec):
    """QualityGate: {pass, blur_score, exposure_score, reasons} for `spec` = {min_blur, max_clipped_fraction}."""
    rgb = np.asarray(rgb).astype(np.int64)
    lum = (299 * rgb[..., 0] + 587 * rgb[..., 1] + 114 * rgb[..., 2] + 500) // 1000
    if lum.shape[0] < 3 or lum.shape[1] < 3:
        blur = 0.0
    else:
        c = lum[1:-1, 1:-1]
        lap = lum[:-2, 1:-1] + lum[1:-1, :-2] - 4 * c + lum[1:-1, 2:] + lum[2:, 1:-1]
        blur = float(np.var(lap.astype(np.float64)))
    dark = float(np.mean(lum == 0))
    bright = float(np.mean(lum == 255))
    reasons = []
    if blur < spec["min_blur"]:
        reasons.append("blur")
    if dark > spec["max_clipped_fraction"]:
        reasons.append("underexposed")
    if bright > spec["max_clipped_fraction"]:
        reasons.append("overexposed")
    return {"pass": not reasons, "blur_score": blur, "exposure_score": max(dark, bright), "reasons": reasons}


def field_result(rgb, probs, manifest, name, case_id="golden"):
    """Contract field_result for one whole field. `probs` = softmax output [p_benign, p_malignant].

    The label comes from the prediction (argmax), never from the dataset's folder name. A quality reject stops there,
    as FieldPipeline does: no router, objects, counts, score or uncertainty.
    """
    q = quality(rgb, manifest["quality"])
    result = {
        "case_id": case_id, "field_id": name, "pack_id": manifest["id"], "pack_version": manifest["version"],
        "quality": q, "router": None, "objects": [], "counts": {}, "image_score": None, "uncertainty": None,
    }
    if not q["pass"]:
        return result
    probs = [float(p) for p in probs]
    top = int(np.argmax(probs))
    result["router"] = {"verdict": "match", "score": 1.0}     # AlwaysMatchRouterGuard until the router lands (#22)
    result["objects"] = [{"label": LABELS[top], "score": probs[top], "bbox": None}]
    result["counts"] = {label: int(i == top) for i, label in enumerate(LABELS)}
    result["image_score"] = probs[LABELS.index(IMAGE_SCORE_LABEL)]
    result["uncertainty"] = {"flag": False, "reason": None}   # manifest uncertainty.method is "none"
    return result
