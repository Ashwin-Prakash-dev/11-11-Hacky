"""S2 smoke model: a small CNN shaped like a 64x64 cell-crop classifier, plus golden outputs for the phone.

Writes android/engine/src/androidTest/assets/smoke/{model.onnx,expected.json}; OnnxSmokeTest checks the
phone's ONNX Runtime output against expected.json. Weights are random (seeded): this tests the runtime only.

Run: python ml/eval/make_smoke_model.py   (needs torch, numpy, onnxruntime)
"""
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
from torch import nn

OUT = Path(__file__).resolve().parents[2] / "android/engine/src/androidTest/assets/smoke"
OPSET = 17
BATCH = 4


class SmokeCnn(nn.Module):
    # About 22 MFLOPs per 64x64 crop.
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            nn.Conv2d(3, 16, 3, padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(16, 32, 3, padding=1), nn.ReLU(), nn.MaxPool2d(2),
            nn.Conv2d(32, 64, 3, padding=1), nn.ReLU(), nn.AdaptiveAvgPool2d(1),
        )
        self.head = nn.Linear(64, 2)

    def forward(self, x):
        return torch.softmax(self.head(torch.flatten(self.features(x), 1)), dim=1)


def smoke_input(batch):
    """Same formula as OnnxSmokeTest.smokeInput: an integer divided once, so float32-exact on both sides."""
    per = 3 * 64 * 64
    i = np.arange(batch * per, dtype=np.int64)
    k = (i * 7919) % 256
    return ((k * (i // per + 1)).astype(np.float32) / np.float32(1020)).reshape(batch, 3, 64, 64)


def main():
    torch.manual_seed(0)
    model = SmokeCnn().eval()
    with torch.no_grad():
        model.head.weight.mul_(80)  # spread the logits so a broken runtime can't pass by printing ~0.5
    OUT.mkdir(parents=True, exist_ok=True)
    onnx_path = OUT / "model.onnx"
    torch.onnx.export(
        model, (torch.zeros(1, 3, 64, 64),), str(onnx_path),
        input_names=["input"], output_names=["probs"],
        dynamic_axes={"input": {0: "n"}, "probs": {0: "n"}},
        opset_version=OPSET, dynamo=False,
    )

    x = smoke_input(BATCH)
    with torch.no_grad():
        want = model(torch.from_numpy(x)).numpy()
    got = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"]).run(None, {"input": x})[0]
    assert np.abs(got - want).max() < 1e-5, f"export mismatch: {np.abs(got - want).max()}"
    assert np.abs(got[:, 0] - 0.5).max() > 0.1, f"golden probs too close to 0.5: {got}"

    (OUT / "expected.json").write_text(json.dumps({
        "batch": BATCH,
        "probs": [float(v) for v in got.ravel()],
        "made_with": {"torch": torch.__version__, "onnxruntime": ort.__version__, "opset": OPSET},
    }, indent=2) + "\n")
    print(f"wrote {onnx_path} ({onnx_path.stat().st_size} bytes)")
    print(f"probs {np.round(got, 4).tolist()}")


if __name__ == "__main__":
    main()
