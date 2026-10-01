"""Validate DeepSight contract files: pack manifests, field results and case results.

Usage:  python contracts/validate.py FILE [FILE ...]
Needs:  pip install jsonschema
Exits 1 if any file is invalid.
"""
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

HERE = Path(__file__).resolve().parent
MANIFEST = json.loads((HERE / "manifest.schema.json").read_text(encoding="utf-8"))
RESULT = json.loads((HERE / "result.schema.json").read_text(encoding="utf-8"))


def result_def(name):
    # Validate against one $def directly so errors name the real problem instead of "oneOf failed".
    return {"$schema": RESULT["$schema"], "$defs": RESULT["$defs"], "$ref": f"#/$defs/{name}"}


def kind_of(doc):
    if "task_type" in doc:
        return "manifest", MANIFEST
    if "field_id" in doc:
        return "field_result", result_def("field_result")
    if "triage" in doc:
        return "case_result", result_def("case_result")
    return None, None


def manifest_problems(m):
    """Cross-field checks JSON Schema can't express. Mirrors PackManifest.problems() in :engine."""
    problems = []
    labels = set(m["output"]["labels"])
    score_label = m["output"].get("image_score_label")
    if score_label is not None and score_label not in labels:
        problems.append(f"output.image_score_label '{score_label}' is not in output.labels")
    if score_label is None and m["aggregation"]["image_score"] != "none":
        problems.append("aggregation.image_score must be 'none' when output.image_score_label is not set")
    ids = [rule["id"] for rule in m["triage"]["rules"]]
    if len(ids) != len(set(ids)):
        problems.append("triage.rules has duplicate ids")
    for rule in m["triage"]["rules"]:
        for cond in rule["all"]:
            for label in cond.get("labels", []):
                if label not in labels:
                    problems.append(f"rule {rule['id']}: unknown label '{label}'")
            if cond["metric"] == "image_score" and score_label is None:
                problems.append(f"rule {rule['id']}: image_score needs output.image_score_label")
    band = m["uncertainty"].get("band")
    if band is not None and not band[0] < band[1]:
        problems.append("uncertainty.band must be [low, high] with low < high")
    return problems


def check(path):
    doc = json.loads(Path(path).read_text(encoding="utf-8"))
    kind, schema = kind_of(doc)
    if kind is None:
        return None, ["not a manifest, field_result or case_result"]
    found = sorted(Draft202012Validator(schema).iter_errors(doc), key=lambda e: [str(p) for p in e.absolute_path])
    errors = [f"{'/'.join(map(str, e.absolute_path)) or '<root>'}: {e.message}" for e in found]
    if kind == "manifest" and not errors:
        errors = manifest_problems(doc)
    return kind, errors


def main(paths):
    if not paths:
        print(__doc__)
        return 2
    failed = 0
    for path in paths:
        kind, errors = check(path)
        if errors:
            failed += 1
            print(f"FAIL {path} ({kind or 'unknown'})")
            for err in errors:
                print(f"  {err}")
        else:
            print(f"ok   {path} ({kind})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
