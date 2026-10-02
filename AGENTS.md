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
- **Never commit:** datasets, downloaded evaluation weights under `ml/models/`, `.litertlm` files, `local.properties`, `build/`, `.idea/`, `.venv/`, `ml/data/`.
- **Python:** use the Conda environment created by `scripts/setup_dev.sh`, never global Python. ONNX needs protobuf>=6.31, which breaks other globally installed packages.

## Test-first workflow
- Every new feature, behavior change, and bug fix must add or update the test that proves it.
- Write or identify the test before production implementation.
- Run it first and confirm it fails for the expected reason.
- Implement the smallest change that makes it pass.
- Never weaken, delete, or loosen an existing test merely to make a change pass.
- Pure Kotlin logic requires JVM unit tests.
- Inference or preprocessing changes require golden tests and `connectedDebugAndroidTest` on the demo phone.
- Python and setup tooling requires automated tests included in CI.
- UI work requires automated tests for testable logic plus an `installDebug` walkthrough on the demo phone.
- Documentation, research, and measurement-only changes may use validation or recorded evidence instead of a code test.
- Every issue and PR must name the test or evidence that proves completion.

## Layout
| Path | What |
|---|---|
| `android/` | Gradle root. Modules `:app` (Compose UI), `:engine` (contracts, runtimes, gates, triage) and `:report`. Package `com.deepsight`. |
| `contracts/` | Manifest and result JSON Schemas, examples, `validate.py`, README (triage order) |
| `ml/packs/<id>/` | One pack per disease: `manifest.json` + weights + golden tests |
| `ml/train`, `ml/eval`, `ml/reference` | Training, evaluation and smoke models, reference implementations |
| `ml/models.json` | Pinned external evaluation models, checksums, licence/provenance status |
| `scripts/setup_dev.sh` | Creates the Conda environment with JDK/Python tooling and downloads approved evaluation models |
| `hub/` | Optional laptop hub (Ollama + PathOS) |
| `docs/STATUS.md` | Current state. Update it with every change that matters. |
| `docs/architecture.md` | Target pipeline, design rules, pack format (from the build plan) |

## Commands
Run Gradle commands from `android/`. On Windows, use `gradlew.bat`.

| Task | Command |
|---|---|
| Install and run on the phone | `./gradlew installDebug`, then `adb shell am start -n com.deepsight/.MainActivity` |
| JVM tests | `./gradlew :engine:testDebugUnitTest` |
| On-device tests | `./gradlew :engine:connectedDebugAndroidTest` (phone connected) |
| Device logs | `adb logcat -s DeepSightS2` (or your tag) |
| Validate contract files | `python contracts/validate.py <files>` |
| Python setup | `scripts/setup_dev.sh`, then `conda activate deepsight` |
| Python downloader tests | `conda run -n deepsight python -m unittest discover -s ml/tests -v` |

## Skills (shared by all agents)
Workflows live in `.agents/skills/<name>/SKILL.md`: `concise-plan`, `tool-research`, `merge-check` and `project-docs`.
- **Invoke:** Claude `/name`, Codex `$name` (or `/skills`), Antigravity 2.0 and CLI `/name`; in the Antigravity IDE, mention the skill by name. Agents also pick them up automatically from their descriptions.
- **Edit only the SKILL.md.** Claude doesn't read `.agents/skills/`, so `.claude/commands/<name>.md` is a one-line pointer to it. If you change a skill's `description`, copy it into the pointer too, because Claude matches on the pointer's copy. A new skill needs both files.

## Git (4 people and their agents)
- **Branches:** `test` is the default branch, and all work lands there. `main` only gets commits that ran on the demo phone. Never commit to `main` directly.
- **Commits:** keep them small and run `git pull --rebase` before every push. Push to `test`, or open a PR into `test`. Never force-push `main` or `test`.
- **Commit message prefix:** the track letter, e.g. `[C] ORT runner: add batch API`.
- **STATUS.md:** edit only your own track's section, plus any spike or gate row you own. Put that edit in the same commit as the work. This keeps merge conflicts rare.
- **Before editing a shared file** (contracts/, `libs.versions.toml`, `settings.gradle.kts`), pull first and keep the change minimal.
- **Promote `test` to `main`** (whoever has the demo phone):
  1. `git switch test`, `git pull --rebase`, then note the commit with `git rev-parse HEAD`. CI must be green for it.
  2. Run that commit on the demo phone: `installDebug` and use the app. If inference changed, also run `:engine:connectedDebugAndroidTest`.
  3. `git push origin <sha>:main`. This promotes exactly the commit you tested, even if `test` has moved since.
  - Don't promote through a GitHub PR. Its merge or squash commit lands on `main` but not on `test`, and later promotions stop being fast-forwards.
  - If the push is refused as not a fast-forward, someone committed to `main`. Merge `main` into `test`, verify again and push again. Never force.

## Tracks
| Track | Scope |
|---|---|
| A | ML packs |
| B | Android shell |
| C | On-device engine (ONNX Runtime, CPU/XNNPACK first) |
| D | Gates and report |
| E | Data, evaluation, clinical sourcing of triage thresholds |
| F | Pitch and demo |
