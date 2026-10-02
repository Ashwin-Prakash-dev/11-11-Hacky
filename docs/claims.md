# Pitch and demo claims

Use only rows marked **YES** in the deck or demo. Copy the scoped wording: parity with a reference implementation is not clinical accuracy, one phone is not every supported phone, and model-flagged cells are not confirmed parasites.

## Deck-safe measured claims

| ID | Deck-safe wording | Value | Data / device | How measured | Source commit | Deck safe |
|---|---|---|---|---|---|---|
| C1 | Android ONNX output matched the desktop smoke-model output within the stated tolerance. | CPU and XNNPACK within `1e-5` | Random-weight `64x64` smoke CNN; Nothing A059 | `OnnxSmokeTest`, `connectedDebugAndroidTest` | [`7757aa9`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/7757aa9) | **YES**, engineering parity only |
| C2 | Median smoke-model inference was below 1 ms at batch 1 on the measured phone. | CPU `0.6 ms`; XNNPACK `0.7 ms` | Same smoke CNN; Nothing A059 | 3 warmups, median of 10 runs | [`7757aa9`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/7757aa9) | **YES**, name model, device and batch |
| C3 | At batch 256, the measured smoke-model median was 72.1 ms on CPU and 68.3 ms with XNNPACK. | CPU `72.1 ms`; XNNPACK `68.3 ms` | Same smoke CNN; Nothing A059 | 3 warmups, median of 10 runs | [`7757aa9`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/7757aa9) | **YES**, name model, device and batch |
| C4 | The malaria pack's tensor and PNG parity tests passed on the demo phone. | `3/3`; CPU/XNNPACK max error `6.3e-7`; PNG pipeline below `5e-5` | 32 NIH cell chips; motorola edge 50 fusion | `MalariaPackGoldenTest`, `connectedDebugAndroidTest` | [`00ef2e0`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/00ef2e0) | **YES**, parity fixture, not accuracy |
| C5 | Across 8 RBCNet fields, Android cell counts stayed within 1.1% of desktop Python and 92.6–97.7% of cells matched. | Counts within `1.1%`; matched cells `92.6–97.7%` | 8 fields from one negative and one positive RBCNet patient; edge 50 fusion | `RbcFieldPipelineTest`; boxes within 2 segmentation px and scores within 0.02 | [`d4cd37c`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/d4cd37c) | **YES**, implementation parity only |
| C6 | A malaria field took 2.4–3.4 seconds through the measured phone pipeline. | Total `2.4–3.4 s`; quality `0.45–0.7 s`; cells `1.3–2.1 s`; model `0.4–0.6 s` | 8 RBCNet fields; edge 50 fusion | Instrumented `FieldPipeline` run | [`d4cd37c`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/d4cd37c) | **YES**, one phone and dataset |
| C7 | One debug-screen field produced the same counts as desktop Python in 3.2 seconds. | `215` cells; `6` model-flagged; `3.2 s` | One RBCNet field; edge 50 fusion | `installDebug`, debug field analysis, compared with Python | [`d4cd37c`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/d4cd37c) | **YES**, say model-flagged, not parasitized truth |
| C8 | The Compose case-flow skeleton and disclaimer passed its device navigation walkthrough. | `2/2` device tests | moto g32, Android 13 | `installDebug` and `:app:connectedDebugAndroidTest` | [`1026779`](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/commit/1026779) | **YES**, skeleton uses a fake engine |

## Required gaps and forbidden claims

| Topic | Status | Deck rule | How to verify | Tracking |
|---|---|---|---|---|
| Malaria sensitivity, specificity and AUC | **UNVERIFIED** on a patient-grouped held-out split | Do not quote the upstream kit's figures as DeepSight accuracy | Evaluate the approved model with patients separated and golden/demo patients excluded | [#9](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/9) |
| Router accuracy | **UNVERIFIED**; no trained router result | Do not claim automatic test-type routing works | Train from #8's source-held-out split and record per-source accuracy | [#8](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/8), [#22](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/22) |
| Clinical accuracy and negative-call threshold | **UNVERIFIED**; triage remains provisional | Never present screening output as diagnosis or the current thresholds as clinically validated | Clinical review plus held-out annotated-slide evaluation | [#7](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/7), [#9](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/9) |
| Main case flow using the real engine | **UNVERIFIED / not integrated** | Say the malaria pipeline runs in the debug screen; do not show the main flow as end-to-end | Complete and phone-test real-engine integration | [#30](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/30) |
| Template or LLM report | **UNVERIFIED / not integrated** | Do not show report generation as working | Implement #24; separately verify any optional on-device LLM | [#24](https://github.com/Ashwin-Prakash-dev/11-11-Hacky/issues/24) |
| Two production packs and G2 | **Not complete** | Do not claim two clinically usable packs | Replace stubs, add real goldens, and pass device tests | `docs/STATUS.md` G2 |

## Required wording

- Product: **"Offline microscopy screening support. A clinician always signs off."**
- Compute: **"Phone-only screening for the lightweight modules, with an optional local hub for heavier ones."**
- Current malaria demo: **"A debug-only thin-smear field pipeline with provisional triage; implementation parity was measured, clinical accuracy was not."**
