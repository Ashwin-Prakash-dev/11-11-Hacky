# DeepSight status

**Last updated:** 2026-10-02 (malaria field pipeline on the phone).
**Hackathon clock:** H0 = TBD. Fill in the start time so everyone can convert H-numbers to clock times.

Rules: AGENTS.md. Edit only your track's section, plus any rows you own. Say how each fact was verified, or mark it UNVERIFIED.

## Gates
| Gate | Due | Definition | State |
|---|---|---|---|
| G1 | H10 | Malaria field image → result on a physical Android phone | **partial:** a field photo gives a `field_result` plus provisional triage on the demo phone via the debug-only `DeepSight debug` screen (Track C below). Not yet in the main case flow (#30); accuracy not validated |
| G2 | H18 | 2 packs + router + quality gate + report | not started |

**Cut lines:**
- G1 missed by H12: everyone moves to malaria.
- G2 missed by H20: switch to a rule-based router and a template report.

## Spikes
| Spike | Question | Owner | State |
|---|---|---|---|
| S1 | NLM Malaria Screener: licence, can the model be extracted, does it convert to ONNX? | Codex | **partial:** thin TFLite conversion works; upstream reuse remains blocked, while a licensed dataset and evaluation-model fallback are pinned; first pack golden case still pending ([evidence](spikes/S1-malaria-screener.md)) |
| S2 | ONNX Runtime on Android | C | **yes:** CPU and XNNPACK outputs passed on the Nothing A059 within 1e-5; timings recorded below |
| S3 | LiteRT-LM Gemma inside our app | TBD | not started |
| S4 | Phone → laptop hub over hotspot, cleartext HTTP | TBD | not started |
| S5 | DeFungi classes | Gemini | **done:** 5 classes (TSH, BASH, GMA, SHC, BBH), no normal class. Yes, leak-free split is possible via filename prefixes. |
| S6 | PathOS conversion | TBD | not started |

## Tracks

### A: ML packs (owner: TBD)
- **Done:** 
  - S1 repository/model inspection and thin TFLite conversion proof. The test-first setup/downloader creates the project Conda environment, verifies pinned checksums, excludes unverified candidates by default, and loads the approved MobileNetV2 state dictionary. Baseline CI runs downloader tests, contract validation and engine JVM tests without downloading weights; both jobs passed on PR #1 (run `36971884205`). No dataset or model weight is committed.
  - Leukaemia pack skeleton (issue #41) using `preprocess.source: cells` for WBCs, with placeholder ONNX model, provisional triage thresholds, dummy golden cases, and manifest.
  - Fungal pack skeleton (issue #13) on DeFungi with placeholder ONNX model, provisional triage, dummy golden cases, and manifest.
- **`ml/packs/malaria_thin/` (NLM thin-smear CNN as ONNX):**
  - **Done:** pack format. The manifest passes `validate.py`; triage, quality and uncertainty are PLACEHOLDER. Also added: the README, golden expected outputs, and the Python reference, moved to `ml/reference/malaria_pipeline.py`.
  - **Model:** switched to NLM's Sudan-retrained model for the demo (2026-10-02), the one NLM's app loads (`CameraActivity.java` line 277).
    - On the RBCNet negative patient it flags 5.6% of cells instead of 18.0%.
    - It misses more infected cells (sensitivity 86.3% vs 96.6% on NIH crops, kit's figures, not held out).
    - Replace it after measurement on annotated field photos.
    - `model.onnx` outputs logits: the final Softmax is removed by `ml/tools/onnx_logits.py`, because the engine's decoder applies softmax. Expected outputs stay probabilities.
  - **Verified:** `MalariaPackGoldenTest` passed 3/3 on the edge 50 fusion demo phone with this model, loaded from `malaria_thin/` (`:engine:connectedDebugAndroidTest`, 2026-10-02):
    - Test A, CPU and XNNPACK: within 6.3e-7.
    - PNG chips with a Kotlin `INTER_CUBIC` port: within 5e-5. Android bilinear: 0.064 off (0.149 with the previous model).
  - **Not in git:** the weights and the NIH golden chips, until S1 and the cell_images licence are resolved. A `WEIGHTS_NOT_IN_GIT` file marks the pack, so CI's pack-hash check (`ml/tools/check_packs.py`) skips the missing model; a model that is present is still hash-checked.
  - **Quality thresholds:** PROVISIONAL `min_blur` 8.0 and `max_clipped_fraction` 0.45, from 8 RBCNet fields (`QualityGate` blur 12.8–15.8; 33–37% black pixels from the vignette). The placeholders (100 / 0.05) rejected every NLM photo.
  - **Field golden:** `golden/field_synthetic.json` holds the Python pipeline's cells and scores on `ml/tests/data/nlm_synthetic.png`, bound to the model by `model_sha256`.
  - **Segmentation:**
    - **Port:** `ml/reference/nlm_segmentation.py` is a faithful Python port of NLM's `MarkerBasedWatershed` and `Cells.runCells`. It is GPL-3.0 ([LICENSING.md](../LICENSING.md)), selected with `--seg nlm`; the old version is `--seg simple`.
    - **Verified bit-exact** against NLM's original Java on OpenCV 3.4.2 (a desktop harness, not in the repo): 0 differing mask pixels and identical cell lists on 8 RBCNet fields.
    - **Neither version separates the negative patient from the positive one.** % of cells flagged > 0.5 (`ml/eval/eval_segmentation.py`), C12N negative vs C92P53 positive:
      - previous model: simple 13.1 vs 10.4; nlm 18.0 vs 15.8.
      - Sudan model: simple 1.4 vs 0.7; nlm 5.6 vs 3.6.
    - **Kotlin port:** waits for approval and the team's GPL decision.
- **Next:** independently evaluate the fallback model on the licensed NIH-NLM data, export the chosen model to ONNX, and create the first golden case in `ml/packs/malaria_thin/`. Replace every UNVERIFIED and PLACEHOLDER manifest value before enabling the pack.

### B: Android shell (owner: TBD)
- **Done:** package `com.deepsight`, modules `:app`, `:engine` and `:report`. The stock Compose screen runs on a physical Android phone.
- **Skeleton (issue #26):** choose test → case → result → review/sign-off → history with a fake engine (`FakeEngine`, reads `contracts/examples`); disclaimer on every screen. **Verified on a phone (moto g32, Android 13, 2026-10-02):** `installDebug` installed and `:app:connectedDebugAndroidTest` passed 2/2, including `NavigationTest` (all screens + disclaimer).
- **Done (#27):** gallery import (photo picker, bytes copied unchanged) and CameraX capture (CameraX 1.6.2) save into `filesDir/cases/<caseId>/field_<n>.<ext>`; the files are the fields. **Verified on a moto g32 (Android 13) (2026-10-02):** `installDebug`; an imported PNG was byte-identical to the source, a captured JPEG was written, and both listed as fields on the case screen, with the rest of the skeleton flow still working; `:app:connectedDebugAndroidTest` 2/2, `CaseStoreTest` and `:engine:testDebugUnitTest` pass. Image files survive an app restart; history does not yet (in-memory until #28). It sits on the case screen above the fake engine's field list (the case id comes from the fake engine until #30); Room is not used yet.
- **Case storage (#28, issue closed 2026-10-02: the moto g32 run meets the AGENTS.md device rule):** Room 2.8.5 + KSP 2.3.12 in `:app` (`data/CaseDb.kt`, saving in `App.kt`): sign-off saves the case, its fields and the case_result JSON; saved cases are loaded into the history list at startup. Versions from dl.google.com maven-metadata and Maven Central, 2026-10-02. **Verified on a moto g32 (Android 13) (2026-10-02):** `installDebug`, `:app:connectedDebugAndroidTest`, and a signed-off case still listed in History after force-stop and relaunch (driven with adb).
- **Result screen + sign-off (#29):** `result/ResultScreen.kt` replaces the skeleton's result and review screens. Per field: quality pass, or reject reasons with Recapture (goes back to the case screen); router message; counts. Case: triage level, rule id, **provisional** badge, uncertainty, report slot ("Report pending (#24)" until the template report lands). Sign-off: clinician name + accept/override (override needs a note), saved into the case row's sign-off columns (`SignOff.applyTo`), which never touches `case_result_json`; the saved sign-off shows again when the result is reopened. The bbox overlay is skipped (no field images in the fake-engine case). **Verified on a moto g32 (Android 13) (2026-10-02):** `:app:testDebugUnitTest` (`SignOffTest`), `:app:connectedDebugAndroidTest` (`NavigationTest` now covers badge, Recapture, name entry, sign-off), and by hand with adb: sign off, force-stop, relaunch, reopen the result: it showed `ACCEPT by DrTest`. The screening-aid line is the disclaimer bar already on every screen.
- **Next:** real engine (#30); wire the template report (#24) into the report slot.

### C: On-device engine (owner: TBD)
- **Done:**
  - Contract types and JSON in `engine/.../contract/`. 4/4 JVM tests pass, and the JSON they write passes `validate.py`.
  - S2:
    - `engine/.../onnx/OnnxModel.kt` runs on CPU or XNNPACK (onnxruntime-android 1.30.0).
    - `OnnxSmokeTest` uses `ml/eval/make_smoke_model.py`: a 64×64 cell-crop-sized CNN with random weights. CPU and XNNPACK outputs matched the desktop golden output within 1e-5 on the Nothing A059 via `:engine:connectedDebugAndroidTest` (2/2 tests passed).
    - Median inference: CPU 0.6 ms at batch 1 and 72.1 ms at batch 256; XNNPACK 0.7 ms and 68.3 ms. See Verified facts for the measurement method.
  - **Done (#15, PackLoader framework):** discovers on-phone ONNX packs in APK assets, rejects invalid manifests/files and SHA-256 mismatches, and exposes only verified packs for the picker. Its 8 JVM tests pass; a synthetic smoke pack loaded through `AssetManager` and created an `OnnxModel` on the Nothing A059 (`:engine:connectedDebugAndroidTest`, 3/3 tests passed).
  - **Done (#16, preprocessing framework):** pure Kotlin converts `PixelImage` to float32 tensors with stretch/letterbox/center-crop/none resize, RGB/BGR, NCHW/NHWC and manifest scale/mean/std; unsupported dtype/stain modes fail explicitly. Eight synthetic JVM tests pass; manifest preprocessing into the smoke ONNX model matched its desktop output within 1e-4 on the Nothing A059 (`:engine:connectedDebugAndroidTest`, 4/4 tests passed).
  - **Done (#17, cell-crop framework only):** pure Kotlin validates pixel boxes, normalizes them to the frozen bbox contract and extracts ordered crops without changing pixels. Four synthetic JVM tests pass. OpenCV is deferred until #10 specifies the reference algorithm.
  - **Done (#18, classifier decoders):** manifest-driven softmax/sigmoid decoding produces objects, per-label counts, max image score and score-band uncertainty; quality rejection clears downstream values. Nine JVM tests pass, and decoder-generated `field_result` JSON passes `contracts/validate.py`.
  - **Done (#19, aggregation and triage):** `engine/.../triage/TriageEvaluator.kt` implements `contracts/README.md` steps 1-6 in pure Kotlin: `evaluate(caseId, fields, manifest)` returns a `CaseResult`. 22 JVM tests cover each step and its order, all five ops, a missing label, a null image score and a hand-computed multi-field case; engine JVM suite 62/62. Its `case_result` JSON passes `contracts/validate.py` (run from a scratch venv with `jsonschema`, since Conda isn't installed on this machine). JVM only: not run on a phone, and the inputs are hand-built fixtures, so behaviour on real model output is UNVERIFIED until #20 wires the pipeline. Counts, score and uncertainty are aggregated even when an early step decides, so the result always shows what was seen.
  - **Done (#20, field pipeline):** `engine/.../pipeline/FieldPipeline.kt`: `analyzeField(caseId, fieldId, bitmap)` runs quality → router stub (always `match` until #23) → crops → preprocess → one batched `OnnxModel.run` → `ClassifierDecoder`, filling `timing_ms` (quality, router, preprocess, pack, total); a quality reject stops early with only quality and total. `closeCase` delegates to `TriageEvaluator`, so #19 now also ran on real model output. `source: cells` packs take an injected `CellFinder`; no real one exists yet (#10 is Python-only), so only a test double has run, and without one they fail with a clear error. **Verified on a moto g32 (Android 13) (2026-10-02):** `:engine:testDebugUnitTest` passes (`FieldPipelineTest`: reject path; its JSON passes `validate.py`), and `:engine:connectedDebugAndroidTest` passes, including `FieldPipelineDeviceTest`: the smoke pack through real ORT, score matching the desktop output, then `closeCase`. One measured field: `quality=11 router=0 preprocess=57 pack=7 total=78` ms (64×64 smoke image, XNNPACK is the default; this test used CPU). The smoke model is random weights and its manifest applies softmax to outputs that are already probabilities, so this proves plumbing, not accuracy. The smoke field scored `positive` 0, so `closeCase` took the `engine.no_rule_matched` → `NEEDS_EXPERT` path; the abnormal-flag path on real output is UNVERIFIED. The accepted-path `field_result` JSON was not run through `validate.py`. **Also on the moto g32 (2026-10-02), 10 more instrumented tests, 15 in total passing:** `FieldPipelineCellsDeviceTest` (cells path with a test `CellFinder`: 3 crops batched in one run, each crop's bbox and score identical to running it alone, empty finder result, missing finder error, and a quality reject through a real `Bitmap` that never reaches the finder) and `TriageEvaluatorDeviceTest` (#19 on real pipeline output: count/score aggregation across 2 fields, max/mean/none, first-match-wins, no-rule-matched, insufficient fields, a quality-rejected field excluded, router mismatch/reject overrides, and the decoder's uncertainty flag turning a normal rule into needs-expert). The smoke model never predicted `positive`, so rules were built from observed counts rather than a real clinical threshold. Not yet run on any other phone model, including the edge 50 fusion.
  - **Done (#21, golden harness):** `PackGoldenTest` (androidTest) runs every `ml/packs/<id>/golden/<name>.png` through `FieldPipeline` and compares with `<name>.json` (a contract `field_result`) via pure-Kotlin `GoldenComparator` within `golden/tolerance.json` (default scores ±0.02, boxes ±2 px, counts exact); one pass/fail row per case, logged under `DeepSightGolden`. 7 comparator JVM tests pass. On a moto g32 (Android 13, 2026-10-02) `:engine:connectedDebugAndroidTest`: `smoke/case1` PASS (expected values derived from the desktop PyTorch output by `ml/eval/make_smoke_golden.py`; a tampered expected score failed as intended); `fungal` and `leukaemia_wbc` FAIL by design (stub 18-byte models, placeholder non-`field_result` expected files, no `CellFinder`). **#21's "all #12 cases pass" box is still open:** #11/#12 have not delivered real malaria goldens. Convention for Track A: PNG inputs (not JPEG), `.json` = full `field_result`, `tolerance.json` optional.
- **Next:**
  - **Known #15 integration gap:** Track A has not delivered the final `malaria_thin` manifest/model. Stage them under the APK's `packs/` assets and repeat the device test with the real pack before G1; real-pack loading is currently UNVERIFIED.
  - **Known #16 parity gap:** compare tensors against #11's Python-reference dumps within 1e-4 when they land; Python/Android parity is currently UNVERIFIED.
  - **Known #17 segmentation gap:** port #10's exact RBC detector when it lands, add the OpenCV Android dependency then measure its APK delta, compare golden counts/boxes, and measure field runtime on a physical Android phone. RBC detection and parity are currently UNVERIFIED.
  - **Known #18 integration gap:** compare decoded objects, scores and uncertainty against #11's real reference outputs when they land; real-model parity is currently UNVERIFIED.
  - **Known #21 gap (issue closed, follow-up tracked here):** add the real `malaria_thin` goldens from #12 to `PackGoldenTest`, run `:engine:connectedDebugAndroidTest` and record every case passing; replace the `fungal` and `leukaemia_wbc` stubs when Track A ships real models. Until then no real pack is golden-tested on a phone.
- **Update, malaria field pipeline (branch `Ashwin-Prakash-dev/c-malaria-field-pipeline`, 2026-10-02):** closes the #15 staging gap, #17, and the real-model part of #18. **Merged into `FieldPipeline` (#54); needs Abhay's review.**
  - **Code:**
    - `engine/.../segmentation/RbcDetector.kt` (+ `NlmHistogram.kt`) is the Kotlin port of `ml/reference/nlm_segmentation.py`, on OpenCV Android 4.14.0. It is GPL-3.0 (LICENSING.md).
    - **`CellFinder` now returns crops (`CellCrop`), not boxes,** so a detector can mask and resize them. A box-only finder returns `CellCropper.crop(field, boxes)`.
    - `pipeline/CellFinders.kt`:
      - `CellFinders.forPack(manifest)` gives the engine's finder: `RbcCellFinder` for `cell_type: rbc`, null otherwise.
      - `RbcCellFinder` cuts NLM-style crops (background black, bicubic to the model input). An NLM retake gives no cells, so triage says NEEDS_EXPERT.
    - `FieldPipeline` times the new step as `cells`.
    - `PackGoldenTest` passes `CellFinders.forPack`.
    - The app's case flow (#30) should call `FieldPipeline(pack, cellFinder = CellFinders.forPack(pack.manifest))`.
    - `:app` stages `ml/packs/*` (only folders that have a `manifest.json`) into the APK's `packs/` assets.
    - Debug-only `DebugAnalyzeActivity` ("DeepSight debug" icon) runs `FieldPipeline` on a picked photo (Android decoder + EXIF rotation): cell boxes, counts, `closeCase` triage, timings.
  - **Softmax:** `malaria_thin`'s `model.onnx` now outputs logits (final Softmax removed by `ml/tools/onnx_logits.py`). `ClassifierDecoder` applies softmax, the convention `make_smoke_golden.py` also encodes, so no engine change was needed. softmax(logits) matches the old probabilities within 3e-8.
  - **Verified on the edge 50 fusion** (`:engine:connectedDebugAndroidTest`, 26 tests; the only failures are 4 by-design `PackGoldenTest` rows, see below):
    - **Same resized input:** the port matches NLM's Java golden (`RbcDetectorTest`).
    - **Full photo → cells:** within ARM/x86 OpenCV resize noise (±1 on ~1% of pixels); synthetic 123 vs 124 cells.
    - **8 RBCNet fields through `FieldPipeline` against desktop Python** (`RbcFieldPipelineTest`):
      - cell counts within 1.1%;
      - 92.6–97.7% of cells match (boxes within 2 segmentation px, scores within 0.02);
      - parasitized counts equal on the 4 positive-patient fields, +1 or +2 on the negative ones.
    - **Abhay's tests:** `FieldPipelineCellsDeviceTest` passes after its test double wraps `CellCropper.crop` and the timing keys include `cells`.
    - **The app itself:** `installDebug`, then the debug screen on an RBCNet field: 215 cells, 6 parasitized, same as Python, 3.2 s.
  - **Measured:**
    - Field time 2.4–3.4 s on the phone: quality ~0.45–0.7 s, cells 1.3–2.1 s, model 0.4–0.6 s.
    - OpenCV adds `libopencv_java4.so` 23.5 MiB + `libc++_shared.so` 1.2 MiB, stored uncompressed. Debug APK 74.1 MiB.
  - **For the team:**
    - **Quality gate (Track D):** `QualityGate` counts the black eyepiece vignette as underexposure (33–37% of every NLM photo) and measures blur over the whole frame.
    - **Golden harness (Abhay):** `PackGoldenTest`'s comparator wants exact counts, but ARM/x86 resize noise moves about 1% of `malaria_thin`'s cells, so a malaria case in the harness format needs a count tolerance in `tolerance.json` or a noise-free fixture.
    - **`PackGoldenTest` (#21):** 4 rows fail by design: `fungal` and `leukaemia_wbc` (stubs), `malaria_thin` (no case in the harness format yet), and a local untracked `breast_breakhis` folder. The other 22 engine device tests pass.
    - **Image decoding (Track B):** the case flow should apply the photo's EXIF orientation when decoding, as the debug screen and cv2.imread do; NLM photos are EXIF-rotated.
  - Aggregation and triage per `contracts/README.md`, as pure Kotlin with JVM tests.

### D: Gates and report (owner: TBD)
- **Done:** Pure-Kotlin quality-gate core accepts an Android-free ARGB pixel buffer, computes Laplacian variance and dark/bright clipped-pixel fractions, and applies each pack's `quality` thresholds. JVM tests cover sharp, blurred, overexposed, dual-clipped, boundary and sparse-field/per-pack cases (`:engine:testDebugUnitTest`).
- **Router guard framework (#23, partial):** `RouterGuard` is injected into `FieldPipeline`; `ScoreRouterGuard` deterministically maps #22's ordered labels and probabilities to `match`, `mismatch` + `predicted`, or `reject`. Mismatch/reject fields skip cell finding and pack inference, produce contract-valid `field_result` JSON, and drive `engine.router_mismatch`/`engine.router_reject`; the result screen shows explicit messages. Verified 2026-10-02 on the Nothing A059: focused engine tests 2/2, full app instrumentation 5/5 (including mismatch/reject Compose tests), `installDebug`, and a successful cold launch. JVM suites pass 79/79 engine and 8/8 app tests; both generated blocked-field JSON files pass `contracts/validate.py`.
- **Router classifier (#22), scaffolding only:** `ml/train/router_split.py` (split-manifest loader: every class needs 2+ sources and a named held-out source; labels derived from the manifest, reject last), `ml/eval/router_eval.py` (accuracy per held-out source, overall and confusion) and `ml/train/train_router.py` (MobileNetV2 backbone, softmax output, ONNX export, `labels.json`, `eval.json`). `ml/tests` pass 17/17 in a throwaway uv venv with `ml/requirements.txt` (not the Conda env; Conda is not installed on that laptop); the torch/ONNX test skips without torch, and the `ml-torch` CI job runs it with the pinned packages. CI also validates every `ml/packs/*/manifest.json` and checks each pack's model file against its manifest sha256 (`ml/tools/check_packs.py`). **Not done:** no model trained and no accuracy number, because the split manifest (#8) does not exist yet; golden match/mismatch/reject cases and the #31 entry are pending that training run. ImageNet backbone weights licence UNVERIFIED (check torchvision's weights terms before shipping).
- **Next:**
  - **Later integration:** match #11's Python reference scores within a stated tolerance once its exact scoring convention and golden outputs land; add the Bitmap/shared image adapter after the joint library decision with #17.
  - **Known #22 gap (issue closed, follow-up tracked here):** once #8's split manifest exists, train the router, report accuracy on the held-out source in #31, add golden match/mismatch/reject cases, and confirm the ImageNet backbone weights licence. Until then the engine router is the always-match stub (#23).
  - **Known #23 gap (issue remains open):** wire #22's trained ONNX model and `labels.json` into `ScoreRouterGuard`, then replace the fallback when #30 connects the real engine. A fungal image under the malaria test is blocked only in synthetic tests; the real-model phone scenario is UNVERIFIED until those assets land.
  - A template report.

### E: Data, eval, clinical thresholds (owner: TBD)
- **Done:** Created `docs/datasets.md` mapping datasets, links, licenses, attributions, and grouping keys for ML packs (issue #5).
- **Next:** sourced triage thresholds for malaria. Every value in the example manifest is a placeholder.

### F: Pitch and demo (owner: TBD)
- **Done:** nothing yet.

## Verified facts
| Fact | How verified |
|---|---|
| DeFungi dataset has 5 classes and no 'normal/no-fungus' class | UCI ML Repository dataset description |
| DeFungi patches can be grouped by source image for a leak-free split | Downloaded UCI zip; filenames encode source image IDs (e.g. `H1_100a_1.jpg` -> source `100a`) |
| Test phone: Nothing A059, Android 16 (API 36), SoC SM7635, arm64-v8a, 7.3 GiB RAM total, ~2.3 GiB available during the S2 run | `adb getprop`, `/proc/meminfo`, 2026-10-02 |
| Test phone storage: 29 GB free | `adb shell df -h /data`, 2026-10-02 |
| ONNX smoke model on Nothing A059: CPU load 2.2 ms, median 0.6 ms batch 1 / 72.1 ms batch 256; XNNPACK load 3.3 ms, median 0.7 ms / 68.3 ms | `OnnxSmokeTest.logTimings`: 3 warmups then median of 10 runs; `connectedDebugAndroidTest`, 2026-10-02 |
| Gemma-4-E2B-it on GPU in AI Edge Gallery 1.0.19: prefill 283.6 tok/s, decode 10.79 tok/s, first token 1.07 s, init 42.5 s first / 17.9 s steady. Model file 2.59 GB. | Measured in the Gallery app; measurement device is UNVERIFIED. Re-run on the Nothing A059 before using this claim. |
| Report latency: about 15 s for 150 tokens, plus init unless the model is preloaded at app start | Arithmetic on the row above |
| Toolchain: Gradle 9.6.0, AGP 9.4.1, Kotlin 2.2.10, compile/target SDK 37, minSdk 24. Gradle provisions JDK 25 itself (foojay). | Builds pass; `gradlew --version` |
| onnxruntime-android 1.30.0 (latest on Maven Central, 2026-09-14) requires minSdk 24 | AAR manifest |
| APKs are arm64-v8a only (`abiFilters`): app 43 MB. ORT's native library is 31.5 MB; all 4 ABIs would be about 129 MB. | APK contents |
| Our app can't read Gallery's copy of the model (Android 11+ scoped storage). It needs its own copy: `adb push` to `/sdcard/Android/data/com.deepsight/files/`. | Android docs; the push path is UNVERIFIED |
| Cleartext HTTP to the hub needs a network security config (blocked by default for targetSdk 28+) | Android network security config docs |
| Ollama listens on 127.0.0.1 by default. Set `OLLAMA_HOST=0.0.0.0` and open port 11434 in the firewall. | Ollama FAQ |

## Open risks
- **Malaria reuse licence:** the upstream root licence is BSD-like, at least 85 source files say GPLv3, and model provenance/licensing is not stated. See `docs/spikes/S1-malaria-screener.md`; do not vendor upstream artefacts until resolved.
- **Memory:** Gemma loaded alongside an ONNX pack with ~2.3 GiB available during S2 is untested. This is the biggest S3 risk; measure on the Nothing A059 with `adb shell dumpsys meminfo com.deepsight`.
- **Hotspot routing (UNVERIFIED):** the phone may route traffic over mobile data when the hotspot has no internet. Test S4 with mobile data off.
- **Malaria model:** upstream extraction and thin-model conversion are feasible, but reuse licensing and active-model parity remain unresolved. A separately licensed dataset and evaluation model are available, but neither clinical quality nor Android parity has been established.

## Decisions
- 2026-10-02: Native Kotlin + Compose, Android only.
- 2026-10-02: Contracts v1.0 frozen. Triage lives on `case_result`, not `field_result`, because it runs after aggregation.
- 2026-10-02: ONNX Runtime and the Python tooling pinned to 1.30.0. Golden outputs depend on this.
- 2026-10-02: APKs are arm64-v8a only.
- 2026-10-02: `test` is the default branch, and all work lands there. `main` only gets commits verified on a physical Android phone, promoted by fast-forward (AGENTS.md, Git).
