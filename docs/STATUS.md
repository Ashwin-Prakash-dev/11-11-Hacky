# DeepSight status

**Last updated:** 2026-10-02 (S3: Gemma in our app on the phone).
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
| S3 | LiteRT-LM Gemma inside our app | D | **yes:** Gemma 4 E2B streams in our own app on the edge 50 fusion (LiteRT-LM 0.17.1, GPU, GPU+MTP and CPU). With the malaria ONNX pack loaded too: 820–982 MiB PSS, 1.5 GiB still available. Warm GPU load 12 s, 50 tokens in 3.7 s with MTP. So the phone report can use Gemma; the template stays the fallback ([evidence](spikes/S3-litertlm-gemma.md)) |
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
  - **In git (private repo only):** `model.onnx`, so the team can test; its licence is unresolved (S1), so it must not leave the private repo. CI's pack-hash check (`ml/tools/check_packs.py`) now checks it. The NIH golden chips stay out of git until the cell_images licence is resolved.
  - **Quality thresholds:** PROVISIONAL `min_blur` 8.0 and `max_clipped_fraction` 0.45, from 8 RBCNet fields (`QualityGate` blur 12.8–15.8; 33–37% black pixels from the vignette). The placeholders (100 / 0.05) rejected every NLM photo.
  - **Field golden:** `golden/field_synthetic.json` holds the Python pipeline's cells and scores on `ml/tests/data/nlm_synthetic.png`, bound to the model by `model_sha256`.
  - **Segmentation:**
    - **Port:** `ml/reference/nlm_segmentation.py` is a faithful Python port of NLM's `MarkerBasedWatershed` and `Cells.runCells`. It is GPL-3.0 ([LICENSING.md](../LICENSING.md)), selected with `--seg nlm`; the old version is `--seg simple`.
    - **Verified bit-exact** against NLM's original Java on OpenCV 3.4.2 (a desktop harness, not in the repo): 0 differing mask pixels and identical cell lists on 8 RBCNet fields.
    - **Per-cell check on the six annotated reserved fields** (`ml/eval/eval_annotated_fields.py`, `nlm`, Python reference, 2026-10-02):
      - **Segmentation:** cell counts within 5% of the annotation; 39 of 40 infected cells inside a detected box.
      - **Sudan model:** flags 28 of 40 infected cells, with 18 false flags among 861 uninfected cells. 14 of them are on one slide (C38P3). The previous model flags 35 of 40, with 64 false flags.
      - **Not accuracy:** 3 reserved patients, and NLM's training overlap is UNVERIFIED. On the edge 50 fusion, the app's `CaseRunner` gives the same counts: parasitized identical on all six fields, cells within 1 (`AnnotatedFieldsDeviceTest`). Table in the [pack README](../ml/packs/malaria_thin/README.md#known-limits).
    - **The earlier RBCNet comparison** (C12N negative vs C92P53 positive, `nlm` 5.6% vs 3.6% flagged) had patient-level labels only, so it mostly measured false flags. It is not evidence that the pipeline can't separate them.
    - **Kotlin port:** done (`RbcDetector.kt`, see Track C).
- **Next:** independently evaluate the fallback model on the licensed NIH-NLM data, export the chosen model to ONNX, and create the first golden case in `ml/packs/malaria_thin/`. Replace every UNVERIFIED and PLACEHOLDER manifest value before enabling the pack.
- **Breast pack `ml/packs/breast_breakhis/` (branch `Ashwin-Prakash-dev/a-breast-pack`, 2026-10-03):** a working demo pack like malaria, with a provisional triage: `malignant_seen` (malignant >= 1) gives `ABNORMAL_FLAG`, and every other case gets `NEEDS_EXPERT`. There is no normal rule on purpose. `DemoPacks` lists it as ready.
  - **Done:** `reference/pipeline.py` (the app's resize, quality gate and `field_result`), `reference/eval_breakhis.py`, `prepare_pack.py` (logits model, manifest hash, golden JSONs), `model_logits.onnx` (sha256 in the manifest), and 6 golden cases. 17 breast tests in `ml/tests` pass, and `validate.py` and `check_packs.py` pass.
  - **Verified on the edge 50 fusion (2026-10-03):** `PackGoldenTest` passes all 6 breast cases (`:engine:connectedDebugAndroidTest`, within 0.02). The app's `CaseRunner` (`BreastCaseDeviceTest`, 3 fields): the two predicted-malignant fields gave `ABNORMAL_FLAG`/`malignant_seen`, the benign one `NEEDS_EXPERT`/`engine.no_rule_matched`, scores within 0.02 of the reference, 0.19–0.74 s per field. The result screen names the prediction instead of drawing boxes (`FieldSummaryTest`).
  - **Finding:** Pillow's anti-aliased resize and the app's resize differ by up to 0.07 in p_malignant on the 6 images, so the earlier goldens would have failed the phone test, so the 90.5% accuracy figure (Pillow) does not hold for the app. Re-measured on all 1,693 BreakHis 400x images with the app's preprocessing: accuracy 89.3%, sensitivity 93.6%, specificity 80.3%, AUC 0.958; not held-out. Details in the pack README.
  - **Not done:** held-out accuracy, a validated triage threshold, a router, a phone-photo domain check. BreakHis redistribution terms are UNVERIFIED (LICENSING.md).

### B: Android shell (owner: TBD)
- **Done:** package `com.deepsight`, modules `:app`, `:engine` and `:report`. The stock Compose screen runs on a physical Android phone.
- **Skeleton (issue #26):** choose test → case → result → review/sign-off → history with a fake engine (`FakeEngine`, reads `contracts/examples`); disclaimer on every screen. **Verified on a phone (moto g32, Android 13, 2026-10-02):** `installDebug` installed and `:app:connectedDebugAndroidTest` passed 2/2, including `NavigationTest` (all screens + disclaimer).
- **Done (#27):** gallery import (photo picker, bytes copied unchanged) and CameraX capture (CameraX 1.6.2) save into `filesDir/cases/<caseId>/field_<n>.<ext>`; the files are the fields. **Verified on a moto g32 (Android 13) (2026-10-02):** `installDebug`; an imported PNG was byte-identical to the source, a captured JPEG was written, and both listed as fields on the case screen, with the rest of the skeleton flow still working; `:app:connectedDebugAndroidTest` 2/2, `CaseStoreTest` and `:engine:testDebugUnitTest` pass. Image files survive an app restart; history does not yet (in-memory until #28). It sits on the case screen above the fake engine's field list (the case id comes from the fake engine until #30); Room is not used yet.
- **Case storage (#28, issue closed 2026-10-02: the moto g32 run meets the AGENTS.md device rule):** Room 2.8.5 + KSP 2.3.12 in `:app` (`data/CaseDb.kt`, saving in `App.kt`): sign-off saves the case, its fields and the case_result JSON; saved cases are loaded into the history list at startup. Versions from dl.google.com maven-metadata and Maven Central, 2026-10-02. **Verified on a moto g32 (Android 13) (2026-10-02):** `installDebug`, `:app:connectedDebugAndroidTest`, and a signed-off case still listed in History after force-stop and relaunch (driven with adb).
- **Result screen + sign-off (#29):** `result/ResultScreen.kt` replaces the skeleton's result and review screens. Per field: quality pass, or reject reasons with Recapture (goes back to the case screen); router message; counts. Case: triage level, rule id, **provisional** badge, uncertainty, report slot ("Report pending (#24)" until the template report lands). Sign-off: clinician name + accept/override (override needs a note), saved into the case row's sign-off columns (`SignOff.applyTo`), which never touches `case_result_json`; the saved sign-off shows again when the result is reopened. The bbox overlay is skipped (no field images in the fake-engine case). **Verified on a moto g32 (Android 13) (2026-10-02):** `:app:testDebugUnitTest` (`SignOffTest`), `:app:connectedDebugAndroidTest` (`NavigationTest` now covers badge, Recapture, name entry, sign-off), and by hand with adb: sign off, force-stop, relaunch, reopen the result: it showed `ACCEPT by DrTest`. The screening-aid line is the disclaimer bar already on every screen.
- **UI polish (branch `Ashwin-Prakash-dev/b-ui-polish`, 2026-10-02):** small changes, mostly in new files, so #30 can still restructure `App.kt`.
  - **Theme:** a fixed teal DeepSight scheme replaces the template purple. Wallpaper-based dynamic colour is off (`DeepSightTheme(dynamicColor = false)`) so triage colours and contrast are the same on every phone.
  - **`ui/components/TriageBadge`:** the result screen shows the exact level string on a red, amber or green surface (`LocalTriageColors`), with a plain-language meaning from `triageStyle()`. Wording is generic across packs and never diagnoses.
  - **`ui/components/DeepSightTopBar`:** a back button whenever there is a previous screen, next to the existing system-back handler. The icon is a vector drawable, so there's no new dependency.
  - **Previews:** `@ThemePreviews` (light and dark) for the badge, the top bar and `ResultScreen`.
  - **Verified on the edge 50 fusion (2026-10-02):** `installDebug`, then all 8 `:app` device tests pass via `am instrument` (`NavigationTest`, new `TopBarNavigationTest` and `TriageBadgeTest`, `RouterResultScreenTest`, `CaseDaoTest`, example). JVM tests pass, including the new `TriageStyleTest` (4). A walkthrough by hand with screenshots was not done, because the phone was in use.
- **Real engine (#30), verified on a moto g32 (Android 13) and the Nothing A059 (Android 16), 2026-10-02:** `FakeEngine` is gone. The picker lists `PackLoader.discover()` packs (rejected ones are logged under `DeepSight`); each case gets a fresh `case-<millis>` id; "Show result" runs every stored image through `CaseRunner` off the main thread (full-resolution decode + EXIF rotation, `FieldPipeline` with `CellFinders.forPack`, then `closeCase`). The last pack's pipeline is kept, so its model loads once; switching packs frees it, and a pack whose model fails to load can't break the next one. Failures and out-of-memory show on the case screen; a run the user backed out of never opens its result; Recapture deletes the rejected image. Field ids are `<caseId>_field_<n>` (Room keys fields by `field_id` alone). **Evidence:** `:app:connectedDebugAndroidTest` 10 pass + 4 `GemmaOnDeviceTest` skipped (no `.litertlm` on the phone): `CaseRunnerTest` 4/4, `NavigationTest`, `ResultScreenTest`, `RouterResultScreenTest` (now reads the contract examples from the test APK), `CaseDaoTest`. `installDebug` walkthrough driven with adb: picker shows Fungal, Leukaemia and Malaria; two NIH RBCNet field photos (5312x2988, imported byte-identical) gave quality pass, router match and `ABNORMAL_FLAG`/`parasite_seen` (provisional): negative patient `239C12NThinF/IMG_20150614_124212` 226 cells, 15 parasitized (Python reference `ml/data/rbcnet_eval/per_image.csv`, Sudan model + NLM segmentation: 225 cells, 14 flagged); positive patient `234C92P53ThinF/IMG_20150821_150718` 215 cells, 6 parasitized (reference 217, 6). `timing_ms` total 7.3 s and 5.0 s per field (cells 4.0/2.4 s, quality 1.6/1.2 s). Accept and override sign-offs, both cases in History, and their Room rows (signer, decision, note, image path, unchanged triage) survived force-stop + relaunch. A malaria photo in the fungal pack was rejected (blur, underexposed) → `NEEDS_EXPERT`/`engine.insufficient_fields`, Recapture deleted it; malaria then ran again in the same session with the same result. **Nothing A059:** the installed APK pulled back off the phone carries `malaria_thin/model.onnx` with sha256 `4ae01239…`, identical to the repo and the manifest, and no `FakeEngine` class; the positive-patient field gave the same 215 cells / 6 parasitized in about 7 s. **Not checked:** the stub-model ORT failure in the app UI (covered on device by `CaseRunnerTest` with a broken pack), release-build memory (no `largeHeap`), the edge 50 fusion demo phone. **The wiring matches the Python reference; the triage does not separate these patients:** the model flags ~3-7% of cells on both, and `parasite_seen` fires at 1 parasitized cell (Track A/E).
- **Demo app on branch `eval` (2026-10-02, kept off `test` on purpose):** the full flow (photo → real pack → triage → Gemma report → sign-off → history), with the UI redesigned along the guidance in `claude-android-skill` (MIT, kept outside the repo).
  - **Architecture:** `AppViewModel` holds the back stack and the UI state (`CaseUiState`, `ResultUiState`, `ReportUiState`) as `StateFlow`, collected with `collectAsStateWithLifecycle`. Screens are stateless (`home/`, `capture/CaseScreen`, `result/`, `history/`, `about/`), so state survives rotation. No Hilt or Navigation library: `CaseRunner` and `ReportService` are process singletons. `App.kt` is now a thin router; the case logic from #30 moved into the ViewModel with the same behaviour.
  - **Design system:**
    - `ui/theme`: teal scheme, type scale, shapes, triage colours.
    - `ui/DeepSightIcons`: material-icons-core plus six Material icon drawables.
    - `ui/components`: disclaimer bar, pills, stat tiles, empty state, and `FieldImage`, which draws the model's cell boxes on the photo.
    - Light/dark previews.
  - **Screens:**
    - **Home:** on-device and AI-report status, and the packs. Packs not yet validated on a phone are shown but can't be opened (`DemoPacks`).
    - **Case:** step indicator, import/capture, thumbnail grid, per-field progress.
    - **Result:** triage badge, stats, streaming report, field photos with flagged cells, and a sign-off that waits for the report.
    - **History:** a read-only detail that shows the report the clinician saw.
    - **About:** GPL notices, licence text, NLM credit.
  - **Data:** Room v2 adds `report_text` and `report_source` to `cases` (`CaseDb.MIGRATION_1_2`).
  - **Offline:** the manifest removes the INTERNET and ACCESS_NETWORK_STATE permissions declared by onnxruntime-android 1.30.0 and media3-common. The APK requests only CAMERA (`aapt2 dump permissions`).
  - **New libraries (shared catalog):** lifecycle-runtime-compose and lifecycle-viewmodel-compose 2.6.1 (the project's lifecycle version), and material-icons-core (BOM, 1.7.8).
  - **Verified on the edge 50 fusion (2026-10-02):**
    - **Device tests:** after `installDebug`, all 22 `:app` device tests pass via `am instrument` (every one except the S3 benchmarks):
      - `NavigationTest`, updated to open the first validated pack and press "Analyse";
      - `TopBarNavigationTest`;
      - new `HomeAndAboutTest`: unvalidated packs stay on Home, no network permission, and the About notices, licence and NLM notice open;
      - new `ReportCardTest`;
      - `ResultScreenTest`, `RouterResultScreenTest`, `TriageBadgeTest`;
      - new `CaseDbMigrationTest`, on a real v1 database;
      - `CaseRunnerTest`, `CaseDaoTest`, `AnnotatedFieldsDeviceTest`;
      - new `PipelineReportDeviceTest`.
    - **JVM tests:** 110 pass (engine, report, app).
    - **By hand on the phone:** an imported field gave quality pass, 28 parasitized and 388 uninfected, `ABNORMAL_FLAG`, the cell overlay and a Gemma report, analysed in 8.7 s.
  - **Known gaps:**
    - Process death loses a case in progress (no `SavedStateHandle`).
    - Release-build memory is unchecked (no `largeHeap`).
    - Compose's `LocalLifecycleOwner` shows a deprecation warning until Lifecycle is 2.8 or later.
    - Not run on another phone model.
- **Patients and the FCFS case queue (#64, branch `Abhay-Mmmm/b-patient-records-and-a-sequential-fcfs-case-que`, 2026-10-03):**
  - **Base:** built on Ashwin's unmerged `b-profiles-ui` (Profiles screen) and `b-analysis-timestamp` (Room v3, `analysed_at`). It replaces #63/PR #66's UI. Merge those two first; if `MIGRATION_2_3` changes, renumber this one.
  - **Room v4 (`MIGRATION_3_4`):**
    - A `patients` table: UID `P-XXXX-XXXX` (Crockford base32, `SecureRandom`, retried on a clash), name, ISO date of birth and sex. These are the fields the Profiles screen shows; blood group was dropped.
    - Cases gain `patient_uid` (foreign key, no cascade), `status` (old rows become `SIGNED`) and `error`.
    - `Patient` validates itself and is the only validator.
  - **Flow:** choose test → choose patient or add one → case.
    - **Analyse:** submits the batch to `CaseQueue`, which runs one batch at a time, first in first out, and survives leaving the screen. Restart recovery re-runs `QUEUED`/`RUNNING` rows. A failed batch is marked `FAILED` and the queue moves on.
    - **History:** a finished, unsigned case opens for sign-off.
    - **Sign-off:** an UPDATE (`CaseDao.sign`), not a REPLACE.
    - **Profiles:** the screen reads Room.
  - **Backup:** the database and `files/cases/` are excluded from cloud backup and device transfer.
  - **Tests:**
    - JVM, written first and passing: `PatientTest` (8), `ProfilesOfTest` (2).
    - New device tests, written first: `CaseDbMigrationTest` (v3 → v4), `PatientDaoTest`, `CaseQueueTest` (fake runner: order, failure, restart, recapture, sign-off), `QueueParityDeviceTest` (real malaria pack; needs a field photo pushed as `queue_parity.jpg`).
    - Updated for the patient step: `NavigationTest` and `ProfilesScreenTest`. `ProfilesNavigationTest` seeds Room instead of using `SampleProfiles`. `NavigationTest` and `TopBarNavigationTest` now scroll to History, which the Profiles card pushed below the fold.
    - **Verified on the edge 50 fusion (Android 15, 2026-10-03):** after `installDebug installDebugAndroidTest`, the full `:app` device suite passed through `am instrument`: OK (46 tests). These are skipped because their files aren't on the phone: Annotated, Breast, Gemma, PipelineReport.
    - The phone had another machine's debug signature, so the app was uninstalled first. Its data was a v2 database with no cases plus 2 photos, backed up locally first.
  - **Walkthrough on the edge 50 fusion (2026-10-03), driven with adb; Room checked after each step:**
    - **Migration:** the phone's own v2 database opened as v4.
    - **New patients:** 2 created, getting `P-V9JH-XPR4` and `P-WXZY-KRCW`.
    - **Back out:** backing out during a 4-field run, that run finished `DONE` in the background.
    - **Queue:** patient 2's batch, submitted during patient 1's 8-field run, showed "Queued behind 1 batch".
    - **Force-stop:** the app was force-stopped with batch 1 `RUNNING` and batch 2 `QUEUED`. After relaunch, both finished in submit order with the right `patient_uid`. Another force-stop mid-run also recovered (4/4 fields).
    - **Sign-off from History:** "Ready for sign-off" opened the result without Recapture. The case became `SIGNED`, keeping its `patient_uid`, field and `analysed_at`.
  - **Measured on the edge 50 fusion:**
    - **Disk:** about 2.6 MB per imported NIH field (5312x2988 JPEG, stored unchanged) and about 1 MB per camera capture. Room adds about 29 KB per field; the database was 1.4 MB for 10 cases and 28 fields (`du`, sqlite).
    - **Camera:** a capture during a running 8-field batch succeeded. Preview smoothness was not measured.
  - **UNVERIFIED (moto g32):** the same MB-per-patient figure, and whether CameraX stays usable during inference on a low-end phone.
- **Next:** decide whether `eval` merges into `test`; the G1 gate row (not Track B's) can now point at the main case flow.

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
- **Router guard framework (#23, partial):** `RouterGuard` is injected into `FieldPipeline`; `ScoreRouterGuard` deterministically maps #22's ordered labels and probabilities to `match`, `mismatch` + `predicted`, or `reject`. Mismatch/reject fields skip cell finding and pack inference, produce contract-valid `field_result` JSON, and drive `engine.router_mismatch`/`engine.router_reject`; the result screen shows explicit messages. Verified 2026-10-02 on the Nothing A059: before the final rebase the full app instrumentation suite passed 5/5; after rebasing onto the merged malaria pipeline, focused engine tests passed 2/2, focused mismatch/reject Compose tests passed 2/2, `installDebug` succeeded and the app cold-launched. JVM suites pass 82/82 engine and 8/8 app tests; both generated blocked-field JSON files pass `contracts/validate.py`.
- **Router classifier (#22), scaffolding only:** `ml/train/router_split.py` (split-manifest loader: every class needs 2+ sources and a named held-out source; labels derived from the manifest, reject last), `ml/eval/router_eval.py` (accuracy per held-out source, overall and confusion) and `ml/train/train_router.py` (MobileNetV2 backbone, softmax output, ONNX export, `labels.json`, `eval.json`). `ml/tests` pass 17/17 in a throwaway uv venv with `ml/requirements.txt` (not the Conda env; Conda is not installed on that laptop); the torch/ONNX test skips without torch, and the `ml-torch` CI job runs it with the pinned packages. CI also validates every `ml/packs/*/manifest.json` and checks each pack's model file against its manifest sha256 (`ml/tools/check_packs.py`). **Not done:** no model trained and no accuracy number, because the split manifest (#8) does not exist yet; golden match/mismatch/reject cases and the #31 entry are pending that training run. ImageNet backbone weights licence UNVERIFIED (check torchvision's weights terms before shipping).
- **Next:**
  - **Later integration:** match #11's Python reference scores within a stated tolerance once its exact scoring convention and golden outputs land; add the Bitmap/shared image adapter after the joint library decision with #17.
  - **Known #22 gap (issue closed, follow-up tracked here):** once #8's split manifest exists, train the router, report accuracy on the held-out source in #31, add golden match/mismatch/reject cases, and confirm the ImageNet backbone weights licence. Until then the engine router is the always-match stub (#23).
  - **Known #23 gap (issue remains open):** wire #22's trained ONNX model and `labels.json` into `ScoreRouterGuard`, then replace the fallback when #30 connects the real engine. A fungal image under the malaria test is blocked only in synthetic tests; the real-model phone scenario is UNVERIFIED until those assets land.
  - ~~A template report.~~ Done on branch `eval` (below).
- **S3, Gemma on LiteRT-LM (branch `Ashwin-Prakash-dev/s3-litertlm-gemma`, 2026-10-02):** details in [docs/spikes/S3-litertlm-gemma.md](spikes/S3-litertlm-gemma.md).
  - **Code:**
    - `report/.../gemma/GemmaRunner.kt`: loads a `.litertlm` from app storage (GPU or CPU, optional MTP) and streams text. It knows nothing about triage.
    - Debug-only `DebugGemmaActivity` ("DeepSight Gemma" icon): backend and MTP chips, an optional malaria ONNX pack, streaming output and timings.
    - `GemmaOnDeviceTest` (`:app` androidTest): GPU, GPU+MTP, CPU, and memory with the ONNX pack.
  - **Verified on the edge 50 fusion** (`am instrument` and the debug screen; not on the Nothing A059): all 4 Gemma tests pass and text streams on screen. Timings and meminfo are in the spike file.
  - **For the team:**
    - **Don't run `:app:connectedDebugAndroidTest` with the model on the phone.** It uninstalls the app, which deletes the 2.6 GB model. Use `am instrument` (spike file, How to reproduce).
    - **Kotlin:** LiteRT-LM 0.17.1 is a Kotlin 2.4 binary. `:report` skips the metadata check, and the app's runtime kotlin-stdlib is now 2.4.0. Upgrading the project to Kotlin 2.4 is the proper fix (team decision).
    - **The report (#24) should:**
      - load Gemma at app start on GPU with MTP, with the app's `cacheDir`;
      - keep the exact-triage-string check and the template fallback (`docs/architecture.md`).
      Gemma's output mostly restated the facts, so the prompt needs work.

- **Report (branch `eval`, 2026-10-02):** a template and Gemma narration, wired into the app.
  - **`:report` `CaseReport.kt`:**
    - `templateReport`: deterministic; the fallback.
    - `gemmaPrompt`: facts only and the exact level; Gemma never sees the other level names.
    - `checkNarrative`: the exact level and no other (docs/architecture.md, Report).
    - `cleanNarrative`, and `triageMeaning`, which the triage badge shares.
    - `CaseReportTest`: 7 JVM tests.
  - **`GemmaRunner.generate`** has a time limit and cancellation (`Conversation.cancelProcess`; throws `GemmaStopped`).
  - **App `ai/ReportService.kt`:**
    - `GemmaNarrator` loads Gemma at app start on GPU with MTP, S3's fastest setup.
    - `ReportWriter` waits up to 45 s for it, then streams, cleans and checks the text. Anything that fails, or no model, gives the template with the reason.
    - `ReportWriterTest`: 7 JVM tests.
  - **Measured on the edge 50 fusion** (`PipelineReportDeviceTest`, 3 runs):
    - **Timings:** `CaseRunner` took 2.2–2.9 s per field. Gemma loaded in the background in 11.8–14.6 s, then streamed the report in 5.2–6.2 s. Every run passed the check (source GEMMA).
    - **Output:** the same text in all 3 runs: "The malaria thin smear test was performed. 1 of 1 fields passed the image-quality check. Model counts across the passed fields showed 9 parasitized and 101 uninfected. The triage level is ABNORMAL_FLAG, which means a screening rule flagged this case and a clinician should review it."
    - **Sampling:** whether LiteRT-LM's default sampling is greedy is UNVERIFIED.

### E: Data, eval, clinical thresholds (owner: TBD)
- **Done:** Created `docs/datasets.md` mapping datasets, links, licenses, attributions, and grouping keys for ML packs (issue #5).
- **Done (#6, field assets):** independently read the NIH-NLM ThinBloodSmearsPf licence DOCX and README; retained its notice and attribution in `ml/fixtures/`. `malaria_fields.json` pins 3 golden fields (15/0/9 annotated parasitized RBCs), 3 separate demo fields and 2 reproducible synthetic failures, with image/annotation URLs and SHA-256 digests. `ml/tools/prepare_malaria_fields.py` fetches everything under ignored `ml/data/`; a fresh fetch and a network-disabled repeat verified identical generated hashes. The reserved patient keys `C70P31`, `C7N`, `C38P3` are rejected by the router split loader and reusable split validator, including their other fields/crops. Python suite 53/53 passed in the project Conda environment, including the trainer/export regression; existing CI discovers the new tests. JVM `ReservedMalariaFieldsTest` verified all six originals pass the unchanged quality gate, and both failure variants reject; engine suite 74/74. Counts, commands and exact rejection reasons are in [datasets.md](datasets.md#reserved-malaria-fields-6). These are infected-cell counts, not individual parasite counts. Physical-phone use of these selected images and model-output goldens remain separate G1 integration work; upstream pretrained-model patient overlap is UNVERIFIED.
- **Done (#7, sourcing):** reviewed WHO MM-SOP-08/09 and NLM's configurable capture target; [malaria threshold evidence](../ml/packs/malaria_thin/README.md#threshold-evidence-issue-7) records the thick-film negative examination minimum and why thin-film counting does not validate this pack's normal-screen rule. `triage.source` individually labels unsupported settings PROVISIONAL. In the `deepsight` Conda environment, the contract validator passed all pack manifests, the pack-integrity check passed, and `:engine:testDebugUnitTest` passed; semantic comparison against the rebased parent commit confirmed only `triage.source` changed. App presentation checked by source inspection only; no new phone-run claim.
- **Done (#31, claims registry):** [docs/claims.md](claims.md) separates 8 deck-safe measured engineering claims from unverified clinical, router, report and integration claims. Each measured number names its scope, method and source commit; missing model/router metrics remain UNVERIFIED with the issue that must measure them.
- **Next (#7, acceptance):** external deck compliance is UNVERIFIED (no deck in this checkout); Track F must audit it before the presentation checkbox can be completed. The automated thin-smear negative-call minimum and uncertainty calibration remain UNVERIFIED; obtain clinical review and held-out validation before changing values.

### F: Pitch and demo (owner: TBD)
- **Done (#32, draft):** generated an 8-slide Figma Slides pitch constrained to the deck-safe wording and claim IDs in [docs/claims.md](claims.md).
- **Next (#32, audit):** select one generated deck, record its URL, and audit every slide against `docs/claims.md`; no deck is approved until that inspection passes.

## Verified facts
| Fact | How verified |
|---|---|
| DeFungi dataset has 5 classes and no 'normal/no-fungus' class | UCI ML Repository dataset description |
| DeFungi patches can be grouped by source image for a leak-free split | Downloaded UCI zip; filenames encode source image IDs (e.g. `H1_100a_1.jpg` -> source `100a`) |
| Test phone: Nothing A059, Android 16 (API 36), SoC SM7635, arm64-v8a, 7.3 GiB RAM total, ~2.3 GiB available during the S2 run | `adb getprop`, `/proc/meminfo`, 2026-10-02 |
| Test phone storage: 29 GB free | `adb shell df -h /data`, 2026-10-02 |
| ONNX smoke model on Nothing A059: CPU load 2.2 ms, median 0.6 ms batch 1 / 72.1 ms batch 256; XNNPACK load 3.3 ms, median 0.7 ms / 68.3 ms | `OnnxSmokeTest.logTimings`: 3 warmups then median of 10 runs; `connectedDebugAndroidTest`, 2026-10-02 |
| Gemma-4-E2B-it on GPU in AI Edge Gallery 1.0.19: prefill 283.6 tok/s, decode 10.79 tok/s, first token 1.07 s, init 42.5 s first / 17.9 s steady. Model file 2.59 GB. | Measured in the Gallery app; measurement device is UNVERIFIED. Re-run on the Nothing A059 before using this claim. |
| Gemma 4 E2B in our app on the edge 50 fusion (LiteRT-LM 0.17.1, 94-token prompt, 50 output tokens): GPU+MTP warm load 12.0 s, first text 1.5 s, done in 3.7 s (decode 14.8 tok/s). GPU without MTP decodes at 8.7 tok/s. The first load after install is 27 s on GPU and 52 s on CPU. | `GemmaOnDeviceTest` and `DebugGemmaActivity`, wall clock, 2026-10-02 ([S3](spikes/S3-litertlm-gemma.md)). LiteRT-LM's own init time reads 2.0× wall clock |
| Gemma (GPU) and the malaria ONNX pack loaded together: 982 MiB PSS (device test) / 820 MiB (debug screen), with 1.46–1.55 GiB still available. lowmemorykiller kills 3–6 background apps during each Gemma load, never ours. | `dumpsys meminfo com.deepsight`, `/proc/meminfo`, logcat, 2026-10-02 (S3) |
| LiteRT-LM `litertlm-android` 0.17.1: AAR minSdk 24, Apache-2.0, `liblitertlm_jni.so` 20.8 MiB (arm64). It is a Kotlin 2.4 binary, so the Kotlin 2.2.10 compiler rejects it unless the metadata check is skipped. | AAR manifest, POM, APK contents, compile error (S3) |
| Gemma model: `gemma-4-E2B-it.litertlm` from Hugging Face `litert-community/gemma-4-E2B-it-litert-lm`, 2,588,147,712 bytes, sha256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`, Apache-2.0, ungated | Hugging Face model API; `sha256sum` on the phone matches (S3) |
| Toolchain: Gradle 9.6.0, AGP 9.4.1, Kotlin 2.2.10, compile/target SDK 37, minSdk 24. Gradle provisions JDK 25 itself (foojay). | Builds pass; `gradlew --version` |
| onnxruntime-android 1.30.0 (latest on Maven Central, 2026-09-14) requires minSdk 24 | AAR manifest |
| APKs are arm64-v8a only (`abiFilters`): app 43 MB. ORT's native library is 31.5 MB; all 4 ABIs would be about 129 MB. | APK contents |
| Our app can't read Gallery's copy of the model (Android 11+ scoped storage). It needs its own copy: `adb push` to `/sdcard/Android/data/com.deepsight/files/`, and the app reads it from there. | Scoped storage: Android docs. Push path: on the edge 50 fusion the app loaded a copy there owned by the adb shell user, and `adb push` writes files with the same owner (S3, 2026-10-02) |
| Cleartext HTTP to the hub needs a network security config (blocked by default for targetSdk 28+) | Android network security config docs |
| Ollama listens on 127.0.0.1 by default. Set `OLLAMA_HOST=0.0.0.0` and open port 11434 in the firewall. | Ollama FAQ |

## Open risks
- **Malaria reuse licence:** the upstream root licence is BSD-like, at least 85 source files say GPLv3, and model provenance/licensing is not stated. See `docs/spikes/S1-malaria-screener.md`; do not vendor upstream artefacts until resolved.
- **Memory:** measured on the edge 50 fusion (S3): Gemma and the malaria ONNX pack fit (982 MiB PSS, ~1.5 GiB left), but loading Gemma makes Android kill background apps. Not measured on the Nothing A059 (same 7.3 GiB RAM) or on any smaller phone. Close other apps before a demo.
- **Kotlin 2.4:** LiteRT-LM 0.17.1 needs `-Xskip-metadata-version-check` in `:report`, and the app's runtime kotlin-stdlib is now 2.4.0 while the compiler is 2.2.10. Upgrading the project to Kotlin 2.4 is the proper fix (team decision; shared `libs.versions.toml`).
- **Hotspot routing (UNVERIFIED):** the phone may route traffic over mobile data when the hotspot has no internet. Test S4 with mobile data off.
- **Malaria model:** upstream extraction and thin-model conversion are feasible, but reuse licensing and active-model parity remain unresolved. A separately licensed dataset and evaluation model are available, but neither clinical quality nor Android parity has been established.

## Decisions
- 2026-10-02: Native Kotlin + Compose, Android only.
- 2026-10-02: Contracts v1.0 frozen. Triage lives on `case_result`, not `field_result`, because it runs after aggregation.
- 2026-10-02: ONNX Runtime and the Python tooling pinned to 1.30.0. Golden outputs depend on this.
- 2026-10-02: APKs are arm64-v8a only.
- 2026-10-02: `test` is the default branch, and all work lands there. `main` only gets commits verified on a physical Android phone, promoted by fast-forward (AGENTS.md, Git).
- 2026-10-02: The repo is private, so the team's model weights and sample data are committed (`ml/models/*.onnx`, `ml/data/`, the malaria pack's `model.onnx` and golden chips) so everyone can test. Their licences are UNVERIFIED; take them out of git before the repo goes public ([LICENSING.md](../LICENSING.md), AGENTS.md exception). The `breast_breakhis` model and its 6 golden images are committed too, on the same terms (branch `Ashwin-Prakash-dev/a-breast-pack`).
