import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from ml.tools.check_packs import pack_problems  # noqa: E402


def write_pack(root: Path, name: str, model: dict, payload: bytes | None = b"weights"):
    pack = root / name
    pack.mkdir()
    (pack / "manifest.json").write_text(json.dumps({"id": name, "model": model}))
    if payload is not None:
        (pack / "model.onnx").write_bytes(payload)


class CheckPacksTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.sha = hashlib.sha256(b"weights").hexdigest()

    def tearDown(self):
        self.tmp.cleanup()

    def test_matching_hash_has_no_problems(self):
        write_pack(self.root, "ok", {"file": "model.onnx", "sha256": self.sha})
        self.assertEqual(pack_problems(self.root), [])

    def test_swapped_model_is_reported(self):
        write_pack(self.root, "swapped", {"file": "model.onnx", "sha256": self.sha}, payload=b"other")
        self.assertRegex(pack_problems(self.root)[0], "swapped.*sha256")

    def test_missing_model_file_is_reported(self):
        write_pack(self.root, "gone", {"file": "model.onnx", "sha256": self.sha}, payload=None)
        self.assertRegex(pack_problems(self.root)[0], "gone.*missing")

    def test_missing_model_marked_not_in_git_is_skipped(self):
        write_pack(self.root, "local", {"file": "model.onnx", "sha256": self.sha}, payload=None)
        (self.root / "local" / "WEIGHTS_NOT_IN_GIT").write_text("licence pending\n")
        self.assertEqual(pack_problems(self.root), [])

    def test_marked_model_that_is_present_is_still_hash_checked(self):
        write_pack(self.root, "local", {"file": "model.onnx", "sha256": self.sha}, payload=b"other")
        (self.root / "local" / "WEIGHTS_NOT_IN_GIT").write_text("licence pending\n")
        self.assertRegex(pack_problems(self.root)[0], "local.*sha256")

    def test_hub_pack_without_a_file_is_skipped(self):
        write_pack(self.root, "hub", {"endpoint": "http://hub.local/x"}, payload=None)
        self.assertEqual(pack_problems(self.root), [])

    def test_detector_sidecar_is_hash_checked(self):
        write_pack(self.root, "cascade", {"file": "model.onnx", "sha256": self.sha})
        pack = self.root / "cascade"
        (pack / "detector.json").write_text(json.dumps({"file": "wbc_detector.onnx", "sha256": self.sha}))
        (pack / "wbc_detector.onnx").write_bytes(b"other")

        self.assertRegex(pack_problems(self.root)[0], "cascade.*wbc_detector.onnx.*sha256")

    def test_missing_detector_sidecar_model_is_reported(self):
        write_pack(self.root, "cascade", {"file": "model.onnx", "sha256": self.sha})
        (self.root / "cascade" / "detector.json").write_text(
            json.dumps({"file": "wbc_detector.onnx", "sha256": self.sha})
        )

        self.assertRegex(pack_problems(self.root)[0], "cascade.*wbc_detector.onnx.*missing")

    def test_every_committed_pack_matches_its_model_hash(self):
        self.assertEqual(pack_problems(REPO_ROOT / "ml" / "packs"), [])


if __name__ == "__main__":
    unittest.main()
