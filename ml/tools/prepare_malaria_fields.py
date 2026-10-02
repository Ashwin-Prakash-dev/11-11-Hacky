"""Fetch checksum-pinned issue #6 fields; preserve annotations and generate failure demos."""
from __future__ import annotations

import argparse
import csv
import json
import math
import re
import shutil
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.tools.download_models import ModelArtifact, download_artifact, sha256_file
from ml.tools.malaria_reservations import SELECTION, patient_keys

LABELS = {
    "Parasitized", "Uninfected", "White_Blood_Cell", "Parasite_Outside_Cell",
    "Dead_Parasite", "Gametocyte", "Debris", "Stain_Precipitation", "Bacteria",
    "Platelet", "Air_Bubble", "Other", "Unclear",
}


@dataclass(frozen=True)
class AnnotationCounts:
    width: int
    height: int
    counts: dict[str, int]

    @property
    def rbc_count(self) -> int:
        return self.counts.get("Parasitized", 0) + self.counts.get("Uninfected", 0)


def parse_annotation(path: Path) -> AnnotationCounts:
    """Count complete GT records by their exact label; never infer individual parasite counts."""
    with path.open(encoding="utf-8-sig", newline="") as stream:
        rows = [row for row in csv.reader(stream) if row]
    if not rows or len(rows[0]) != 3:
        raise ValueError(f"invalid annotation header: {path}")
    total, width, height = map(int, rows[0])
    if total < 0 or width <= 0 or height <= 0 or total != len(rows) - 1:
        raise ValueError(f"incomplete annotation/header: {path}")
    ids: set[str] = set()
    counts: Counter[str] = Counter()
    for row in rows[1:]:
        if len(row) < 7 or row[0] in ids or row[1] not in LABELS:
            raise ValueError(f"invalid/duplicate annotation record: {path}")
        points = int(row[4])
        if row[3] not in {"Point", "Polygon"} or points < 1 or len(row) != 5 + 2 * points:
            raise ValueError(f"invalid annotation geometry: {path}")
        if (row[3] == "Point" and points != 1) or (row[3] == "Polygon" and points < 3):
            raise ValueError(f"invalid point count: {path}")
        if not all(math.isfinite(float(value)) for value in row[5:]):
            raise ValueError(f"nonfinite annotation coordinate: {path}")
        ids.add(row[0])
        counts[row[1]] += 1
    return AnnotationCounts(width, height, dict(counts))


@dataclass(frozen=True)
class Asset:
    filename: str
    url: str
    sha256: str

    @classmethod
    def read(cls, raw: dict) -> Asset:
        asset = cls(**raw)
        if not re.fullmatch(r"[A-Za-z0-9_. -]+", asset.filename) or asset.filename in {".", ".."}:
            raise ValueError("asset filename must be a safe basename")
        if not re.fullmatch(r"[0-9a-f]{64}", asset.sha256):
            raise ValueError("asset sha256 must be pinned")
        if not asset.url.startswith(("https://", "file://")):
            raise ValueError("asset URL must be https (file URLs are for tests/offline mirrors)")
        return asset

    def fetch(self, output: Path) -> Path:
        return download_artifact(ModelArtifact(self.filename, self.filename, self.url, self.sha256, "approved"), output)


@dataclass(frozen=True)
class Field:
    id: str
    role: str
    category: str
    patient_id: str
    field_id: str
    image: Asset
    annotation: Asset
    counts: dict[str, int]
    width: int
    height: int


@dataclass(frozen=True)
class Selection:
    fields: tuple[Field, ...]
    notices: tuple[Asset, ...]
    variants: tuple[dict, ...]


def load_selection(path: Path = SELECTION) -> Selection:
    doc = json.loads(path.read_text(encoding="utf-8"))
    if doc.get("schema_version") != 1:
        raise ValueError("selection schema_version must be 1")
    fields = tuple(Field(**{**raw, "image": Asset.read(raw["image"]), "annotation": Asset.read(raw["annotation"])})
                   for raw in doc["fields"])
    notices = tuple(Asset.read(raw) for raw in doc["notices"])
    variants = tuple(doc["variants"])
    ids = [field.id for field in fields] + [variant["id"] for variant in variants]
    if not fields or len(set(ids)) != len(ids) or any(not re.fullmatch(r"[a-z][a-z0-9_]*", value) for value in ids):
        raise ValueError("selection IDs must be unique safe directory names")
    reserved = set(doc["reserved_patients"])
    for field in fields:
        keys = patient_keys(field.patient_id)
        if len(keys) != 1 or not keys <= reserved:
            raise ValueError(f"field patient must be reserved: {field.id}")
        if field.role not in {"golden", "demo"} or field.category not in {"positive", "negative", "sparse"}:
            raise ValueError(f"invalid role/category: {field.id}")
        if field.width <= 0 or field.height <= 0 or not field.counts:
            raise ValueError(f"missing dimensions/counts: {field.id}")
        if any(label not in LABELS or type(count) is not int or count < 0 for label, count in field.counts.items()):
            raise ValueError(f"invalid counts: {field.id}")
        if field.category == "negative" and field.counts.get("Parasitized", 0) != 0:
            raise ValueError(f"negative field has parasitized annotations: {field.id}")
        if field.category == "positive" and field.counts.get("Parasitized", 0) <= 0:
            raise ValueError(f"positive field lacks parasitized annotations: {field.id}")
    for variant in variants:
        if variant["source_id"] not in {field.id for field in fields if field.role == "demo"}:
            raise ValueError("failure variants must derive from a reserved demo field")
    return Selection(fields, notices, variants)


def generate_failure(image, spec: dict):
    """OpenCV handles filtering; promote to float before clipping exposure to uint8."""
    import cv2
    import numpy as np
    if spec["kind"] == "gaussian_blur":
        kernel, sigma = spec["kernel"], spec["sigma"]
        if type(kernel) is not int or kernel <= 0 or kernel % 2 != 1 or not math.isfinite(sigma) or sigma <= 0:
            raise ValueError("blur requires an odd positive kernel and finite positive sigma")
        return cv2.GaussianBlur(image, (kernel, kernel), sigmaX=sigma, sigmaY=sigma, borderType=cv2.BORDER_REFLECT_101)
    if spec["kind"] == "overexpose":
        gain, offset = spec["gain"], spec["offset"]
        if not math.isfinite(gain) or gain <= 0 or not math.isfinite(offset) or offset < 0:
            raise ValueError("exposure requires finite positive gain and nonnegative offset")
        return np.clip(image.astype(np.float32) * gain + offset, 0, 255).astype(np.uint8)
    raise ValueError(f"unknown failure kind: {spec['kind']}")


def prepare(manifest: Path, output: Path) -> None:
    import cv2
    selection = load_selection(manifest)
    output.mkdir(parents=True, exist_ok=True)
    for notice in selection.notices:
        notice.fetch(output)
    notice_text = SELECTION.parent / "NOTICE_NLM_THIN_FIELDS.txt"
    if notice_text.exists():
        shutil.copyfile(notice_text, output / notice_text.name)
    paths: dict[str, Path] = {}
    for field in selection.fields:
        directory = output / field.id
        image_path = field.image.fetch(directory)
        annotation_path = field.annotation.fetch(directory)
        gt = parse_annotation(annotation_path)
        if gt.counts != field.counts or (gt.width, gt.height) != (field.width, field.height):
            raise ValueError(f"annotation dimensions/counts differ from pinned metadata: {field.id}")
        raw = cv2.imread(str(image_path), cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
        if raw is None or raw.shape[:2] != (field.height, field.width):
            raise ValueError(f"image dimensions differ from annotation: {field.id}")
        paths[field.id] = image_path
        print(f"{field.id}: {gt.rbc_count} RBCs; {gt.counts.get('Parasitized', 0)} parasitized RBCs")
    generated = []
    for variant in selection.variants:
        # Apply source EXIF orientation once, then write a PNG without EXIF.
        image = cv2.imread(str(paths[variant["source_id"]]))
        result = generate_failure(image, variant)
        path = output / variant["id"] / "field.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        if not cv2.imwrite(str(path), result, [cv2.IMWRITE_PNG_COMPRESSION, 9]):
            raise RuntimeError(f"could not write failure variant: {path}")
        generated.append({**variant, "sha256": sha256_file(path), "width": result.shape[1], "height": result.shape[0]})
    (output / "prepared.json").write_text(json.dumps({"selection_sha256": sha256_file(manifest), "variants": generated}, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=SELECTION)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "ml/data/malaria_fields")
    args = parser.parse_args()
    prepare(args.manifest, args.output_dir)


if __name__ == "__main__":
    main()
