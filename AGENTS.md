# DeepSight: instructions for agents and teammates

Before you change anything, read this file and then [docs/STATUS.md](docs/STATUS.md).
STATUS.md says where the project stands. This file holds the rules, which rarely change.

## Product
Offline Android app for microscopy screening and triage. A health worker:
1. Picks the test type.
2. Captures or imports a microscope field image.
3. Gets a quality check, a triage flag and a short report.

The phone needs no internet. A clinician always signs off. This is screening support, not diagnosis.
4 people work on this repo in parallel, each with AI agents. Hackathon: 30 hours.

## Hard rules
- **Triage:** deterministic, using the rules in each pack's manifest. The LLM only narrates. It never decides or changes triage.
- **Contracts:** contracts/ is frozen at v1.0.
  - Adding an optional field is a minor bump, and needs agreement from the whole team.
  - Never rename or remove a field.
  - After any contract change, run the validator and the engine tests (below).
- **Facts:** don't assume. Every claim about versions, devices, licences, latency or clinical thresholds needs a source or a measurement. Otherwise write UNVERIFIED and say how to check it.
- **Offline:** everything runs offline on the phone, except packs with `compute: hub`.
- **Demo phone:** "it works" means it ran on the demo phone through `installDebug` or `connectedDebugAndroidTest`. A successful build is not enough. There is no emulator.
- **Never commit:** datasets, `.litertlm` files, `local.properties`, `build/`, `.idea/`, `.venv/`.
- **Python:** use a venv built from `ml/requirements.txt`, never global Python. onnx needs protobuf>=6.31, which breaks other globally installed packages.

## Layout
| Path | What |
|---|---|
| `android/` | Gradle root. Modules `:app` (Compose UI), `:engine` (contracts, runtimes, gates, triage) and `:report`. Package `com.deepsight`. |
| `contracts/` | Manifest and result JSON Schemas, examples, `validate.py`, README (triage order) |
| `ml/packs/<id>/` | One pack per disease: `manifest.json` + weights + golden tests |
| `ml/train`, `ml/eval`, `ml/reference` | Training, evaluation and smoke models, reference implementations |
| `hub/` | Optional laptop hub (Ollama + PathOS) |
| `docs/STATUS.md` | Current state. Update it with every change that matters. |

## Commands
Run Gradle commands from `android/`. On Windows, use `gradlew.bat`.

| Task | Command |
|---|---|
| Install and run on the phone | `./gradlew installDebug`, then `adb shell am start -n com.deepsight/.MainActivity` |
| JVM tests | `./gradlew :engine:testDebugUnitTest` |
| On-device tests | `./gradlew :engine:connectedDebugAndroidTest` (phone connected) |
| Device logs | `adb logcat -s DeepSightS2` (or your tag) |
| Validate contract files | `python contracts/validate.py <files>` |
| Python setup | `python -m venv .venv`, then `.venv/Scripts/python -m pip install -r ml/requirements.txt` |

## Git (4 people and their agents)
- **Commits:** keep them small and run `git pull --rebase` before every push. Never force-push `main`.
- **Commit message prefix:** the track letter, e.g. `[C] ORT runner: add batch API`.
- **STATUS.md:** edit only your own track's section, plus any spike or gate row you own. Put that edit in the same commit as the work. This keeps merge conflicts rare.
- **Before editing a shared file** (contracts/, `libs.versions.toml`, `settings.gradle.kts`), pull first and keep the change minimal.
- **Branch `test`:** IDE files only. Don't merge it.

## Tracks
| Track | Scope |
|---|---|
| A | ML packs |
| B | Android shell |
| C | On-device engine (ONNX Runtime, CPU/XNNPACK first) |
| D | Gates and report |
| E | Data, evaluation, clinical sourcing of triage thresholds |
| F | Pitch and demo |
