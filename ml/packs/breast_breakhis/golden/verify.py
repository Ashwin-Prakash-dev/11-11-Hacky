"""Parity test. Test A: raw tensor -> model, tol 1e-4. Test B: PNG -> preprocessing -> model, tol 0.02."""
import json, os, sys, numpy as np, onnxruntime as ort
from PIL import Image
h = os.path.dirname(os.path.abspath(__file__)); e = json.load(open(os.path.join(h, "expected.json")))
s = ort.InferenceSession(os.path.join(h, "..", e["model"]))
ref = np.array([[c["p_benign"], c["p_malignant"]] for c in e["cases"]])
X = np.fromfile(os.path.join(h, "input_6x3x224x224_float32.bin"), "<f4").reshape(-1, 3, 224, 224)
a = np.abs(s.run(None, {"input": X})[0] - ref).max()
b = 0
for i, c in enumerate(e["cases"]):
    x = np.asarray(Image.open(os.path.join(h, "images", c["file"])).convert("RGB").resize((224, 224), Image.BILINEAR), np.float32).transpose(2, 0, 1)[None] / 255
    b = max(b, abs(s.run(None, {"input": x})[0][0, 1] - c["p_malignant"]))
print(f"Test A {a:.2e} {'PASS' if a < 1e-4 else 'FAIL'} | Test B {b:.2e} {'PASS' if b < 0.02 else 'FAIL'}")
sys.exit(0 if a < 1e-4 and b < 0.02 else 1)
