# DeepSight

An offline Android app for microscopy screening and triage. A health worker picks a test type, captures or imports microscope field images, and gets a quality check, a triage flag and a short report. No internet is needed, and a clinician always signs off. It supports screening; it doesn't diagnose.

Built in a 30-hour hackathon. Current state: [docs/STATUS.md](docs/STATUS.md).

## Development setup

Requires Conda. The setup script creates the `deepsight` environment, installs OpenJDK 25 and the pinned Python packages, and downloads checksum-verified models approved for evaluation. Model weights stay untracked.

```sh
scripts/setup_dev.sh
conda activate deepsight
```

Run `scripts/setup_dev.sh --help` for a different environment name or to skip dependencies/models. Candidates with unresolved licensing or provenance require the explicit `--include-unverified` option.

Pull requests and pushes to `main` run Python tests, contract validation, setup-script checks, and Android engine JVM tests in GitHub Actions. Model downloads and phone-only tests are intentionally excluded.

## Run it

Requires an arm64 Android phone (Android 7.0+, API 24) with USB debugging on.

```sh
cd android
./gradlew installDebug          # Windows: gradlew.bat installDebug
adb shell am start -n com.deepsight/.MainActivity
```

## Docs

| Doc | For |
|---|---|
| [AGENTS.md](AGENTS.md) | Rules, layout and commands for teammates and their agents |
| [docs/STATUS.md](docs/STATUS.md) | What is built and verified, what's next |
| [docs/architecture.md](docs/architecture.md) | Pipeline, design rules, pack format |
| [contracts/README.md](contracts/README.md) | Manifest and result contracts, triage order |
