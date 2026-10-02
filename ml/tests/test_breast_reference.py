"""Synthetic tests for the breast pack's desktop reference; no patient images needed."""
import unittest

import numpy as np

from ml.packs.breast_breakhis.reference.pipeline import preprocess, quality, field_result


class BreastReferenceTest(unittest.TestCase):
    def test_resize_uses_half_pixel_sampling_without_uint8_rounding(self):
        # Downsampling a 2x2 RGB field to one pixel preserves the fractional average.
        rgb = np.array([[[0, 20, 40], [1, 21, 41]],
                        [[2, 22, 42], [4, 24, 44]]], dtype=np.uint8)
        got = preprocess(rgb, width=1, height=1)
        self.assertEqual(got.shape, (1, 3, 1, 1))
        self.assertEqual(got.dtype, np.float32)
        np.testing.assert_allclose(got[0, :, 0, 0], np.array([1.75, 21.75, 41.75]) / 255, atol=1e-8)

    def test_resize_does_not_antialias_distant_pixels(self):
        rgb = np.zeros((1, 6, 3), np.uint8)
        rgb[0, 0] = 255
        np.testing.assert_array_equal(preprocess(rgb, width=2, height=1), np.zeros((1, 3, 1, 2)))

    def test_quality_uses_rounded_luminance_and_interior_laplacian(self):
        rgb = np.zeros((3, 4, 3), np.uint8)
        rgb[1, 1] = 255
        result = quality(rgb, {"min_blur": 1, "max_clipped_fraction": 0.9})
        self.assertAlmostEqual(result["blur_score"], np.var([-1020, 255]))
        self.assertAlmostEqual(result["exposure_score"], 11 / 12)
        self.assertEqual(result["reasons"], ["underexposed"])
        self.assertFalse(result["pass"])

    def test_whole_field_result_uses_prediction_not_dataset_label(self):
        manifest = {"id": "breast_breakhis", "version": "0.1.0",
                    "quality": {"min_blur": 0, "max_clipped_fraction": 1}}
        rgb = np.full((3, 3, 3), 100, np.uint8)
        result = field_result(rgb, [0.1, 0.9], manifest, "benign_reference")
        self.assertEqual(result["objects"], [{"label": "malignant", "score": 0.9, "bbox": None}])
        self.assertEqual(result["counts"], {"benign": 0, "malignant": 1})
        self.assertEqual(result["image_score"], 0.9)
        self.assertEqual(result["router"], {"verdict": "match", "score": 1.0})

    def test_quality_reject_has_no_downstream_result(self):
        manifest = {"id": "breast_breakhis", "version": "0.1.0",
                    "quality": {"min_blur": 1, "max_clipped_fraction": 1}}
        result = field_result(np.full((3, 3, 3), 100, np.uint8), [0.1, 0.9], manifest, "flat")
        self.assertFalse(result["quality"]["pass"])
        self.assertEqual(result["objects"], [])
        self.assertEqual(result["counts"], {})
        self.assertIsNone(result["image_score"])
        self.assertIsNone(result["router"])
        self.assertIsNone(result["uncertainty"])


if __name__ == "__main__":
    unittest.main()
