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
- **Everything runs on the phone**, except packs marked `compute: hub`, which call an optional laptop on a local hotspot.

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

## Tests

| What | Command |
|---|---|
| Engine JVM tests | `cd android && ./gradlew :engine:testDebugUnitTest` |
| App JVM tests | `cd android && ./gradlew :app:testDebugUnitTest` |
| On-device tests (phone connected) | `cd android && ./gradlew :engine:connectedDebugAndroidTest` |
| Python tests | `conda run -n deepsight python -m unittest discover -s ml/tests -v` |
| Contract files | `python contracts/validate.py <files>` |

Tests that need torch skip themselves outside the Conda environment. "It works" means it ran on a physical phone: a green build or CI run is not enough.

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
