"""Tests for ml/reference/nlm_segmentation.py, the port of NLM Malaria Screener's thin-smear segmentation."""
import json
import os
import sys
import unittest

import cv2
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path[:0] = [HERE, os.path.join(HERE, "..", "reference")]

import malaria_pipeline as mp  # noqa: E402
import nlm_segmentation as nlm  # noqa: E402
from nlm_synthetic import synthetic_field  # noqa: E402

DATA = os.path.join(HERE, "data")


def segment(rgb):
    h, w = rgb.shape[:2]
    rv = mp.compute_rv(h, w)
    return nlm.marker_based_watershed(cv2.resize(rgb, (int(w / rv), int(h / rv)), interpolation=cv2.INTER_CUBIC), rv)


class MatchesNlmJava(unittest.TestCase):
    """Golden outputs of NLM's own Java (OpenCV 3.4.2) on synthetic_field(). The port matched them exactly on the
    dev machine. The small tolerances allow for CPU-dependent float rounding in OpenCV's SIMD code; changing CLAHE's
    clip limit, a LoG sigma or the WBC factor moves 16-39 pixels and fails."""

    @classmethod
    def setUpClass(cls):
        cls.rgb = synthetic_field()
        cls.ws, cls.wbc = segment(cls.rgb)
        cls.centers = nlm.run_cells(cls.ws, cls.wbc, cls.rgb)[1]

    def test_watershed_mask(self):
        golden = cv2.imread(os.path.join(DATA, "nlm_synthetic_ws.png"), cv2.IMREAD_GRAYSCALE)
        self.assertEqual(golden.shape, self.ws.shape)
        self.assertLessEqual(int((self.ws != golden).sum()), 10)

    def test_wbc_mask(self):
        golden = cv2.imread(os.path.join(DATA, "nlm_synthetic_wbc.png"), cv2.IMREAD_GRAYSCALE) // 255
        self.assertGreater(golden.sum(), 0, "golden must exercise the WBC path")
        self.assertLessEqual(int((self.wbc != golden).sum()), 5)

    def test_cell_centers(self):
        with open(os.path.join(DATA, "nlm_synthetic_cells.json")) as f:
            golden = [tuple(c) for c in json.load(f)["cell_centers_row_col"]]
        ours = [tuple(int(v) for v in c) for c in self.centers]
        self.assertEqual(len(ours), len(golden))
        self.assertGreaterEqual(len(set(ours) & set(golden)), len(golden) - 1)


class AndroidFixturesMatchGenerator(unittest.TestCase):
    """The phone tests (RbcDetectorTest, FieldAnalyzerTest) read these PNGs instead of regenerating the field."""

    def test_synthetic_png_is_synthetic_field(self):
        png = cv2.cvtColor(cv2.imread(os.path.join(DATA, "nlm_synthetic.png")), cv2.COLOR_BGR2RGB)
        np.testing.assert_array_equal(png, synthetic_field())

    def test_small_png_is_the_desktop_resize(self):
        h, w = 747, 1328
        rv = mp.compute_rv(h, w)
        small = cv2.resize(synthetic_field(), (int(w / rv), int(h / rv)), interpolation=cv2.INTER_CUBIC)
        png = cv2.cvtColor(cv2.imread(os.path.join(DATA, "nlm_synthetic_small.png")), cv2.COLOR_BGR2RGB)
        # OpenCV's cubic SIMD path rounds channels differently across CPU architectures (about 1.2% on macOS ARM,
        # two channels on Linux x86). Keep this tight enough that a changed source, size or interpolation still fails:
        # at most 2% of channels may differ, and only by one intensity level.
        diff = np.abs(png.astype(np.int16) - small.astype(np.int16))
        self.assertLessEqual(int(diff.max()), 1)
        self.assertLessEqual(int(np.count_nonzero(diff)), png.size // 50)


class ReproducesJavaQuirks(unittest.TestCase):
    def test_histogram_retake_when_green_minimum_is_not_near_zero(self):
        ramp = np.tile(np.arange(30, 231, dtype=np.uint8), (10, 1))
        self.assertIsNone(nlm.stretch_hist_8bit(ramp, 0.01, 0.99))
        ramp[0, 0] = 0
        self.assertIsNotNone(nlm.stretch_hist_8bit(ramp, 0.01, 0.99))

    def test_float_zero_over_zero_is_zero_as_in_opencv_342(self):
        out = nlm._div_self(np.array([0.0, 5.0, 255.0]))
        np.testing.assert_array_equal(out, [0.0, 1.0, 1.0])

    def test_uint8_scalar_rounds_half_to_even_and_saturates(self):
        self.assertEqual([nlm._scalar_u8(v) for v in (2.5, 3.5, 300.0, -4.0)], [2, 4, 255, 0])

    def test_roi_filter_reads_parent_pixels(self):
        img = np.ones((10, 10), np.uint8)
        img[:, 2] = 0
        k = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
        roi = nlm._morph_roi(cv2.erode, img, (3, 0, 4, 10), k)
        self.assertEqual(roi[5, 0], 0)                                   # parent column 2 erodes ROI column 0
        self.assertEqual(cv2.erode(np.ascontiguousarray(img[:, 3:7]), k)[5, 0], 1)   # an isolated ROI would not

    def test_wbc_test_saturates_labels_at_255(self):
        mask = np.zeros((498, 885), np.uint8)
        squares = [(2 + 16 * (i // 55), 2 + 16 * (i % 55)) for i in range(300)]   # raster order = label order
        for y, x in squares:
            mask[y:y + 12, x:x + 12] = 255
        ori = np.full((747, 1328, 3), 128, np.uint8)

        def kept(wbc_square):
            wbc = np.zeros_like(mask)
            y, x = squares[wbc_square]
            wbc[y + 3:y + 9, x + 3:x + 9] = 1
            return len(nlm.run_cells(mask, wbc, ori)[1])

        self.assertEqual(kept(10), 299)        # only the touching cell is dropped
        self.assertEqual(kept(260), 254)       # label 261 is tested as 255, so every label >= 255 is dropped


class PipelineUsesPort(unittest.TestCase):
    def setUp(self):
        self.model = mp.MalariaThin.__new__(mp.MalariaThin)               # no ONNX session: weights aren't in git
        self.model.classify = lambda x: np.tile(np.float32([0.2, 0.8]), (len(x), 1))
        self.rgb = synthetic_field()

    def test_nlm_segmentation_is_the_default(self):
        boxes, p = self.model.cells(self.rgb)
        self.assertEqual(boxes, nlm.run_cells(*segment(self.rgb), self.rgb)[2])
        np.testing.assert_allclose(p, 0.2)

    def test_simple_segmentation_still_selectable(self):
        boxes, p = self.model.cells(self.rgb, seg="simple")
        self.assertGreater(len(boxes), 0)
        self.assertEqual(len(p), len(boxes))


if __name__ == "__main__":
    unittest.main()
