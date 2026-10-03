"""Desktop parity: golden PNG -> reference preprocessing -> logits model -> softmax must give each case's committed
`image_score` (golden/<name>.json) within 1e-4. The same cases run on the phone through the real pipeline in
PackGoldenTest (scores within 0.02); this checks the files agree with each other on the desktop first."""
import json
import sys
from pathlib import Path

import cv2
import onnxruntime as ort

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[3]))

from ml.packs.breast_breakhis.reference.eval_breakhis import probabilities_from_logits  # noqa: E402
from ml.packs.breast_breakhis.reference.pipeline import preprocess  # noqa: E402

expected = json.loads((HERE / "expected.json").read_text())
session = ort.InferenceSession(str(HERE.parent / expected["model"]), providers=["CPUExecutionProvider"])
worst = 0.0
for case in expected["cases"]:
    rgb = cv2.cvtColor(cv2.imread(str(HERE / case["file"]), cv2.IMREAD_COLOR), cv2.COLOR_BGR2RGB)
    score = probabilities_from_logits(session.run(None, {"input": preprocess(rgb)})[0])[0, 1]
    committed = json.loads((HERE / case["file"]).with_suffix(".json").read_text())["image_score"]
    worst = max(worst, abs(score - committed))
print(f"worst |score - committed image_score| = {worst:.2e} {'PASS' if worst < 1e-4 else 'FAIL'}")
sys.exit(0 if worst < 1e-4 else 1)
