"""Desktop golden test for the malaria_thin pack. The on-device port is engine's MalariaPackGoldenTest.

The model outputs logits (its final Softmax was removed, see the pack README); expected.json holds
probabilities, so outputs are softmaxed before comparing.
Test A (model only): feed input_32x44x44x3_float32.bin (raw float32, NHWC, little-endian)
        -> softmax(outputs) must match expected.json within 1e-4.
Test B (preprocessing + model): load chips/*.png, resize with OpenCV INTER_CUBIC
        -> p_infected must match within 0.07. On Android, port INTER_CUBIC: bilinear
        createScaledBitmap was measured 0.149 off on the demo phone.
"""
import json, os, sys
import numpy as np, cv2, onnxruntime as ort

here = os.path.dirname(os.path.abspath(__file__))
exp = json.load(open(os.path.join(here, "expected.json")))
manifest = json.load(open(os.path.join(here, "..", "manifest.json")))
s = ort.InferenceSession(os.path.join(here, "..", manifest["model"]["file"]))


def probs(x):
    logits = s.run(["logits"], {"input": x})[0]
    e = np.exp(logits - logits.max(axis=1, keepdims=True))
    return e / e.sum(axis=1, keepdims=True)


X = np.fromfile(os.path.join(here, "input_32x44x44x3_float32.bin"), dtype="<f4").reshape(32, 44, 44, 3)
p = probs(X)
ref = np.array([[c["p_infected"], c["p_uninfected"]] for c in exp["cases"]])
errA = np.abs(p - ref).max()
print(f"Test A (tensor -> model): max abs err {errA:.2e}  {'PASS' if errA < exp['tolerance_tensor'] else 'FAIL'}")

errB = 0
for i, c in enumerate(exp["cases"]):
    rgb = cv2.cvtColor(cv2.imread(os.path.join(here, "chips", c["file"])), cv2.COLOR_BGR2RGB)
    x = cv2.resize(rgb, (44, 44), interpolation=cv2.INTER_CUBIC).astype(np.float32)[None] / 255.0
    errB = max(errB, abs(probs(x)[0, 0] - c["p_infected"]))
print(f"Test B (png -> preprocess -> model): max abs err {errB:.2e}  {'PASS' if errB < 0.07 else 'FAIL'}")
sys.exit(0 if errA < exp["tolerance_tensor"] and errB < 0.07 else 1)
