"""Breast pack contract checks; discovered by both ML CI jobs without private weights/data."""
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from contracts.validate import check  # noqa: E402

PACK = ROOT / "ml/packs/breast_breakhis"


class BreastPackContractTest(unittest.TestCase):
    def test_manifest_is_a_valid_phone_field_classifier(self):
        path = PACK / "manifest.json"
        self.assertTrue(path.is_file(), "breast pack needs manifest.json")
        self.assertEqual(check(path), ("manifest", []))
        manifest = json.loads(path.read_text())
        self.assertEqual(manifest["id"], PACK.name)
        self.assertEqual(manifest["runtime"], "onnx")
        self.assertEqual(manifest["compute"], "phone")
        self.assertEqual(manifest["preprocess"]["source"], "field")
        self.assertEqual(manifest["input"]["shape"], [1, 3, 224, 224])
        self.assertEqual(manifest["output"]["labels"], ["benign", "malignant"])
        self.assertEqual(manifest["output"]["image_score_label"], "malignant")
        self.assertTrue(manifest["triage"]["provisional"])
        # One provisional flag rule. No benign/normal rule on purpose: a benign prediction is not evidence of a normal screen.
        self.assertEqual([(r["id"], r["level"]) for r in manifest["triage"]["rules"]], [("malignant_seen", "ABNORMAL_FLAG")])
        self.assertNotIn("NORMAL_SCREEN", {r["level"] for r in manifest["triage"]["rules"]})

    def test_each_reference_image_has_a_contract_golden(self):
        reference = json.loads((PACK / "golden/expected.json").read_text())
        for case in reference["cases"]:
            with self.subTest(image=case["file"]):
                path = PACK / "golden" / Path(case["file"]).with_suffix(".json")
                self.assertTrue(path.is_file(), f"Android harness needs {path.name}")
                self.assertEqual(check(path), ("field_result", []))
                field = json.loads(path.read_text())
                self.assertEqual(field["pack_id"], PACK.name)
                self.assertTrue(field["quality"]["pass"])
                self.assertEqual(sum(field["counts"].values()), 1)
                self.assertEqual(len(field["objects"]), 1)
                self.assertIsNone(field["objects"][0].get("bbox"))
                self.assertAlmostEqual(field["image_score"], field["objects"][0]["score"]
                    if field["objects"][0]["label"] == "malignant" else 1 - field["objects"][0]["score"])

    def test_pack_holds_only_the_logits_model_and_its_hash_is_set(self):
        # The 28 MB upstream export stays out of the pack (it would double the APK's share); prepare_pack.py rebuilds from it.
        manifest = json.loads((PACK / "manifest.json").read_text())
        self.assertEqual(manifest["model"]["file"], "model_logits.onnx")
        self.assertRegex(manifest["model"]["sha256"] or "", r"^[0-9a-f]{64}$")
        self.assertEqual(sorted(p.name for p in PACK.glob("*.onnx")), ["model_logits.onnx"])
        self.assertTrue((PACK / "prepare_pack.py").is_file())

    def test_every_golden_case_has_its_image_next_to_the_json(self):
        # PackGoldenTest reads golden/<name>.png beside golden/<name>.json.
        reference = json.loads((PACK / "golden/expected.json").read_text())
        for case in reference["cases"]:
            self.assertTrue((PACK / "golden" / case["file"]).is_file(), case["file"])

    def test_runtime_model_outputs_logits_when_local_weights_exist(self):
        manifest_path = PACK / "manifest.json"
        self.assertTrue(manifest_path.is_file(), "manifest must name the logits model")
        manifest = json.loads(manifest_path.read_text())
        model_path = PACK / manifest["model"]["file"]
        if not model_path.is_file():
            self.skipTest("Weights are local-only; run prepare_pack.py before device tests")
        import onnx
        model = onnx.load(model_path)
        onnx.checker.check_model(model)
        self.assertEqual(len(model.graph.output), 1)
        output = model.graph.output[0]
        self.assertEqual(output.name, "logits")
        producer = next(node for node in model.graph.node if output.name in node.output)
        self.assertNotEqual(producer.op_type, "Softmax", "ClassifierDecoder applies softmax once")


if __name__ == "__main__":
    unittest.main()
