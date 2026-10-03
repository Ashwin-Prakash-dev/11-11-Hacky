import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from ml.packs.breast_breakhis.reference.eval_breakhis import (  # noqa: E402
    binary_metrics,
    label_from_path,
    probabilities_from_logits,
)


class BreakHisEvalTest(unittest.TestCase):
    def test_labels_use_directory_components_on_windows_and_posix(self):
        for path, label in (
            ("/data/malignant/ductal/field.png", 1),
            (r"C:\data\malignant\ductal\field.png", 1),
            ("/data/benign/adenosis/field.png", 0),
            (r"C:\data\benign\adenosis\field.png", 0),
            (Path("data/benign/adenosis/malignant.png"), 0),
        ):
            with self.subTest(path=path):
                self.assertEqual(label_from_path(path), label)

    def test_unknown_or_ambiguous_labels_are_rejected(self):
        for path in ("data/unknown/field.png", "data/nonmalignant/field.png",
                     "data/benign/malignant/field.png"):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "label"):
                label_from_path(path)

    def test_logits_receive_one_stable_softmax(self):
        logits = np.array([[1000.0, 1002.0], [-1000.0, -1000.0]])
        probabilities = probabilities_from_logits(logits)
        np.testing.assert_allclose(
            probabilities, [[0.119202922, 0.880797078], [0.5, 0.5]], rtol=1e-7,
        )

    def test_invalid_logits_are_rejected(self):
        for logits in ([[1.0]], [[0.0, np.nan]], [[np.inf, 1.0]], []):
            with self.subTest(logits=logits), self.assertRaises(ValueError):
                probabilities_from_logits(logits)

    def test_metrics_preserve_strict_threshold_and_average_tied_ranks(self):
        metrics = binary_metrics([0, 1, 0, 1], [0.1, 0.5, 0.5, 0.9])
        self.assertEqual(metrics["n"], 4)
        self.assertEqual(metrics["accuracy"], 0.75)
        self.assertEqual(metrics["sensitivity"], 0.5)
        self.assertEqual(metrics["specificity"], 1.0)
        self.assertEqual(metrics["auc"], 0.875)
        self.assertEqual(binary_metrics([0, 1], [0.5, 0.5])["auc"], 0.5)

    def test_empty_and_single_class_evaluations_are_rejected(self):
        for labels, scores in (([], []), ([0, 0], [0.1, 0.2]), ([1, 1], [0.8, 0.9])):
            with self.subTest(labels=labels), self.assertRaises(ValueError):
                binary_metrics(labels, scores)

    def test_invalid_metric_inputs_are_rejected(self):
        for labels, scores in (
            ([0, 1], [0.1]), ([0, 2], [0.1, 0.9]),
            ([0, 1], [0.1, np.nan]), ([0, 1], [0.1, 1.1]),
            ([[0, 1]], [[0.1, 0.9]]),
        ):
            with self.subTest(labels=labels, scores=scores), self.assertRaises(ValueError):
                binary_metrics(labels, scores)


if __name__ == "__main__":
    unittest.main()
