"""Deterministic synthetic thin-smear field used by test_nlm_segmentation.py (and to make its Java golden)."""
import cv2
import numpy as np

SEED = 20261002


def synthetic_field():
    """1328x747 RGB field: black vignette, pink background, packed RBCs with pallor, one granular WBC, specks.

    At this size compute_rv gives RV = 1.5, so segmentation runs at 885x498, the same as a 5312x2988 photo.
    """
    rng = np.random.default_rng(SEED)
    img = np.zeros((747, 1328, 3), np.uint8)
    cv2.circle(img, (664, 374), 352, (236, 200, 205), -1)
    for y in range(50, 710, 25):                     # packed grid: many cells touch or overlap
        for x in range(320, 1010, 25):
            cx, cy = x + int(rng.integers(-5, 6)), y + int(rng.integers(-5, 6))
            if (cx - 664) ** 2 + (cy - 374) ** 2 < 335 ** 2:
                r = int(rng.integers(10, 15))
                tint = rng.integers(-12, 13, 3)
                cv2.circle(img, (cx, cy), r, tuple(int(v) for v in np.array([196, 120, 168]) + tint), -1)
                # central pallor; its size straddles NLM's 150 px small-blob threshold at segmentation scale
                cv2.circle(img, (cx, cy), int(rng.integers(2, min(r - 1, 12))), (214, 150, 186), -1)
    for _ in range(40):
        cv2.circle(img, (int(rng.integers(360, 970)), int(rng.integers(60, 690))), int(rng.integers(1, 5)), (90, 30, 100), -1)
    img = cv2.GaussianBlur(img, (5, 5), 1.2)
    # WBC, painted after the blur: NLM finds WBCs by a strong texture gradient over the whole cell
    wbc = np.zeros(img.shape[:2], np.uint8)
    cv2.circle(wbc, (520, 300), 36, 1, -1)
    grains = cv2.resize(rng.integers(0, 2, (374, 664)).astype(np.uint8), (1328, 747), interpolation=cv2.INTER_NEAREST)
    img[(wbc == 1) & (grains == 1)] = (20, 0, 40)
    img[(wbc == 1) & (grains == 0)] = (200, 160, 210)
    return np.clip(img + rng.normal(0, 3, img.shape), 0, 255).astype(np.uint8)
