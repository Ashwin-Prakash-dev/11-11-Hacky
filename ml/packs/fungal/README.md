# Fungal Pack (DeFungi)

## Dataset
- **Source**: DeFungi dataset (UCI ML Repository).
- **Licence**: UNVERIFIED.
- **Split**: Held-out split grouped by source image to prevent data leakage. Patch filenames encode the source image (for example `H1_100a_1.jpg` is source `100a`), so a leak-free split is possible (spike S5).
- **Classes**: 5 fungal classes and no normal class, so a field with no fungus has no label to receive.

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
