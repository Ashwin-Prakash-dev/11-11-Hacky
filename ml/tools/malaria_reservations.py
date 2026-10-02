"""Reject reserved issue #6 patients before constructing training/evaluation splits."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Iterable, Mapping

SELECTION = Path(__file__).resolve().parents[1] / "fixtures" / "malaria_fields.json"
# Source directories add an acquisition prefix (143); cell crops omit it.
# The common patient key is C39P4, C13N, C70P31, etc., not the image timestamp.
PATIENT = re.compile(r"(?<![A-Za-z])C\d+(?:[A-Z]*P\d+)?N?", re.IGNORECASE)


def patient_keys(value: str) -> set[str]:
    return {match.group().upper() for match in PATIENT.finditer(value)}


def assert_no_reserved_patients(
    files: Iterable[Mapping[str, str]], selection_path: Path = SELECTION,
) -> None:
    doc = json.loads(selection_path.read_text(encoding="utf-8"))
    reserved = set(doc["reserved_patients"])
    field_ids = {field["field_id"] for field in doc["fields"]}
    for row in files:
        path = row["path"]
        evidence = " ".join(str(row.get(key, "")) for key in ("path", "patient_id", "field_id"))
        keys = patient_keys(evidence)
        if keys & reserved or any(field_id in evidence for field_id in field_ids):
            raise ValueError(f"reserved malaria patient/field in split: {path}")
        source = str(row.get("source", "")).lower()
        provenance = f"{source} {path.lower()}"
        is_malaria_source = (
            any(hint in provenance for hint in ("cell_images", "thinbloodsmears"))
            or source in {"nih-nlm", "nlm"}
            or (any(hint in provenance for hint in ("nlm", "nih"))
                and ("malaria" in provenance or row.get("class") == "malaria_thin"))
        )
        if is_malaria_source and not keys:
            raise ValueError(f"NLM patient provenance required for split file: {path}")


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("split", type=Path, help="JSON object with a files array (including patient_id for renamed NLM files)")
    args = parser.parse_args()
    doc = json.loads(args.split.read_text(encoding="utf-8"))
    assert_no_reserved_patients(doc["files"])
    print(f"PASS: {len(doc['files'])} split entries exclude reserved patients")


if __name__ == "__main__":
    main()
