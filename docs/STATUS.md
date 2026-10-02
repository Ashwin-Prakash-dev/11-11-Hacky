# DeepSight status

**Last updated:** 2026-10-02 (router training scaffolding, docs refresh).
**Hackathon clock:** H0 = TBD. Fill in the start time so everyone can convert H-numbers to clock times.

Rules: AGENTS.md. Edit only your track's section, plus any rows you own. Say how each fact was verified, or mark it UNVERIFIED.

## Gates
| Gate | Due | Definition | State |
|---|---|---|---|
| G1 | H10 | Malaria field image → result on a physical Android phone | not started |
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
- **Next:** independently evaluate the fallback model on the licensed NIH-NLM data, export the chosen model to ONNX, and create the first golden case in `ml/packs/malaria_thin/`. Replace every UNVERIFIED and PLACEHOLDER manifest value before enabling the pack.

### B: Android shell (owner: TBD)
- **Done:** package `com.deepsight`, modules `:app`, `:engine` and `:report`. The stock Compose screen runs on a physical Android phone.
- **Skeleton (issue #26):** choose test → case → result → review/sign-off → history with a fake engine (`FakeEngine`, reads `contracts/examples`); disclaimer on every screen. **Verified on a phone (moto g32, Android 13, 2026-10-02):** `installDebug` installed and `:app:connectedDebugAndroidTest` passed 2/2, including `NavigationTest` (all screens + disclaimer).
- **Done (#27):** gallery import (photo picker, bytes copied unchanged) and CameraX capture (CameraX 1.6.2) save into `filesDir/cases/<caseId>/field_<n>.<ext>`; the files are the fields. **Verified on a moto g32 (Android 13) (2026-10-02):** `installDebug`; an imported PNG was byte-identical to the source, a captured JPEG was written, and both listed as fields on the case screen, with the rest of the skeleton flow still working; `:app:connectedDebugAndroidTest` 2/2, `CaseStoreTest` and `:engine:testDebugUnitTest` pass. Image files survive an app restart; history does not yet (in-memory until #28). It sits on the case screen above the fake engine's field list (the case id comes from the fake engine until #30); Room is not used yet.
- **Case storage (#28):** Room 2.8.5 + KSP 2.3.12 in `:app` (`data/CaseDb.kt`, saving in `App.kt`): sign-off saves the case, its fields and the case_result JSON; saved cases are loaded into the history list at startup. Versions from dl.google.com maven-metadata and Maven Central, 2026-10-02. **Verified on a moto g32 (Android 13) (2026-10-02):** `installDebug`, `:app:connectedDebugAndroidTest`, and a signed-off case still listed in History after force-stop and relaunch (driven with adb).
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
  - Aggregation and triage per `contracts/README.md`, as pure Kotlin with JVM tests.

### D: Gates and report (owner: TBD)
- **Done:** Pure-Kotlin quality-gate core accepts an Android-free ARGB pixel buffer, computes Laplacian variance and dark/bright clipped-pixel fractions, and applies each pack's `quality` thresholds. JVM tests cover sharp, blurred, overexposed, dual-clipped, boundary and sparse-field/per-pack cases (`:engine:testDebugUnitTest`).
- **Router classifier (#22), scaffolding only:** `ml/train/router_split.py` (split-manifest loader: every class needs 2+ sources and a named held-out source; labels derived from the manifest, reject last), `ml/eval/router_eval.py` (accuracy per held-out source, overall and confusion) and `ml/train/train_router.py` (MobileNetV2 backbone, softmax output, ONNX export, `labels.json`, `eval.json`). `ml/tests` pass 17/17 in a throwaway uv venv with `ml/requirements.txt` (not the Conda env; Conda is not installed on that laptop); the torch/ONNX test skips without torch, so CI covers only the split and eval tests. **Not done:** no model trained and no accuracy number, because the split manifest (#8) does not exist yet; golden match/mismatch/reject cases and the #31 entry are pending that training run. ImageNet backbone weights licence UNVERIFIED (check torchvision's weights terms before shipping).
- **Next:**
  - **Later integration:** match #11's Python reference scores within a stated tolerance once its exact scoring convention and golden outputs land; add the Bitmap/shared image adapter after the joint library decision with #17.
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
