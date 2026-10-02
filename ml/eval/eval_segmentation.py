"""Compare cell segmentations on RBCNet field photos: cells per image and % of cells flagged.

Data: RBCNet Data/ (https://github.com/nlm-malaria/RBCNet, commit b98941d71de3cb3a42a25714d151e0e7ab9c35c5),
4 fields each from 239C12NThinF (negative) and 234C92P53ThinF (positive), downloaded to ml/data/rbcnet/<patient>/
(gitignored). NLM data, BSD-style notice; see LICENSING.md.

    python ml/eval/eval_segmentation.py [--data ml/data/rbcnet] [--out ml/data/rbcnet_eval] [--models a.onnx b.onnx]

Writes <out>/results.md, <out>/per_image.csv and, for the first model, <out>/overlays/<patient>_<image>.jpg:
simple | nlm side by side, boxes green p <= 0.5, orange 0.5 < p <= 0.8, red p > 0.8.
"""
import argparse
import csv
import glob
import os
import sys
import time

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "reference"))
from malaria_pipeline import MalariaThin  # noqa: E402

PATIENTS = {"239C12NThinF": "negative", "234C92P53ThinF": "positive"}
SEGS = ("simple", "nlm")
THS = (0.5, 0.8)
COLORS = {"low": (0, 200, 0), "mid": (0, 165, 255), "high": (0, 0, 255)}   # BGR


def draw(bgr, boxes, p, title):
    vis = bgr.copy()
    for (x0, y0, x1, y1), pi in zip(boxes, p):
        color = COLORS["high"] if pi > 0.8 else COLORS["mid"] if pi > 0.5 else COLORS["low"]
        cv2.rectangle(vis, (x0, y0), (x1, y1), color, 8 if pi > 0.5 else 3)
    vis = cv2.resize(vis, (vis.shape[1] // 4, vis.shape[0] // 4), interpolation=cv2.INTER_AREA)
    cv2.rectangle(vis, (0, 0), (vis.shape[1], 44), (255, 255, 255), -1)
    cv2.putText(vis, title, (10, 31), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 0, 0), 2, cv2.LINE_AA)
    return vis


def pct(n, d):
    return 100.0 * n / d if d else float("nan")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=os.path.join(HERE, "..", "data", "rbcnet"))
    ap.add_argument("--out", default=os.path.join(HERE, "..", "data", "rbcnet_eval"))
    ap.add_argument("--models", nargs="+", default=[os.path.join(HERE, "..", "packs", "malaria_thin", "model.onnx")])
    a = ap.parse_args()
    os.makedirs(os.path.join(a.out, "overlays"), exist_ok=True)

    rows = []
    for mi, model_path in enumerate(a.models):
        model = MalariaThin(model_path)
        model_name = os.path.basename(model_path)
        for patient, truth in PATIENTS.items():
            for path in sorted(glob.glob(os.path.join(a.data, patient, "*.jpg"))):
                bgr = cv2.imread(path)
                rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
                panels = []
                for seg in SEGS:
                    t0 = time.perf_counter()
                    out = model.cells(rgb, seg)
                    seconds = time.perf_counter() - t0
                    boxes, p = out if out is not None else ([], np.zeros(0))
                    row = {"model": model_name, "patient": patient, "truth": truth,
                           "image": os.path.basename(path), "seg": seg, "retake": out is None,
                           "cells": len(boxes), "seconds": round(seconds, 2)}
                    row.update({f"flag_{th}": int((p > th).sum()) for th in THS})
                    rows.append(row)
                    print(model_name, patient, row["image"], seg, row["cells"], row["flag_0.5"], row["flag_0.8"], flush=True)
                    if mi == 0:
                        panels.append(draw(bgr, boxes, p, f"{seg}: {len(boxes)} cells, "
                                                          f"{pct(row['flag_0.5'], len(boxes)):.1f}% >0.5, "
                                                          f"{pct(row['flag_0.8'], len(boxes)):.1f}% >0.8"))
                if mi == 0:
                    name = f"{patient}_{os.path.splitext(os.path.basename(path))[0]}.jpg"
                    cv2.imwrite(os.path.join(a.out, "overlays", name), np.hstack(panels), [cv2.IMWRITE_JPEG_QUALITY, 85])

    with open(os.path.join(a.out, "per_image.csv"), "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)

    lines = ["# Segmentation comparison on RBCNet field photos", "",
             "Flagged = P(parasitized) above the threshold. Truth is the patient-level label.", ""]
    for model_name in dict.fromkeys(r["model"] for r in rows):
        lines += [f"## Model `{model_name}`", "",
                  "| Patient | Truth | Segmentation | Images | Cells/image | % flagged p>0.5 | % flagged p>0.8 |",
                  "|---|---|---|---|---|---|---|"]
        for patient, truth in PATIENTS.items():
            for seg in SEGS:
                rs = [r for r in rows if r["model"] == model_name and r["patient"] == patient and r["seg"] == seg]
                cells = sum(r["cells"] for r in rs)
                lines.append(f"| {patient} | {truth} | {seg} | {len(rs)} | {cells / len(rs):.0f} | "
                             f"{pct(sum(r['flag_0.5'] for r in rs), cells):.1f} | "
                             f"{pct(sum(r['flag_0.8'] for r in rs), cells):.1f} |")
        lines.append("")
    lines += ["## Per image", "", "| Model | Patient | Image | Segmentation | Cells | % >0.5 | % >0.8 | Seconds |",
              "|---|---|---|---|---|---|---|---|"]
    lines += [f"| {r['model']} | {r['patient']} | {r['image']} | {r['seg']} | {r['cells']} | "
              f"{pct(r['flag_0.5'], r['cells']):.1f} | {pct(r['flag_0.8'], r['cells']):.1f} | {r['seconds']} |" for r in rows]
    with open(os.path.join(a.out, "results.md"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
