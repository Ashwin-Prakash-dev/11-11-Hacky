# Leukaemia (WBC) Pack

## Dataset
- **Source**: C-NMC Leukaemia dataset (ISBI 2019) via Kaggle ([link](https://www.kaggle.com/datasets/andrewmvd/leukemia-classification)).
- **Licence**: CC BY 4.0 (UNVERIFIED).
- **Split**: Held-out test split should be grouped by patient ID to prevent data leakage.

## Model (Placeholder)
Currently uses a randomly initialized lightweight CNN (smoke model structure) for evaluation/pipeline testing. 
The expected input is a WBC cell crop, which implies we need a WBC segmenter (or use `tile_vote`) in the pipeline before this runs. For now, it relies on `preprocess.source: "cells"` with `cell_type: "wbc"`.

## Metrics
Metrics are to be reported on a patient-grouped held-out split.
- **Accuracy**: TBD
- **Sensitivity/Specificity**: TBD

## Known Limits
- Thresholds are provisional (`triage.provisional: true`).
- A true segmentation algorithm for WBCs needs to be ported (similar to the RBC segmentation from #10) or we need to fall back to `tile_vote`.
- This is a placeholder pack to unblock G2. Golden cases are generated with dummy images and dummy JSON to satisfy the layout.
