# Fungal Pack (DeFungi)

## Dataset
- **Source**: DeFungi dataset (UCI ML Repository).
- **Licence**: UNVERIFIED.
- **Split**: Held-out split grouped by source image to prevent data leakage.

## Model (Placeholder)
Currently uses a dummy model file and placeholder manifest to validate the pipeline and unblock G2.
The expected input is `field` images, processed for fungal classes: `tsh`, `bash`, `gma`, `shc`, `bbh`.

## Metrics
Metrics are to be reported on held-out source-image groups.
- **Accuracy**: TBD
- **Sensitivity/Specificity**: TBD

## Known Limits
- Thresholds are provisional (`triage.provisional: true`).
- This pack currently outputs dummy results. A real patch/tile classifier must be trained and exported to ONNX to replace it.
