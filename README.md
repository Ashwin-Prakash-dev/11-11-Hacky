# DeepSight

An offline Android app for microscopy screening and triage. A health worker picks a test type, captures or imports microscope field images, and gets a quality check, a triage flag and a short report. No internet is needed, and a clinician always signs off. It supports screening; it does not diagnose.

Built in a 30-hour hackathon by four people and their agents. What works today, and what is still open: [docs/STATUS.md](docs/STATUS.md).

## How it works

```text
pick test → capture or import fields → quality gate → router guard → ONNX pack → aggregate → triage → report → clinician sign-off
```

- **Packs:** each disease is a pack in `ml/packs/<id>/` with a manifest, an ONNX model and golden tests. Nothing else in the app knows about a specific disease.
- **Triage is rules only.** The thresholds live in each pack's manifest. The report model narrates the result and can never change the triage level.
- **The user picks the test.** The router guard only checks that the image matches it, and flags a mismatch or an image that is no known test.
- **Everything runs on the phone**, including the report model. The app requests no internet permission: the network permissions some libraries declare are removed in its manifest. (`compute: hub` packs would need a laptop hub; none ships in the demo.)

Details: [docs/architecture.md](docs/architecture.md).

## Repository layout

| Path | What |
|---|---|
| `android/` | Gradle root: `:app` (Compose UI), `:engine` (contracts, runtimes, gates, triage), `:report` |
| `contracts/` | Frozen v1.0 JSON Schemas for manifests and results, examples, `validate.py` |
| `ml/packs/` | One folder per disease pack |
| `ml/train`, `ml/eval` | Training, export and evaluation scripts (router classifier, smoke model) |
| `ml/models.json` | Pinned external evaluation models with checksums and licence status |
| `scripts/` | `setup_dev.sh` and the Android SDK installers |
| `docs/` | Status, architecture, datasets, spike write-ups |

## Development setup

Requires Conda. The setup script creates the `deepsight` environment; installs OpenJDK 25, the pinned Python packages, and the Google Android SDK toolchain; and downloads checksum-verified models approved for evaluation. The SDK bootstrap supports Intel/Apple Silicon macOS and x86_64 Linux/Windows. Model weights stay untracked.

```sh
scripts/setup_dev.sh
conda activate deepsight
```

Run `scripts/setup_dev.sh --help` for a different environment name or to skip dependencies, the Android SDK or models. Android command-line tools, platform-tools 37.0.1, API 37.0 and Build Tools 36.0.0 are pinned to Google's versioned packages and checksums; their use is subject to the [Android SDK terms](https://developer.android.com/studio/terms). Candidates with unresolved licensing or provenance require the explicit `--include-unverified` option.

Never install the Python packages into global Python. ONNX needs protobuf 6.31 or newer, which breaks other globally installed packages.

## Run it

Requires an arm64 Android phone (Android 7.0+, API 24) with USB debugging on. There is no emulator workflow.

```sh
cd android
./gradlew installDebug          # Windows: gradlew.bat installDebug
adb shell am start -n com.deepsight/.MainActivity
```

**The demo flow.** Home → pick a validated test (packs not yet validated on a phone are listed but can't be opened) → **Import image** or **Capture** one or more fields → **Analyse** (progress per field) → result: triage badge, counts, each field with the model's cell boxes drawn on it, and the report → clinician sign-off → **History**, where signed-off cases reopen with the report the clinician saw. **About** (top right on Home) shows the licence notices and credits.

**AI report (optional, recommended).** The report is written on the phone by Gemma 4 E2B through LiteRT-LM, from the result only; it never changes the triage, and a report that doesn't state the exact triage level is replaced by the template. Download `gemma-4-E2B-it.litertlm` (2.6 GB, Apache-2.0) from Hugging Face [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) and push it once per phone:

```sh
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.deepsight/files/
```

- Without it, every case gets the deterministic template report, and Home says "Template reports".
- Gemma loads in the background when the app starts: about 12 s, or 27 s on the first launch after install (measured on the demo phone, [S3](docs/spikes/S3-litertlm-gemma.md)).
- Don't run `:app:connectedDebugAndroidTest` with the model on the phone: it uninstalls the app, which deletes the model. Use `adb shell am instrument` ([S3](docs/spikes/S3-litertlm-gemma.md), How to reproduce).

**Analyze one malaria field (debug builds).** Open the **DeepSight debug** icon and pick a thin-smear photo. It runs the real engine and shows a box per cell (green below 0.5, orange 0.5–0.8, red above 0.8), the counts, quality, provisional triage and timings.
- It needs the `malaria_thin` weights in `ml/packs/malaria_thin/`. They're in the private repo; see that pack's README for licence status.
- Without the photo picker:

  ```sh
  adb push field.jpg /sdcard/Android/data/com.deepsight/files/field.jpg
  adb shell am start -n com.deepsight/.DebugAnalyzeActivity --es path /sdcard/Android/data/com.deepsight/files/field.jpg
  adb logcat -s DeepSightDebug
  ```

## Tests

| What | Command |
|---|---|
| Engine JVM tests | `cd android && ./gradlew :engine:testDebugUnitTest` |
| App JVM tests | `cd android && ./gradlew :app:testDebugUnitTest` |
| On-device tests (phone connected) | `cd android && ./gradlew :engine:connectedDebugAndroidTest` |
| Python tests | `conda run -n deepsight python -m unittest discover -s ml/tests -v` |
| Contract files | `python contracts/validate.py <files>` |
| Pack model hashes | `python ml/tools/check_packs.py` |

Tests that need torch skip themselves outside the Conda environment; the `ml-torch` CI job installs the pinned packages and runs them. "It works" means it ran on a physical phone: a green build or CI run is not enough.

Pull requests and pushes to `test` and `main` run the Python tests, contract validation, setup-script checks and the Android engine JVM tests in GitHub Actions. Model downloads and phone-only tests are excluded.

## Train the router

The router classifier has one class per pack id plus `reject`. Training needs a split manifest that names a held-out source for every class (format at the top of `ml/train/router_split.py`).

```sh
python ml/train/train_router.py --split <split.json> --data-root <dir> --out <dir>
```

It writes `router.onnx`, `labels.json` and `eval.json` (accuracy per held-out source). Do not commit datasets or outputs.

## Branches

| Branch | Holds |
|---|---|
| `test` (default) | Where all work lands. CI-checked, but may not have run on a phone yet. |
| `main` | Only commits verified on a physical Android phone. Build demos from here. |

How to promote a commit from `test` to `main`: the Git section of [AGENTS.md](AGENTS.md).

## Docs

| Doc | For |
|---|---|
| [AGENTS.md](AGENTS.md) | Rules, layout and commands for teammates and their agents |
| [docs/STATUS.md](docs/STATUS.md) | What is built and verified, what is next |
| [docs/architecture.md](docs/architecture.md) | Pipeline, design rules, pack format, router |
| [docs/datasets.md](docs/datasets.md) | Datasets, licences and leak-free grouping keys |
| [docs/spikes/S1-malaria-screener.md](docs/spikes/S1-malaria-screener.md) | Malaria reuse licence and conversion findings |
| [contracts/README.md](contracts/README.md) | Manifest and result contracts, triage order |
| [LICENSING.md](LICENSING.md) | Licences of our code, models, data and dependencies |

## Licence

DeepSight is free software under the **GNU General Public License, version 3 only** (GPL-3.0-only). The full text is in [LICENSE](LICENSE), also kept as [LICENSES/GPL-3.0.txt](LICENSES/GPL-3.0.txt).

```text
DeepSight: offline microscopy screening and triage
Copyright (C) 2026 the DeepSight authors (see the git history)

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License version 3 as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License along with this program.
If not, see <https://www.gnu.org/licenses/>.
```

- **Why the GPL:** the malaria cell segmentation (`RbcDetector.kt`, `NlmHistogram.kt`, `ml/reference/nlm_segmentation.py`) is a port of GPLv3 code from NLM's Malaria Screener, and the app ships it, so the app as a whole is GPLv3.
- **In the app:** the About screen shows the copyright notice, the no-warranty statement, the permission to share under the GPL and the full licence text (GPLv3 §5d).
- **If you give anyone the APK,** also give them the source of the same commit (GPLv3 §6), for example a link to that commit or an archive of it, and keep these notices.
- **Not covered by the GPL:** model weights, datasets and the Gemma model keep their own terms, and some are still UNVERIFIED (see [LICENSING.md](LICENSING.md)). The NLM notice asks for this credit: courtesy of the U.S. National Library of Medicine.
