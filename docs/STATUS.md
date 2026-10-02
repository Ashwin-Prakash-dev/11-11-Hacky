# DeepSight status

**Last updated:** 2026-10-02 (S1 fallback setup).
**Hackathon clock:** H0 = TBD. Fill in the start time so everyone can convert H-numbers to clock times.

Rules: AGENTS.md. Edit only your track's section, plus any rows you own. Say how each fact was verified, or mark it UNVERIFIED.

## Gates
| Gate | Due | Definition | State |
|---|---|---|---|
| G1 | H10 | Malaria field image → result on the demo phone | not started |
| G2 | H18 | 2 packs + router + quality gate + report | not started |

**Cut lines:**
- G1 missed by H12: everyone moves to malaria.
- G2 missed by H20: switch to a rule-based router and a template report.

## Spikes
| Spike | Question | Owner | State |
|---|---|---|---|
| S1 | NLM Malaria Screener: licence, can the model be extracted, does it convert to ONNX? | Codex | **partial:** thin TFLite conversion works; upstream reuse remains blocked, while a licensed dataset and evaluation-model fallback are pinned; first pack golden case still pending ([evidence](spikes/S1-malaria-screener.md)) |
| S2 | ONNX Runtime on Android | C | **code done; phone run pending** (see Track C) |
| S3 | LiteRT-LM Gemma inside our app | TBD | not started |
| S4 | Phone → laptop hub over hotspot, cleartext HTTP | TBD | not started |
| S5 | DeFungi classes | TBD | not started |
| S6 | PathOS conversion | TBD | not started |

## Tracks

### A: ML packs (owner: TBD)
- **Done:** S1 repository/model inspection and thin TFLite conversion proof. The test-first setup/downloader creates the project Conda environment, verifies pinned checksums, excludes unverified candidates by default, and loads the approved MobileNetV2 state dictionary. No dataset or model weight is committed.
- **Next:** independently evaluate the fallback model on the licensed NIH-NLM data, export the chosen model to ONNX, and create the first golden case in `ml/packs/malaria_thin/`. Replace every UNVERIFIED and PLACEHOLDER manifest value before enabling the pack. After the priority path is stable, add CI for contract validation and JVM tests.

### B: Android shell (owner: TBD)
- **Done:** package `com.deepsight`, modules `:app`, `:engine` and `:report`. The stock Compose screen runs on the demo phone.
- **Next:** case flow: pick test type → CameraX capture or gallery import → result screen.

### C: On-device engine (owner: TBD)
- **Done:**
  - Contract types and JSON in `engine/.../contract/`. 4/4 JVM tests pass, and the JSON they write passes `validate.py`.
  - S2 code:
    - `engine/.../onnx/OnnxModel.kt` runs on CPU or XNNPACK (onnxruntime-android 1.30.0).
    - The golden test `OnnxSmokeTest` uses `ml/eval/make_smoke_model.py`: a 64×64 cell-crop-sized CNN with random weights. The exported model matches PyTorch to within 1e-5.
    - It builds; it has **not yet run on the phone**.
- **Next:**
  - Run `:engine:connectedDebugAndroidTest`, then record the timings below.
  - After that: aggregation and triage per `contracts/README.md`, as pure Kotlin with JVM tests.

### D: Gates and report (owner: TBD)
- **Done:** nothing yet.
- **Next:**
  - Quality gate (variance of Laplacian, histogram clipping), using thresholds from the manifest's `quality` block.
  - A template report.

### E: Data, eval, clinical thresholds (owner: TBD)
- **Done:** nothing yet.
- **Next:** sourced triage thresholds for malaria. Every value in the example manifest is a placeholder.

### F: Pitch and demo (owner: TBD)
- **Done:** nothing yet.

## Verified facts
| Fact | How verified |
|---|---|
| Demo phone: motorola edge 50 fusion, Android 15 (API 35), SoC SM7435, arm64-v8a, 7.3 GiB RAM total, ~2.5 GiB available at idle, OpenCL present | adb getprop, /proc/meminfo |
| Demo phone storage: 8.2 GB free | adb df, 2026-10-02 |
| Gemma-4-E2B-it on GPU in AI Edge Gallery 1.0.19: prefill 283.6 tok/s, decode 10.79 tok/s, first token 1.07 s, init 42.5 s first / 17.9 s steady. Model file 2.59 GB. | Measured in the Gallery app. Gallery is installed on the demo phone, so it was probably measured there (confirm). |
| Report latency: about 15 s for 150 tokens, plus init unless the model is preloaded at app start | Arithmetic on the row above |
| Toolchain: Gradle 9.6.0, AGP 9.4.1, Kotlin 2.2.10, compile/target SDK 37, minSdk 24. Gradle provisions JDK 25 itself (foojay). | Builds pass; `gradlew --version` |
| onnxruntime-android 1.30.0 (latest on Maven Central, 2026-09-14) requires minSdk 24 | AAR manifest |
| APKs are arm64-v8a only (`abiFilters`): app 43 MB. ORT's native library is 31.5 MB; all 4 ABIs would be about 129 MB. | APK contents |
| Our app can't read Gallery's copy of the model (Android 11+ scoped storage). It needs its own copy: `adb push` to `/sdcard/Android/data/com.deepsight/files/`. | Android docs; the push path is UNVERIFIED |
| Cleartext HTTP to the hub needs a network security config (blocked by default for targetSdk 28+) | Android network security config docs |
| Ollama listens on 127.0.0.1 by default. Set `OLLAMA_HOST=0.0.0.0` and open port 11434 in the firewall. | Ollama FAQ |

## Open risks
- **Malaria reuse licence:** the upstream root licence is BSD-like, at least 85 source files say GPLv3, and model provenance/licensing is not stated. See `docs/spikes/S1-malaria-screener.md`; do not vendor upstream artefacts until resolved.
- **Memory:** Gemma loaded alongside an ONNX pack on ~2.5 GiB of available RAM is untested. This is the biggest S3 risk; measure with `adb shell dumpsys meminfo com.deepsight`.
- **Hotspot routing (UNVERIFIED):** the phone may route traffic over mobile data when the hotspot has no internet. Test S4 with mobile data off.
- **Malaria model:** upstream extraction and thin-model conversion are feasible, but reuse licensing and active-model parity remain unresolved. A separately licensed dataset and evaluation model are available, but neither clinical quality nor Android parity has been established.

## Decisions
- 2026-10-02: Native Kotlin + Compose, Android only.
- 2026-10-02: Contracts v1.0 frozen. Triage lives on `case_result`, not `field_result`, because it runs after aggregation.
- 2026-10-02: ONNX Runtime and the Python tooling pinned to 1.30.0. Golden outputs depend on this.
- 2026-10-02: APKs are arm64-v8a only.
