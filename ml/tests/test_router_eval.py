import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from ml.eval.router_eval import Prediction, accuracy_by_source  # noqa: E402


class RouterEvalTest(unittest.TestCase):
    def test_accuracy_is_reported_per_heldout_source_and_overall(self):
        preds = [
            Prediction("fungal", "b", "fungal"),
            Prediction("fungal", "b", "reject"),
            Prediction("reject", "f", "reject"),
            Prediction("reject", "f", "reject"),
            Prediction("reject", "f", "fungal"),
        ]
        report = accuracy_by_source(preds)
        self.assertEqual(report["by_source"]["fungal/b"], {"n": 2, "correct": 1, "accuracy": 0.5})
        self.assertEqual(report["by_source"]["reject/f"]["accuracy"], 2 / 3)
        self.assertEqual(report["overall"], {"n": 5, "correct": 3, "accuracy": 0.6})

    def test_confusion_counts_true_vs_predicted(self):
        preds = [Prediction("fungal", "b", "reject"), Prediction("fungal", "b", "reject")]
        self.assertEqual(accuracy_by_source(preds)["confusion"], {"fungal": {"reject": 2}})

    def test_empty_predictions_rejected(self):
        with self.assertRaises(ValueError):
            accuracy_by_source([])


if __name__ == "__main__":
    unittest.main()
