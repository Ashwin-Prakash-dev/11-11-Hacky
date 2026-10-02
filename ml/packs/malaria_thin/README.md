# malaria_thin: thin-smear malaria cell classifier

NLM Malaria Screener's thin-smear CNN, converted from TensorFlow to ONNX. It classifies one red-cell crop as `parasitized` or `uninfected`. The crops come from the engine's shared RBC detector (`preprocess.source: cells`). The Python reference for the whole photo → counts pipeline is [ml/reference/malaria_pipeline.py](../../reference/malaria_pipeline.py).

**Not demo-ready:**
- The weights aren't in git (licence, see below).
- The triage, quality and uncertainty values in the manifest are placeholders.
- The segmentation step is not validated (see Known limits).

## Files

| Path | In git | What |
|---|---|---|
| `manifest.json` | yes | Contract v1.0. Passes `contracts/validate.py` |
| `model.onnx` | **no** | 1.5 MB. Get it from Ashwin and check its sha256 against the manifest |
| `golden/expected.json`, `golden/verify.py` | yes | Expected outputs for 32 chips; desktop golden test |
| `golden/chips/*.png`, `golden/input_32x44x44x3_float32.bin` | **no** | 32 NIH cell chips (16 parasitized, 16 uninfected) and the exact input tensor. Dataset licence UNVERIFIED |
| `NOTICE_NLM.txt` | yes | NLM notice. Must ship with the app if the weights do |

## Model

| | |
|---|---|
| Input | `input`, `[N, 44, 44, 3]` float32, NHWC, RGB, `pixel / 255`, no mean subtraction. Batch is dynamic |
| Output | `probs`, `[N, 2]` softmax. **Column 0 = P(parasitized)**, column 1 = P(uninfected) |
| Decision | Argmax, which equals `P(parasitized) > 0.5`, NLM's default |
| Resize | **OpenCV `INTER_CUBIC`**, as NLM used. Android's bilinear `createScaledBitmap` was up to 0.149 off on the demo phone; a Kotlin port of `INTER_CUBIC` matched within 5e-5 |
| Graph | ONNX opset 13, made by tf2onnx 1.17.0. Standard ops only (Conv, Relu, MaxPool, GlobalAveragePool, MatMul, Softmax) |

The original kit cites NLM source for the input values: 44 px from `CameraActivity.java` (`TF_input_size_thin`); /255 and RGB from `Cells.putInPixels`; column order from `TensorFlowClassifier.recongnize_batch` ("0 is infected, 1 is normal"). It reports a maximum ONNX-vs-TensorFlow difference of 1.3e-6, converted from `malaria_thinsmear_44.h5.pb`. The S1 inventory lists this model as `malaria_thinsmear_44.tflite`. The exact upstream file and hash are UNVERIFIED.

## Golden tests (verified 2026-10-02)

| Where | Result |
|---|---|
| Desktop, onnxruntime 1.30.0 | Test A (input tensor → model): max error 4.8e-7 against a tolerance of 1e-4 |
| Demo phone (motorola edge 50 fusion), `MalariaPackGoldenTest` via `:engine:connectedDebugAndroidTest` | Test A: CPU 6.0e-7, XNNPACK 5.4e-7. Test B (PNG → bicubic → model): below 5e-5, 0/32 decisions changed |
| Demo phone timing, XNNPACK median | 2.2 ms for 1 cell, 29.8 ms for 32, 232 ms for 256 |

The golden chips are a parity fixture, not an accuracy estimate: 28 of 32 are classified correctly.

## Metrics

The original kit reports the following on the NIH `cell_images` set (27,558 chips). They were not reproduced here, and the kit includes no evaluation script.

| Model | Accuracy | Sensitivity | Specificity | AUC |
|---|---|---|---|---|
| This model | 95.7% | 96.6% | 94.8% | 0.990 |
| NLM's Sudan-retrained variant (not in this pack; local copy in `ml/models/`) | 92.3% | 86.3% | 98.3% | 0.982 |

**These are not held-out numbers.** NLM probably trained on these chips. UNVERIFIED. To check: evaluate per patient on data NLM didn't train on (Track E).

## Known limits
- **Segmentation.** The kit author ran the reference pipeline on full field photos (UNVERIFIED here):
  - C12N, a patient with 0 parasitized cells: about 13% of cells flagged.
  - C92P53, a positive patient: about 10% flagged.

  So it doesn't separate positive from negative patients yet. Most false positives are cells with small dark specks (debris). The classifier scores NLM-style crops well but mis-scores crops from the simplified segmentation.
- **One field is not a slide.** NLM keeps capturing fields until it has counted at least 1000 red cells (`CameraActivity.totalCellNeeded`).
- **Thin smears only.** Thick smears use a different NLM model.

## Licence
- NLM's root `LICENSE` is BSD-style. It requires shipping `NOTICE_NLM.txt` and the credit "Courtesy of the U.S. National Library of Medicine".
- But 85+ upstream source files carry GPLv3 headers, and the weights have no licence or provenance file of their own. Reuse is UNVERIFIED ([S1](../../../docs/spikes/S1-malaria-screener.md)).
- Don't commit the weights until Track E resolves this.
- Don't copy NLM's Java code. `ml/reference/malaria_pipeline.py` is a re-implementation.
