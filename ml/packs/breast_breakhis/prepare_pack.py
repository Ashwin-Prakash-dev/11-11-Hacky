"""Build this pack's runtime files from the upstream ONNX export, and regenerate the golden cases.

    python ml/packs/breast_breakhis/prepare_pack.py [--source ml/models/breakhis_densenet121_original.onnx]

1. Checks the upstream export's SHA-256 (README, Model contract) and strips its final Softmax (ml/tools/onnx_logits.py),
   because the engine's ClassifierDecoder applies softmax itself: writes model_logits.onnx.
2. Writes that file's SHA-256 into manifest.json (model.sha256).
3. For every golden/<name>.png runs the reference pipeline (reference/pipeline.py) and the logits model and writes
   golden/<name>.json (a contract field_result, what PackGoldenTest compares) and golden/expected.json (the case list).

The upstream export is not in the pack: it would add 28 MB to the APK. Keep it at the --source path (git-ignored).
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

import cv2
import numpy as np
import onnx
import onnxruntime as ort

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "ml" / "tools"))

from onnx_logits import strip_final_softmax  # noqa: E402
from ml.packs.breast_breakhis.reference.pipeline import field_result, preprocess  # noqa: E402
from ml.packs.breast_breakhis.reference.eval_breakhis import probabilities_from_logits  # noqa: E402

UPSTREAM_SHA256 = "0a6274f45f33a644b5ba11fe852bcc30a8ec7beb018f02a262564d81fa6573c0"
DEFAULT_SOURCE = ROOT / "ml" / "models" / "breakhis_densenet121_original.onnx"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def label_of(name: str) -> str:
    """BreakHis file names start SOB_B_ (benign) or SOB_M_ (malignant). For the README only: results use predictions."""
    return {"B": "benign", "M": "malignant"}[name.split("_")[1]]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    a = ap.parse_args()

    if sha256(a.source) != UPSTREAM_SHA256:
        sys.exit(f"{a.source} is not the upstream export (sha256 {UPSTREAM_SHA256}); see README, Source.")
    logits_path = HERE / "model_logits.onnx"
    onnx.save(strip_final_softmax(onnx.load(str(a.source))), str(logits_path))

    manifest_path = HERE / "manifest.json"
    text = manifest_path.read_text(encoding="utf-8")
    manifest = json.loads(text)
    manifest["model"]["sha256"] = sha256(logits_path)
    newline = "\r\n" if "\r\n" in text else "\n"
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False).replace("\n", newline) + newline, encoding="utf-8")

    session = ort.InferenceSession(str(logits_path), providers=["CPUExecutionProvider"])
    golden = HERE / "golden"
    cases = []
    for png in sorted(golden.glob("*.png")):
        rgb = cv2.cvtColor(cv2.imread(str(png), cv2.IMREAD_COLOR), cv2.COLOR_BGR2RGB)
        logits = session.run(None, {"input": preprocess(rgb)})[0]
        probs = probabilities_from_logits(logits)[0]
        result = field_result(rgb, probs, manifest, png.stem)
        (golden / f"{png.stem}.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        cases.append({"file": png.name, "label": label_of(png.stem), "p_benign": float(probs[0]), "p_malignant": float(probs[1])})
        print(f"{png.name}: p_malignant {probs[1]:.6f} ({label_of(png.stem)})")
    expected = {
        "model": "model_logits.onnx",
        "input": "NCHW float32 RGB /255; whole field stretched to 224x224 with bilinear half-pixel sampling, no anti-aliasing "
                 "(reference/pipeline.py preprocess). Scores are softmax of the logits.",
        "tolerance_score": 0.02,
        "cases": cases,
    }
    (golden / "expected.json").write_text(json.dumps(expected, indent=1) + "\n", encoding="utf-8")
    print(f"model_logits.onnx sha256 {manifest['model']['sha256']}")


if __name__ == "__main__":
    main()
