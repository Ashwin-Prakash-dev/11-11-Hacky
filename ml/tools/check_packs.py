"""Check that every pack model and optional detector exist and match their declared SHA-256.

A pack whose weights are deliberately kept out of git (licence pending) carries a WEIGHTS_NOT_IN_GIT file saying why;
its missing model is not a problem, but a model that is present is still hash-checked.
"""
import hashlib
import json
import sys
from pathlib import Path


def pack_problems(packs_dir: Path) -> list[str]:
    problems = []
    for manifest in sorted(Path(packs_dir).glob("*/manifest.json")):
        model = json.loads(manifest.read_text())["model"]
        if "file" not in model:  # hub packs have an endpoint instead of weights
            continue
        path = manifest.parent / model["file"]
        if not path.is_file():
            if not (manifest.parent / "WEIGHTS_NOT_IN_GIT").is_file():
                problems.append(f"{manifest.parent.name}: model file {model['file']} is missing")
        elif hashlib.sha256(path.read_bytes()).hexdigest() != model["sha256"]:
            problems.append(f"{manifest.parent.name}: {model['file']} does not match the manifest sha256")
        detector_spec = manifest.parent / "detector.json"
        if detector_spec.is_file():
            detector = json.loads(detector_spec.read_text())
            detector_path = manifest.parent / detector["file"]
            if not detector_path.is_file():
                problems.append(f"{manifest.parent.name}: detector file {detector['file']} is missing")
            elif hashlib.sha256(detector_path.read_bytes()).hexdigest() != detector["sha256"]:
                problems.append(f"{manifest.parent.name}: {detector['file']} does not match detector.json sha256")
    return problems


if __name__ == "__main__":
    found = pack_problems(Path(__file__).resolve().parents[1] / "packs")
    print("\n".join(found) or "ok: all pack models match their manifests")
    sys.exit(1 if found else 0)
