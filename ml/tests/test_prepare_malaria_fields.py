"""Issue #6: reproducible fields, annotation counts and patient reservations."""
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from ml.tools.prepare_malaria_fields import (  # noqa: E402
    load_selection, parse_annotation, prepare, generate_failure,
)
from ml.tools.malaria_reservations import assert_no_reserved_patients  # noqa: E402


class AnnotationTest(unittest.TestCase):
    def parse(self, text):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "field.txt"
            path.write_text(text)
            return parse_annotation(path)

    def test_point_and_polygon_counts_are_cells_not_parasites(self):
        result = self.parse(
            "3,100,80\n1-1,Parasitized,No_Comment,Point,1,20,30\n"
            "1-2,Uninfected,No_comment,Polygon,3,1,2,3,4,5,6\n"
            "1-3,Parasite_Outside_Cell,No_Comment,Point,1,30,40\n"
        )
        self.assertEqual(result.counts, {"Parasitized": 1, "Uninfected": 1, "Parasite_Outside_Cell": 1})
        self.assertEqual((result.width, result.height, result.rbc_count), (100, 80, 2))

    def test_annotated_negative_is_zero_not_missing_ground_truth(self):
        result = self.parse("1,100,80\n1-1,Uninfected,No_Comment,Point,1,20,30\n")
        self.assertEqual(result.counts.get("Parasitized", 0), 0)

    def test_incomplete_duplicate_and_malformed_annotations_fail(self):
        cases = [
            "2,100,80\n1-1,Parasitized,x,Point,1,20,30\n",
            "2,100,80\n1-1,Uninfected,x,Point,1,20,30\n1-1,Uninfected,x,Point,1,30,40\n",
            "1,100,80\n1-1,Parasitized,x,Polygon,3,20,30\n",
            "1,100,80\n1-1,Parasitzed,x,Point,1,20,30\n",
            "1,100,80\n1-1,Parasitized,x,Point,1,nan,30\n",
        ]
        for text in cases:
            with self.subTest(text=text), self.assertRaises(ValueError):
                self.parse(text)


class PreparationTest(unittest.TestCase):
    def test_pinned_selection_covers_required_roles_and_has_no_reused_fields(self):
        selection = load_selection()
        for role in ("golden", "demo"):
            fields = [field for field in selection.fields if field.role == role]
            self.assertEqual(len(fields), 3)
            self.assertEqual({field.category for field in fields}, {"positive", "negative", "sparse"})
        self.assertEqual(len({field.field_id for field in selection.fields}), 6)
        self.assertEqual({variant["expected_reason"] for variant in selection.variants}, {"blur", "overexposed"})
        self.assertEqual(len(selection.notices), 2)

    def manifest(self, root):
        image = root / "source.png"
        cv2.imwrite(str(image), np.full((8, 10, 3), 128, np.uint8))
        annotation = root / "source.txt"
        annotation.write_text("1,10,8\n1-1,Uninfected,x,Point,1,3,4\n")
        notice = root / "notice.txt"
        notice.write_text("Test notice")

        def asset(path):
            return {"filename": path.name, "url": path.as_uri(), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}

        doc = {
            "schema_version": 1,
            "reserved_patients": ["C39P4"],
            "notices": [asset(notice)],
            "fields": [{"id": "golden_negative", "role": "golden", "category": "negative",
                        "patient_id": "143C39P4thinF_original", "field_id": "source",
                        "image": asset(image), "annotation": asset(annotation),
                        "counts": {"Uninfected": 1}, "width": 10, "height": 8}],
            "variants": [],
        }
        path = root / "selection.json"
        path.write_text(json.dumps(doc))
        return path, doc

    def test_prepare_verifies_originals_annotations_and_notice_then_reuses_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, _ = self.manifest(root)
            out = root / "out"
            prepare(manifest, out)
            self.assertEqual((out / "golden_negative/source.png").read_bytes(), (root / "source.png").read_bytes())
            self.assertEqual((out / "notice.txt").read_text(), "Test notice")
            with patch("urllib.request.urlopen", side_effect=AssertionError("cache should be reused")):
                prepare(manifest, out)

    def test_changed_ground_truth_and_wrong_checksum_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, doc = self.manifest(root)
            doc["fields"][0]["counts"] = {"Uninfected": 2}
            manifest.write_text(json.dumps(doc))
            with self.assertRaisesRegex(ValueError, "counts"):
                prepare(manifest, root / "out")
            doc["fields"][0]["counts"] = {"Uninfected": 1}
            doc["fields"][0]["image"]["sha256"] = "0" * 64
            manifest.write_text(json.dumps(doc))
            with self.assertRaisesRegex(RuntimeError, "expected"):
                prepare(manifest, root / "out2")
            self.assertFalse((root / "out2/golden_negative/source.png").exists())

    def test_unsafe_paths_and_incomplete_reservations_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, doc = self.manifest(root)
            doc["fields"][0]["image"]["filename"] = "../escape.png"
            manifest.write_text(json.dumps(doc))
            with self.assertRaises(ValueError):
                load_selection(manifest)
            doc["fields"][0]["image"]["filename"] = "source.png"
            doc["reserved_patients"] = []
            manifest.write_text(json.dumps(doc))
            with self.assertRaisesRegex(ValueError, "reserved"):
                load_selection(manifest)

    def test_failure_variants_are_deterministic_and_clip_without_uint8_wrap(self):
        image = np.indices((64, 64)).sum(axis=0).astype(np.uint8) % 2 * 200 + 20
        image = np.repeat(image[:, :, None], 3, axis=2)
        blur = {"kind": "gaussian_blur", "kernel": 31, "sigma": 8.0}
        first = generate_failure(image, blur)
        np.testing.assert_array_equal(first, generate_failure(image, blur))
        self.assertLess(float(first.var()), float(image.var()))
        bright = generate_failure(image, {"kind": "overexpose", "gain": 4.0, "offset": 20})
        self.assertTrue(np.all(bright[image == 220] == 255))


class ReservationTest(unittest.TestCase):
    def test_reserved_patients_block_other_fields_crops_and_explicit_ids(self):
        paths = [
            "Polygon Set/211C70P31_ThinF/Img/unselected.jpg",
            "Parasitized/C70P31thinF_cell_999.png",
            "Polygon Set\\244C7NthinF\\Img\\another.jpg",
            "Uninfected/C7NthinF_cell_1.png",
            "Parasitized/C38P3thinF_original_cell_1.png",
        ]
        for path in paths:
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "reserved"):
                assert_no_reserved_patients([{"path": path, "source": "NIH-NLM"}])
        with self.assertRaisesRegex(ValueError, "reserved"):
            assert_no_reserved_patients([{"path": "renamed.png", "patient_id": "C70P31", "source": "other"}])

    def test_unrelated_sources_and_unreserved_patients_pass(self):
        assert_no_reserved_patients([{"path": "breast/a.png", "source": "BreakHis"},
                                    {"path": "C999P999thinF_cell_1.png", "source": "NIH-NLM"}])

    def test_nlm_without_patient_provenance_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "patient"):
            assert_no_reserved_patients([{"path": "renamed.png", "source": "NIH-NLM-ThinBloodSmearsPf"}])

    def test_other_nih_datasets_do_not_require_malaria_patient_ids(self):
        assert_no_reserved_patients([{"path": "xray/normal.png", "source": "NIH Chest X-ray", "class": "chest"}])


if __name__ == "__main__":
    unittest.main()
