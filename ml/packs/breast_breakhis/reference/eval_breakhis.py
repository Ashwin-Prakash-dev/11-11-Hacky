"""Reproduce the README metrics on BreakHis 400x with the pack's own model and preprocessing.

Data: git clone --depth 1 https://github.com/PerceptiLabs/breakhis-400x   (benign/ and malignant/ folders of PNGs)
Usage: python ml/packs/breast_breakhis/reference/eval_breakhis.py breakhis-400x/data [--pillow]

By default the field is resized the way the app does it (`pipeline.preprocess`). `--pillow` resizes with Pillow's
anti-aliasing bilinear, the way the numbers in older notes were measured, to show the difference.
"""
import glob
import os
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))


def label_from_path(path) -> int:
    """0 = benign, 1 = malignant, from the directory components only (either path style); the file name is ignored."""
    parts = [p.lower() for p in str(path).replace("\\", "/").split("/")[:-1]]
    found = {p for p in parts if p in ("benign", "malignant")}
    if len(found) != 1:
        raise ValueError(f"cannot read a single benign/malignant label from the directories of {path!s}")
    return 1 if found == {"malignant"} else 0


def probabilities_from_logits(logits) -> np.ndarray:
    """Softmax over [N, 2] logits, once and numerically stable (the pack model outputs logits)."""
    x = np.asarray(logits, dtype=np.float64)
    if x.ndim != 2 or x.shape[0] == 0 or x.shape[1] != 2:
        raise ValueError(f"expected logits of shape [N, 2], got {x.shape}")
    if not np.isfinite(x).all():
        raise ValueError("logits must be finite")
    e = np.exp(x - x.max(axis=1, keepdims=True))
    return e / e.sum(axis=1, keepdims=True)


def binary_metrics(labels, scores) -> dict:
    """Accuracy, sensitivity, specificity at the strict threshold score > 0.5, and AUC with tie-averaged ranks."""
    y = np.asarray(labels)
    s = np.asarray(scores, dtype=np.float64)
    if y.ndim != 1 or s.ndim != 1 or len(y) != len(s):
        raise ValueError("labels and scores must be 1-D and the same length")
    if len(y) == 0:
        raise ValueError("no samples")
    if not np.isin(y, (0, 1)).all():
        raise ValueError("labels must be 0 or 1")
    if not np.isfinite(s).all() or ((s < 0) | (s > 1)).any():
        raise ValueError("scores must be finite probabilities in [0, 1]")
    y = y.astype(int)
    positives, negatives = int(y.sum()), int((1 - y).sum())
    if positives == 0 or negatives == 0:
        raise ValueError("need both classes to compute sensitivity, specificity and AUC")
    predicted = s > 0.5
    order = np.argsort(s, kind="mergesort")
    ranks = np.empty(len(s))
    i = 0
    while i < len(s):                                   # average the ranks of tied scores
        j = i
        while j + 1 < len(s) and s[order[j + 1]] == s[order[i]]:
            j += 1
        ranks[order[i:j + 1]] = (i + j) / 2 + 1
        i = j + 1
    auc = (ranks[y == 1].sum() - positives * (positives + 1) / 2) / (positives * negatives)
    return {
        "n": len(y),
        "accuracy": float((predicted == (y == 1)).mean()),
        "sensitivity": float((predicted & (y == 1)).sum() / positives),
        "specificity": float((~predicted & (y == 0)).sum() / negatives),
        "auc": float(auc),
    }


def main(argv):
    import cv2
    import onnxruntime as ort
    from pipeline import preprocess

    use_pillow = "--pillow" in argv
    args = [a for a in argv if a != "--pillow"]
    if len(args) != 1:
        sys.exit(__doc__)
    files = sorted(glob.glob(os.path.join(args[0], "*", "*", "*.png")))
    session = ort.InferenceSession(str(HERE.parent / "model_logits.onnx"))
    if use_pillow:
        from PIL import Image

    def tensor(path):
        if use_pillow:
            return np.asarray(Image.open(path).convert("RGB").resize((224, 224), Image.BILINEAR), np.float32).transpose(2, 0, 1)[None] / 255
        return preprocess(cv2.cvtColor(cv2.imread(path, cv2.IMREAD_COLOR), cv2.COLOR_BGR2RGB))

    labels = [label_from_path(f) for f in files]
    scores = []
    for i in range(0, len(files), 32):
        batch = np.concatenate([tensor(f) for f in files[i:i + 32]])
        scores.extend(probabilities_from_logits(session.run(None, {"input": batch})[0])[:, 1])
    m = binary_metrics(labels, scores)
    print(f"{'pillow' if use_pillow else 'app'} preprocessing: n={m['n']} acc={m['accuracy']:.4f} "
          f"sens={m['sensitivity']:.4f} spec={m['specificity']:.4f} AUC={m['auc']:.4f}")


if __name__ == "__main__":
    main(sys.argv[1:])
