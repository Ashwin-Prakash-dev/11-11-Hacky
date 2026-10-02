"""Per-cell check of the malaria pipeline against NIH ThinBloodSmearsPf annotations.

Fields: the six reserved fields in ml/fixtures/malaria_fields.json (docs/datasets.md), prepared into
ml/data/malaria_fields/<id>/ by ml/tools/prepare_malaria_fields.py (gitignored). Each annotation file lists every
red cell as Parasitized or Uninfected (plus White_Blood_Cell) with its polygon or point, in image pixels.

    python ml/eval/eval_annotated_fields.py [--data ml/data/malaria_fields] [--seg nlm] [--models a.onnx b.onnx] [--out f.md]

An annotated cell counts as found when its centroid lies inside a detected cell box, and as flagged when such a box
has P(parasitized) > threshold (0.5 = the engine's two-class argmax). A false flag is a flagged box that contains no
annotated infected centroid. These fields are reserved fixtures from 3 patients: the numbers show behaviour, not
accuracy, and NLM's training-patient overlap is UNVERIFIED (docs/datasets.md).
"""
import argparse
import csv
import json
import os
import sys
from dataclasses import dataclass

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
RBC_LABELS = ("Parasitized", "Uninfected")


@dataclass(frozen=True)
class FieldMatch:
    rbcs: int               # annotated red cells (infected + uninfected)
    infected: int           # annotated infected red cells
    cells_found: int        # boxes the segmentation produced
    infected_found: int     # annotated infected cells whose centroid lies in a box
    infected_flagged: int   # ... in a box above the threshold
    flagged: int            # boxes above the threshold
    false_flags: int        # flagged boxes containing no annotated infected centroid


def read_annotation(path):
    """((width, height), [(label, centroid xy)]) from a ThinBloodSmearsPf GT file: header 'count,width,height',
    then 'id,label,comment,Polygon|Point,n,x1,y1,...'."""
    with open(path, newline="") as f:
        rows = [r for r in csv.reader(f) if r]
    width, height = int(rows[0][1]), int(rows[0][2])
    cells = []
    for r in rows[1:]:
        n = int(r[4])
        xy = np.array([float(v) for v in r[5:5 + 2 * n]]).reshape(n, 2)
        cells.append((r[1], xy.mean(axis=0)))
    return (width, height), cells


def match_cells(boxes, probs, cells, threshold=0.5):
    """Match detected boxes (x0, y0, x1, y1) with P(parasitized) [probs] to annotated (label, centroid) cells."""
    boxes = np.asarray(boxes, dtype=float).reshape(-1, 4)
    probs = np.asarray(probs, dtype=float).reshape(-1)
    flagged = probs > threshold

    def containing(c):
        x, y = c
        return np.where((boxes[:, 0] <= x) & (x <= boxes[:, 2]) & (boxes[:, 1] <= y) & (y <= boxes[:, 3]))[0]

    infected = [c for label, c in cells if label == "Parasitized"]
    found = hit = 0
    boxes_with_infected = set()
    for c in infected:
        inside = containing(c)
        if len(inside):
            found += 1
            boxes_with_infected.update(inside.tolist())
            hit += bool(flagged[inside].any())
    return FieldMatch(
        rbcs=sum(label in RBC_LABELS for label, _ in cells),
        infected=len(infected),
        cells_found=len(boxes),
        infected_found=found,
        infected_flagged=hit,
        flagged=int(flagged.sum()),
        false_flags=len(set(np.where(flagged)[0].tolist()) - boxes_with_infected),
    )


def load_rgb(path, size):
    """The field in the orientation of its annotation: raw pixels if they match the GT header, else EXIF-rotated."""
    import cv2
    bgr = cv2.imread(path, cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
    if bgr.shape[1::-1] != size:
        bgr = cv2.imread(path)
    if bgr.shape[1::-1] != size:
        raise ValueError(f"{path}: image is {bgr.shape[1::-1]}, annotation says {size}")
    return cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)


def table(model_name, seg, rows):
    lines = [f"### `{model_name}`, `{seg}` segmentation", "",
             "| Field | Patient | Annotated RBCs | Annotated infected | Cells found | Infected found | "
             "Infected flagged | Flagged | False flags |",
             "|---|---|---:|---:|---:|---:|---:|---:|---:|"]
    for f, m in rows:
        lines.append(f"| {f['id']} | `{f['patient_id']}` | {m.rbcs} | {m.infected} | {m.cells_found} | "
                     f"{m.infected_found} | {m.infected_flagged} | {m.flagged} | {m.false_flags} |")
    infected = sum(m.infected for _, m in rows)
    uninfected = sum(m.rbcs - m.infected for _, m in rows)
    lines += ["", f"Infected cells flagged: {sum(m.infected_flagged for _, m in rows)} of {infected}. "
                  f"False flags: {sum(m.false_flags for _, m in rows)} (annotated uninfected cells: {uninfected}).", ""]
    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=os.path.join(ROOT, "ml", "data", "malaria_fields"))
    ap.add_argument("--fixtures", default=os.path.join(ROOT, "ml", "fixtures", "malaria_fields.json"))
    ap.add_argument("--models", nargs="+", default=[os.path.join(ROOT, "ml", "packs", "malaria_thin", "model.onnx")])
    ap.add_argument("--seg", default="nlm", choices=("nlm", "simple"))
    ap.add_argument("--threshold", type=float, default=0.5)
    ap.add_argument("--out")
    a = ap.parse_args()

    sys.path.insert(0, os.path.join(ROOT, "ml", "reference"))
    from malaria_pipeline import MalariaThin

    fields = json.load(open(a.fixtures))["fields"]
    lines = []
    for model_path in a.models:
        model = MalariaThin(model_path)
        rows = []
        for f in fields:
            d = os.path.join(a.data, f["id"])
            size, cells = read_annotation(os.path.join(d, f["annotation"]["filename"]))
            out = model.cells(load_rgb(os.path.join(d, f["image"]["filename"]), size), a.seg)
            boxes, probs = out if out is not None else ([], [])
            rows.append((f, match_cells(boxes, probs, cells, a.threshold)))
            print(f["id"], rows[-1][1], flush=True)
        lines += table(os.path.basename(model_path), a.seg, rows)
    report = "\n".join(lines)
    print("\n" + report)
    if a.out:
        with open(a.out, "w", encoding="utf-8") as fh:
            fh.write(report)


if __name__ == "__main__":
    main()
