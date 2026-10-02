# malaria_thin: thin-smear malaria cell classifier

NLM Malaria Screener's **Sudan-retrained** thin-smear CNN, converted from TensorFlow to ONNX. It classifies one red-cell crop as `parasitized` or `uninfected`.
- **Why this model:** it's the one NLM's own app runs with this segmentation (`CameraActivity.java` line 277 loads `malaria_thinsmear_44_retrainSudan_20P_4000C_separate.pb`). It's a demo choice, to be replaced after measurement on annotated field photos.
- **Where the crops come from:** the engine's shared RBC detector (`preprocess.source: cells`).
- **Python reference** for the whole photo → counts pipeline: [ml/reference/malaria_pipeline.py](../../reference/malaria_pipeline.py).

**Not validated for screening:**
- The weights aren't in git (licence, see below).
- The triage, quality and uncertainty values remain provisional; see [threshold evidence](#threshold-evidence-issue-7).
- On field photos it doesn't yet separate a negative patient from a positive one (see Known limits).

## Threshold evidence (issue #7)

Reviewed 2026-10-02. All existing values are retained and `triage.provisional` stays `true`. Protocols for human microscopy do not validate this classifier's predictions.

| Manifest setting | Evidence and status |
|---|---|
| `parasite_seen`: `parasitized >= 1` | **PROVISIONAL.** A predicted positive triggers review, not diagnosis. WHO MM-SOP-08 describes human parasite detection; it supplies no validated threshold for this model. |
| `enough_cells_clear`: `uninfected >= 1000` AND `parasitized == 0` | **PROVISIONAL.** [NLM CameraActivity at c485a21](https://github.com/LHNCBC/MalariaScreener/blob/c485a21/app/src/main/java/gov/nih/nlm/malaria_screener/camera/CameraActivity.java#L151) sets a configurable 1000-cell capture target (lines 151, 450). This is software provenance, not a clinical negative criterion. Zero predicted positives does not establish absence of parasites. |
| `aggregation.min_fields = 1` | **PROVISIONAL.** Pipeline minimum only; no validated negative-screen examination minimum for this automated thin-smear pack was identified in the reviewed protocols. |
| `uncertainty.band = [0.5, 0.65]`; `max_fraction = 0.05` | **PROVISIONAL.** Contract-example placeholders, with no applicable protocol or held-out calibration evidence. These are model scores and a fraction of predictions, not parasite density. |
| `quality.min_blur = 8.0`; `max_clipped_fraction = 0.45` | **PROVISIONAL.** Engineering settings measured on 8 RBCNet fields, as recorded in this manifest's `provenance.notes`; not clinical cutoffs or a validated quality calibration. |

**Examination minimum and applicability:**

- [WHO MM-SOP-08, version 1 (2016), section 4.2, page 4](https://www.who.int/docs/default-source/wpro---documents/toolkit/malaria-sop/gmp-sop-08-revised.pdf): examine at least 100 high-power fields before reporting no parasites seen in a **thick film**. Section 4.3 uses thin films to confirm species and mixed infections. The thick-film minimum cannot be substituted for an RBC count or this pack's minimum accepted images.
- [WHO MM-SOP-09, version 1 (2016), section 4.2, page 4](https://www.who.int/docs/default-source/wpro---documents/toolkit/malaria-sop/gmp-sop-09-revised.pdf): thin-film parasite counting uses approximately 20 non-overlapping fields with about 250 RBCs each (about 5000 RBCs). This is a quantification procedure when infected cells are present, not evidence for declaring this pack's negative predictions normal.
- A clinically supported automated thin-smear negative-call minimum remains **UNVERIFIED**. Check an applicable national microscopy protocol with a clinical reviewer and validate the complete pipeline on held-out annotated slides before changing these thresholds or removing provisional status.

**Presentation audit (2026-10-02, source inspection only):** `ResultScreen` displays "PROVISIONAL: thresholds not clinically validated"; `DebugAnalyzeActivity` labels triage "provisional thresholds". The existing `NavigationTest` checks the main-screen provisional badge; it was not rerun for this documentation change. Neither screen presents the placeholder cutoffs as clinical facts. Debug overlay colours are model-score display settings, not clinical severity. No deck or claims file is present in this checkout: external deck compliance is **UNVERIFIED**. Track F must review the actual deck and source or label every threshold claim before issue #7's presentation checkbox is complete.

**Completion evidence:** compare this table with the manifest's `triage.source`, validate the manifest with `conda run -n deepsight python contracts/validate.py ml/packs/malaria_thin/manifest.json`, and confirm a semantic comparison against the base manifest differs only in `triage.source`. No threshold or engine behavior changes are intended.

**Validation recorded (2026-10-02):** in the `deepsight` Conda environment, `contracts/validate.py` passed this manifest and all pack manifests; `ml/tools/check_packs.py` passed (weights excluded by `WEIGHTS_NOT_IN_GIT` remain skipped); and `:engine:testDebugUnitTest` passed 82 tests. PowerShell `ConvertFrom-Json` parsed the edited manifest, a semantic comparison against the rebased parent commit confirmed that only `triage.source` changed, and `git diff --check` passed. These checks do not establish clinical validity or device performance.

## Files

| Path | In git | What |
|---|---|---|
| `manifest.json` | yes | Contract v1.0. Passes `contracts/validate.py` |
| `model.onnx` | **no** | 1.5 MB, sha256 `4ae01239…`: NLM's Sudan model with its final Softmax removed (`python ml/tools/onnx_logits.py ml/models/malaria_thin_44_sudan.onnx model.onnx`). Get it from Ashwin and check it against the manifest |
| `golden/expected.json`, `golden/verify.py` | yes | Expected outputs of this model for 32 chips (`model_sha256` records which model); desktop golden test |
| `golden/chips/*.png`, `golden/input_32x44x44x3_float32.bin` | **no** | 32 NIH cell chips (16 parasitized, 16 uninfected) and the exact input tensor. Dataset licence UNVERIFIED |
| `NOTICE_NLM.txt` | yes | NLM notice. Must ship with the app if the weights do |

The previous default model, `malaria_thin_44.onnx` (sha256 `63e2d8d5…`), is kept locally in `ml/models/` for comparison.

## Model

| | |
|---|---|
| Input | `input`, `[N, 44, 44, 3]` float32, NHWC, RGB, `pixel / 255`, no mean subtraction. Batch is dynamic |
| Output | `logits`, `[N, 2]`. The engine's `ClassifierDecoder` applies softmax (the convention the smoke golden also encodes), so the graph doesn't. **Column 0 = parasitized**, column 1 = uninfected |
| Decision | Argmax, which equals `P(parasitized) > 0.5`, NLM's default |
| Resize | **OpenCV `INTER_CUBIC`**, as NLM used. A Kotlin port of `INTER_CUBIC` matched desktop within 5e-5 on the demo phone. Android's bilinear `createScaledBitmap` was 0.064 off with this model and 0.149 off with the previous one |
| Graph | ONNX opset 13, made by tf2onnx 1.17.0. Standard ops only (Conv, Relu, MaxPool, GlobalAveragePool, MatMul, Softmax) |

Sources for the input values, all in NLM's code:
- 44 px: `CameraActivity.java` (`TF_input_size_thin`).
- /255 and RGB: `Cells.putInPixels`.
- Column order: `TensorFlowClassifier.recongnize_batch`, which treats `output[i*2] > Th` as infected. The app uses the same code for this model.

Not yet verified:
- **Conversion parity:** this ONNX file hasn't been compared with NLM's original TensorFlow graph. S1 could not convert the Sudan `.pb` itself.
- **Training data:** the file name suggests 20 patients and 4,000 cells, possibly from Sudan.

## Golden tests (verified 2026-10-02)

| Where | Result |
|---|---|
| Desktop, onnxruntime 1.30.0 | Expected outputs generated from the exact input tensor |
| Demo phone (motorola edge 50 fusion), `MalariaPackGoldenTest` via `:engine:connectedDebugAndroidTest` | Test A (input tensor → model): CPU 5.2e-7, XNNPACK 6.3e-7, tolerance 1e-4. Test B (PNG → bicubic → model): below 5e-5, 0/32 decisions changed. 3/3 tests passed |
| Demo phone timing, XNNPACK median | 4.5 ms for 1 cell, 60 ms for 32, 521 ms for 256. This run was about twice as slow as the previous model's run (232 ms for 256) on the same architecture, probably the phone's state (UNVERIFIED) |

The golden chips are a parity fixture, not an accuracy estimate. This model gets 28 of 32 right: 0 false positives and 4 missed parasitized cells. The previous model also got 28 right, but with 3 false positives and 1 miss.

## Metrics

The original kit reports the following on the NIH `cell_images` set (27,558 chips). They were not reproduced here, and the kit includes no evaluation script.

| Model | Accuracy | Sensitivity | Specificity | AUC |
|---|---|---|---|---|
| **This model** (Sudan-retrained) | 92.3% | 86.3% | 98.3% | 0.982 |
| Previous default (`ml/models/malaria_thin_44.onnx`) | 95.7% | 96.6% | 94.8% | 0.990 |

**These are not held-out numbers.** NLM probably trained the default model on these chips. UNVERIFIED. To check: evaluate per patient on annotated field photos NLM didn't train on (Track E).

This model trades sensitivity for specificity. It raises fewer false alarms but misses more infected cells, so the case-level triage rule needs care.

## Known limits
- **Field photos: the pipeline doesn't yet separate a negative patient from a positive one.** `ml/eval/eval_segmentation.py`, 4 RBCNet fields per patient. The figures are the % of cells with P(parasitized) > 0.5:

  | Segmentation | Model | C12N (negative) | C92P53 (positive) |
  |---|---|---|---|
  | `nlm` (port that matches NLM's Java exactly) | **this model** | 5.6% | 3.6% |
  | `simple` | **this model** | 1.4% | 0.7% |
  | `nlm` | previous default | 18.0% | 15.8% |
  | `simple` | previous default | 13.1% | 10.4% |

  - RBCNet has no per-cell labels, so it's unknown how many of the positive patient's flags are real.
  - Seen in the overlays:
    - NLM boxes merged clumps of 2–4 touching cells, which get flagged.
    - The simplified version misses touching cells.
    - Both flag cells with small dark specks.
- **One field is not a slide.** NLM keeps capturing fields until it has counted at least 1000 red cells (`CameraActivity.totalCellNeeded`).
- **Thin smears only.** Thick smears use a different NLM model.

## Licence
- NLM's root `LICENSE` is BSD-style. It requires shipping `NOTICE_NLM.txt` and the credit "Courtesy of the U.S. National Library of Medicine".
- But 85+ upstream source files carry GPLv3 headers, and the weights have no licence or provenance file of their own. Reuse is UNVERIFIED ([S1](../../../docs/spikes/S1-malaria-screener.md)).
- Don't commit the weights until Track E resolves this.
- The segmentation port (`ml/reference/nlm_segmentation.py`) and the open licensing decisions: see [LICENSING.md](../../../LICENSING.md).
