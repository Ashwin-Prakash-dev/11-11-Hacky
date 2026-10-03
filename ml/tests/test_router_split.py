import json
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from ml.train.router_split import SplitError, load_split  # noqa: E402


def entries(cls, source, n=2):
    return [{"path": f"{source}/{cls}_{i}.png", "class": cls, "source": source} for i in range(n)]


def valid():
    files = []
    for cls, sources in {"breast_breakhis": ["a", "b"], "malaria_thin": ["c", "d"], "reject": ["e", "f"]}.items():
        for s in sources:
            files += entries(cls, s)
    return {
        "schema_version": 1,
        "reject_class": "reject",
        "heldout": {"breast_breakhis": "b", "malaria_thin": "d", "reject": "f"},
        "files": files,
    }


class RouterSplitTest(unittest.TestCase):
    def load(self, doc):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "split.json"
            path.write_text(json.dumps(doc))
            return load_split(path)

    def test_labels_come_from_files_with_reject_last(self):
        split = self.load(valid())
        self.assertEqual(split.labels, ["breast_breakhis", "malaria_thin", "reject"])

    def test_heldout_source_is_entirely_in_test(self):
        split = self.load(valid())
        self.assertEqual({(f.cls, f.source) for f in split.test}, {("breast_breakhis", "b"), ("malaria_thin", "d"), ("reject", "f")})
        self.assertEqual({(f.cls, f.source) for f in split.train}, {("breast_breakhis", "a"), ("malaria_thin", "c"), ("reject", "e")})

    def test_single_source_class_rejected(self):
        doc = valid()
        doc["files"] = [f for f in doc["files"] if f["source"] != "a"]
        with self.assertRaisesRegex(SplitError, "at least 2 sources"):
            self.load(doc)

    def test_missing_heldout_rejected(self):
        doc = valid()
        del doc["heldout"]["breast_breakhis"]
        with self.assertRaisesRegex(SplitError, "heldout"):
            self.load(doc)

    def test_heldout_must_be_a_source_of_that_class(self):
        doc = valid()
        doc["heldout"]["breast_breakhis"] = "c"
        with self.assertRaisesRegex(SplitError, "heldout"):
            self.load(doc)

    def test_missing_reject_class_rejected(self):
        doc = valid()
        doc["files"] = [f for f in doc["files"] if f["class"] != "reject"]
        del doc["heldout"]["reject"]
        with self.assertRaisesRegex(SplitError, "reject"):
            self.load(doc)

    def test_duplicate_path_rejected(self):
        doc = valid()
        doc["files"].append(dict(doc["files"][0]))
        with self.assertRaisesRegex(SplitError, "duplicate"):
            self.load(doc)

    def test_reserved_demo_patient_rejected_before_train_or_eval(self):
        doc = valid()
        doc["files"][0]["path"] = "C70P31thinF_cell_999.png"
        with self.assertRaisesRegex(SplitError, "reserved"):
            self.load(doc)


if __name__ == "__main__":
    unittest.main()
