# malaria_thin: thin-smear malaria cell classifier

NLM Malaria Screener's **Sudan-retrained** thin-smear CNN, converted from TensorFlow to ONNX. It classifies one red-cell crop as `parasitized` or `uninfected`.
- **Why this model:** it's the one NLM's own app runs with this segmentation (`CameraActivity.java` line 277 loads `malaria_thinsmear_44_retrainSudan_20P_4000C_separate.pb`). It's a demo choice, to be replaced after measurement on annotated field photos.
- **Where the crops come from:** the engine's shared RBC detector (`preprocess.source: cells`).
- **Python reference** for the whole photo → counts pipeline: [ml/reference/malaria_pipeline.py](../../reference/malaria_pipeline.py).

**Not validated for screening:**
- The weights are in git, in this private repo only (licence unresolved, see below).
- The triage, quality and uncertainty values remain provisional; see [threshold evidence](#threshold-evidence-issue-7).
- On 6 annotated field photos it flags 28 of 40 infected cells, and its false flags vary by slide (0–14 per field). That is behaviour on reserved fixtures, not accuracy (see Known limits).

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
| `model.onnx` | yes (private repo) | 1.5 MB, sha256 `4ae01239…`: NLM's Sudan model with its final Softmax removed (`python ml/tools/onnx_logits.py ml/models/malaria_thin_44_sudan.onnx model.onnx`). CI hash-checks it against the manifest |
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
- **Annotated field photos, cell by cell: the segmentation is good; this model flags about 70% of infected cells, and its false flags depend on the slide.** From `ml/eval/eval_annotated_fields.py` with `nlm` segmentation, in the Python reference (2026-10-02). It ran on the six reserved fields in [docs/datasets.md](../../../docs/datasets.md), whose annotations label every red cell:

  | Field | Annotated infected / red cells | Cells found | Infected flagged | False flags |
  |---|---|---:|---:|---:|
  | golden_positive | 15 / 114 | 110 | 9 | 0 |
  | golden_sparse | 9 / 79 | 81 | 9 | 1 |
  | golden_negative | 0 / 204 | 214 | 0 | 0 |
  | demo_positive | 2 / 209 | 217 | 0 | 14 |
  | demo_sparse | 14 / 94 | 94 | 10 | 0 |
  | demo_negative | 0 / 201 | 212 | 0 | 3 |

  - **Segmentation:** cell counts are within 5% of the annotation, and 39 of the 40 infected cells fall inside a detected box.
  - **This model:** it flags 28 of 40 infected cells, with 18 false flags among 861 uninfected cells. 14 of those are on one slide (`demo_positive`, patient C38P3), and none of that slide's flags are on its 2 infected cells. The previous default model flags 35 of 40, with 64 false flags.
  - **Triage under the provisional rules, one field per case:**
    - every positive field gives `ABNORMAL_FLAG`;
    - `demo_negative` gives a false `ABNORMAL_FLAG` (3 flags);
    - `golden_negative` gives `NEEDS_EXPERT` (fewer than 1000 clear cells).
  - **Not accuracy:** 6 fields from 3 reserved patients, and NLM may have trained on these patients (UNVERIFIED).
  - **Not run on the phone:** the Kotlin port closely matched the Python on the RBCNet fields (Track C in STATUS.md), but these six fields haven't been run on the phone.
- **The earlier RBCNet comparison was a weak test.** `ml/eval/eval_segmentation.py` compared 4 fields from a negative patient (C12N) with 4 from a positive one (C92P53). It found the positive patient flagged no more often: `nlm` with this model gave 5.6% vs 3.6% of cells; `simple` gave 1.4% vs 0.7%; the previous default model gave 18.0% vs 15.8% with `nlm`. But RBCNet only labels the patient, not the cells, so nobody knows how many infected cells those positive fields hold. The comparison mostly measured false flags.
  - Seen in the overlays:
    - NLM boxes merged clumps of 2–4 touching cells, which get flagged.
    - The simplified version misses touching cells.
    - Both flag cells with small dark specks.
- **One field is not a slide.** NLM keeps capturing fields until it has counted at least 1000 red cells (`CameraActivity.totalCellNeeded`).
- **Thin smears only.** Thick smears use a different NLM model.

## Licence
- NLM's root `LICENSE` is BSD-style. It requires shipping `NOTICE_NLM.txt` and the credit "Courtesy of the U.S. National Library of Medicine".
- But 85+ upstream source files carry GPLv3 headers, and the weights have no licence or provenance file of their own. Reuse is UNVERIFIED ([S1](../../../docs/spikes/S1-malaria-screener.md)).
- The weights are committed because the repo is private. **Resolve this before the repo goes public or the app is distributed:** history keeps the file even after a later delete.
- The segmentation port (`ml/reference/nlm_segmentation.py`) and the open licensing decisions: see [LICENSING.md](../../../LICENSING.md).
