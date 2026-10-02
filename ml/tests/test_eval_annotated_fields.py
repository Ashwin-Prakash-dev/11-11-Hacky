"""Per-cell matching against NIH ThinBloodSmearsPf annotations; synthetic data, no dataset or model needed."""
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from ml.eval.eval_annotated_fields import FieldMatch, match_cells, read_annotation  # noqa: E402


class ReadAnnotationTest(unittest.TestCase):
    def test_polygons_and_points_become_labelled_centroids(self):
        text = (
            "3,5312,2988\n"
            "1-1,Parasitized,No_comment,Polygon,4,10,10,30,10,30,30,10,30\n"
            "1-2,Uninfected,No_comment,Point,1,100,50\n"
            "1-3,White_Blood_Cell,No_comment,Polygon,3,0,0,6,0,0,6\n"
        )
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "field.txt"
            path.write_text(text)
            size, cells = read_annotation(path)
        self.assertEqual(size, (5312, 2988))
        self.assertEqual([label for label, _ in cells], ["Parasitized", "Uninfected", "White_Blood_Cell"])
        np.testing.assert_allclose(cells[0][1], (20, 20))
        np.testing.assert_allclose(cells[1][1], (100, 50))
        np.testing.assert_allclose(cells[2][1], (2, 2))


class MatchCellsTest(unittest.TestCase):
    def test_counts_found_flagged_and_false_flags(self):
        boxes = [(0, 0, 50, 50), (100, 0, 150, 50), (200, 0, 250, 50)]
        probs = [0.9, 0.7, 0.2]
        cells = [
            ("Parasitized", (25, 25)),      # in box 0, flagged
            ("Parasitized", (500, 500)),    # missed by segmentation
            ("Uninfected", (125, 25)),      # in box 1, flagged: a false flag
            ("Uninfected", (225, 25)),      # in box 2, not flagged
            ("White_Blood_Cell", (600, 600)),
        ]
        self.assertEqual(
            match_cells(boxes, probs, cells),
            FieldMatch(rbcs=4, infected=2, cells_found=3, infected_found=1, infected_flagged=1, flagged=2, false_flags=1),
        )

    def test_one_flagged_box_over_two_infected_cells_is_no_false_flag(self):
        cells = [("Parasitized", (10, 10)), ("Parasitized", (40, 40))]
        m = match_cells([(0, 0, 50, 50)], [0.95], cells)
        self.assertEqual((m.infected_found, m.infected_flagged, m.flagged, m.false_flags), (2, 2, 1, 0))

    def test_threshold_is_strictly_above(self):
        m = match_cells([(0, 0, 50, 50)], [0.5], [("Parasitized", (25, 25))])
        self.assertEqual((m.infected_found, m.infected_flagged, m.flagged), (1, 0, 0))

    def test_no_cells_found_is_all_missed(self):
        m = match_cells([], [], [("Parasitized", (25, 25)), ("Uninfected", (5, 5))])
        self.assertEqual(m, FieldMatch(rbcs=2, infected=1, cells_found=0, infected_found=0, infected_flagged=0, flagged=0, false_flags=0))


if __name__ == "__main__":
    unittest.main()
