import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

try:
    import numpy as np
    import onnxruntime as ort
    import torch
    from PIL import Image
except ImportError:  # CI's Python job installs only jsonschema; run this inside the deepsight env
    torch = None


@unittest.skipIf(torch is None, "needs the deepsight Conda env (torch, onnxruntime, Pillow)")
class TrainRouterTest(unittest.TestCase):
    def test_trains_one_epoch_exports_onnx_matching_torch_and_reports_heldout(self):
        from ml.train.train_router import run

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            files = []
            rng = np.random.default_rng(0)
            for ci, cls in enumerate(["fungal", "malaria_thin", "reject"]):
                for source in ["a", "b"]:
                    for i in range(4):
                        rel = f"{source}/{cls}_{i}.png"
                        (root / source).mkdir(exist_ok=True)
                        pixels = np.clip(rng.normal(60 * ci + 60, 20, (40, 40, 3)), 0, 255).astype("uint8")
                        Image.fromarray(pixels).save(root / rel)
                        files.append({"path": rel, "class": cls, "source": source})
            split = root / "split.json"
            split.write_text(json.dumps({
                "schema_version": 1, "reject_class": "reject",
                "heldout": {"fungal": "b", "malaria_thin": "b", "reject": "b"}, "files": files,
            }))
            out = root / "out"
            report, model = run(split, root, out, epochs=1, image_size=32, pretrained=False)

            self.assertEqual(json.loads((out / "labels.json").read_text()), ["fungal", "malaria_thin", "reject"])
            self.assertEqual(set(report["by_source"]), {"fungal/b", "malaria_thin/b", "reject/b"})
            self.assertEqual(report["overall"]["n"], 12)

            x = torch.rand(2, 3, 32, 32)
            with torch.no_grad():
                expected = model.eval()(x).numpy()
            got = ort.InferenceSession(str(out / "router.onnx")).run(None, {"input": x.numpy()})[0]
            np.testing.assert_allclose(got, expected, atol=1e-4)
            np.testing.assert_allclose(got.sum(axis=1), 1.0, atol=1e-5)


if __name__ == "__main__":
    unittest.main()
