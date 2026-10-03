# B-ALL cell classification pack

This private-evaluation pack runs an automatic two-model cascade entirely on the phone:

1. SatellaDet-Blood letterboxes the microscope field to 640×640 and proposes WBC boxes.
2. The engine applies WBC score threshold 0.25, class-agnostic NMS at IoU 0.45, and 10% crop padding.
3. Every retained crop is stretched to 224×224, normalized as `(RGB - 128) / 128`, and sent in one batch to MobileNetV2.
4. The UI shows per-cell B-ALL morphology-like classes and counts. There is no operator-confirmation step.

The four classifier outputs, in model order, are `early_pre_b_like`, `pre_b_like`, `pro_b_like`, and `benign`. They are per-cell image classes from the upstream training set. They do **not** determine a whole-smear diagnosis, acute-leukaemia stage, prognosis, genetic risk, or a clinical blast percentage. The frozen result contract still requires a triage value, so the only deterministic rule is internal `NEEDS_EXPERT`; classification-only screens hide that implementation detail and the generated report. Clinician sign-off remains required.

## Pinned models

- Classifier: upstream MobileNetV2 TFLite at commit `9477169349d2543a107c9f150d48cf6b5417fbdb`, source SHA-256 `df026f86…8a89`; converted to ONNX opset 18 and its final softmax removed. Pack SHA-256: `280223d0…9a5`.
- Detector: `EphAsad/SatellaDet-Blood` revision `116a0432c56d15015992435506c820d8a602a904`. Pack SHA-256: `af8cc48b…2d6b`.

The conversion was checked on three deterministic random tensors: softmax of ONNX logits differed from the source TFLite probabilities by at most `4.887580871582031e-06`, with identical top-1 classes. This verifies conversion parity only, not clinical quality.

The detector model card reports held-out TXL-PBC WBC precision `1.0` and recall `0.834586` over 133 WBCs. DeepSight has not reproduced those numbers. Exact upstream preprocessing, the chosen score/NMS/padding values, cascade accuracy on the classifier's C-NMC domain, operator-crop versus automatic-crop degradation, and patient-grouped classifier performance are all **UNVERIFIED**. Measure them on a patient-separated, smear-level set before any clinical claim.

## Licensing and distribution

The classifier training data is described upstream as C-NMC 2019 under CC BY-NC 4.0. The classifier artifact/model-code licence and SatellaDet ONNX artifact licence are **UNVERIFIED**. Both weights are committed only under the private-repository exception in `AGENTS.md`; remove them before making the repository public unless redistribution rights are confirmed.

## Verification

- Contract validation and pack SHA checks: pass on 2026-10-03.
- Source-TFLite/converted-ONNX parity: pass as described above.
- JVM engine tests and Android source/androidTest compilation: pass on 2026-10-03.
- Physical Android model execution: `LeukaemiaModelsDeviceTest` passed on the rebased `leukamia` branch on a Motorola edge 50 fusion, Android 15/API 35, arm64-v8a (2026-10-03). CPU and XNNPACK both loaded and executed the two packaged ONNX files. Classifier zero-input probabilities matched the desktop reference within `2e-5`, and the detector produced the expected finite `[1,7,33600]` output; the test report recorded `1.643 s` total for both providers.
- Full detector→crop→classifier integration: `LeukaemiaFieldDeviceTest` passed on the same phone for CPU and XNNPACK. The fixed NIH-NLM field passed quality, one WBC crop was retained, and both providers produced `{early_pre_b_like=0, pre_b_like=0, pro_b_like=0, benign=1}`. Measured pipeline totals were `1.623 s` (CPU) and `1.120 s` (XNNPACK) for this one run.
- The integration field is a malaria-dataset blood smear, not leukemia-domain data and not leukemia ground truth. It proves Android cascade wiring and provider regression only; detector recall, classifier/cascade accuracy, clinical validity, operator-crop degradation, memory, and leukemia-domain latency remain **UNVERIFIED**.
- The pack's `min_blur=8.0` and `max_clipped_fraction=0.45` quality limits are provisional values measured for NIH-NLM/RBCNet fields. They are not validated on leukemia images. The pack remains outside `DemoPacks` until leukemia-domain validation and review are complete.
- After explicit authorization, the differently signed existing `com.deepsight` installation was uninstalled and `installDebug` succeeded. Uninstalling erased that installation's app-local data.
