# DeepSight

An offline Android app for microscopy screening and triage. A health worker picks a test type, captures or imports microscope field images, and gets a quality check, a triage flag and a short report. No internet is needed, and a clinician always signs off. It supports screening; it doesn't diagnose.

Built in a 30-hour hackathon. Current state: [docs/STATUS.md](docs/STATUS.md).

## Development setup

Requires Conda. The setup script creates the `deepsight` environment; installs OpenJDK 25, the pinned Python packages, and the Google Android SDK toolchain; and downloads checksum-verified models approved for evaluation. The SDK bootstrap supports Intel/Apple Silicon macOS and x86_64 Linux/Windows. Model weights stay untracked.

```sh
scripts/setup_dev.sh
conda activate deepsight
```

Run `scripts/setup_dev.sh --help` for a different environment name or to skip dependencies, the Android SDK or models. Android command-line tools, platform-tools 37.0.1, API 37.0 and Build Tools 36.0.0 are pinned to Google's versioned packages and checksums; their use is subject to the [Android SDK terms](https://developer.android.com/studio/terms). Candidates with unresolved licensing or provenance require the explicit `--include-unverified` option.

Pull requests and pushes to `test` and `main` run Python tests, contract validation, setup-script checks, and Android engine JVM tests in GitHub Actions. Model downloads and phone-only tests are intentionally excluded.

## Run it

Requires an arm64 Android phone (Android 7.0+, API 24) with USB debugging on.

```sh
cd android
./gradlew installDebug          # Windows: gradlew.bat installDebug
adb shell am start -n com.deepsight/.MainActivity
```

## Branches

| Branch | Holds |
|---|---|
| `test` (default) | Where all work lands. CI-checked, but may not have run on the phone yet. |
| `main` | Only commits verified on the demo phone. Build demos from here. |

How to promote a commit from `test` to `main`: the Git section of [AGENTS.md](AGENTS.md).

## Docs

| Doc | For |
|---|---|
| [AGENTS.md](AGENTS.md) | Rules, layout and commands for teammates and their agents |
| [docs/STATUS.md](docs/STATUS.md) | What is built and verified, what's next |
| [docs/architecture.md](docs/architecture.md) | Pipeline, design rules, pack format |
| [contracts/README.md](contracts/README.md) | Manifest and result contracts, triage order |
