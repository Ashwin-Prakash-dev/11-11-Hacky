---
name: merge-check
description: Final pre-merge gate for a DeepSight change, run after code review on the finished diff. Checks correctness, the AGENTS.md hard rules (deterministic triage, frozen contracts, offline, demo-phone proof, sourced facts), tests and commit hygiene, and reports a one-row-per-section table. Use before pushing to test or calling work done.
---

# Pre-Merge Checklist

The last gate before a change lands on `test`. Promotion to `main` is a separate step after a demo-phone run (AGENTS.md, Git section). We are in a 30-hour hackathon: the bar is **"works on the demo phone and breaks nothing"**, not production polish. MVP first; don't block a merge on elegance.

## Workflow position

1. Implementation is complete.
2. Code review has run and its findings are fixed: Claude `/code-review`, Codex `/review`. Antigravity has no verified built-in review command, so do one adversarial review pass yourself first.
3. This checklist runs on the final diff. Don't ask whether to run review; that step is upstream.

Priority: Correctness > Hard rules > Regression safety > Type safety > Maintainability > Performance > Elegance.

## 0. Scope calibration

State the class and the reason as the first line of output.

- **Trivial** — docs, copy, styling, comments; no logic or contract change. Run sections 1, 6 and 8 only, and say the rest were skipped.
- **Moderate** — logic in one module; no contract or manifest change. Run all sections; answer 4–5 briefly if nothing structural changed.
- **High-risk** — touches `contracts/`, triage/aggregation, a pack manifest or weights, shared build files (`libs.versions.toml`, `settings.gradle.kts`), or anything on the G1/G2 demo path. Run every section in full.

## 1. Correctness

Assume it's wrong until you fail to break it. Try to construct an input that breaks it; "I didn't think of a way" is not a pass.

- Does what the task/STATUS.md item asked; nothing more.
- Edge cases: empty/null fields, zero passed fields, missing labels (count as 0), `image_score` null, boundary values on every threshold (`>=` vs `>`).
- Image inputs: wrong size, rotated EXIF, grayscale, huge gallery image, blurry or blown-out frame.
- The 3 most likely demo failures have been tried (e.g. first-run model load, a rejected field, router mismatch).

## 2. Hard rules (AGENTS.md)

Check each rule in AGENTS.md "Hard rules" against the diff; don't restate them here. In particular:

- **Triage** is computed only by the rule engine, in `contracts/README.md` order. No LLM output feeds triage, and the report can't change the level.
- **Contracts**: no field renamed or removed. Any change is agreed by the team and versioned, and the validator and engine tests were run.
- **Facts**: every new number (threshold, latency, size, licence, version) has a source or measurement, or says UNVERIFIED with how to check.
- **Offline**: no network call on the phone path, except `compute: hub` packs.
- **"Works"** means run on the demo phone (`installDebug` or `connectedDebugAndroidTest`). A green build alone is ⚠️ at best.
- **Never committed**: datasets, `.litertlm`, `local.properties`, `build/`, `.idea/`, `.venv/`. Check `git status` and the diff's file list.

## 3. Type safety and contracts in code

- Contract types in `engine/.../contract/` match the JSON Schemas; new JSON written by the app passes `python contracts/validate.py`.
- No stringly-typed triage levels or verdicts where an enum exists.
- Python: typed, with dataclasses/Pydantic at file and JSON boundaries.

## 4. Placement

- Code is in the right module: UI in `:app`, inference/gates/triage in `:engine`, narrative in `:report`, training/reference in `ml/`.
- Nothing outside `ml/packs/` and the manifests knows about a specific disease.
- No new abstraction without a second real user (one interface + one implementation = delete the interface).

## 5. Failure handling

- How does it fail? Model file missing, ORT session error, out of memory, Gemma timeout, hub unreachable.
- Failures reach the user as a clear message or a NEEDS_EXPERT result; never a crash, never a silent NORMAL_SCREEN.
- Logs use the track's tag (`adb logcat -s <tag>`) and contain no patient data.

## 6. Tests and checks

Use the Commands table in AGENTS.md.

- Pure logic (triage, aggregation, gates) has JVM tests: `./gradlew :engine:testDebugUnitTest`.
- Anything touching inference has golden tests, run on the phone with `:engine:connectedDebugAndroidTest`. A pack isn't done until its golden tests pass on device.
- Contract or example changes: `python contracts/validate.py <files>` passes.
- Python changes ran in the project Conda environment created by `scripts/setup_dev.sh`.

## 7. Performance (only obvious regressions)

- No model loaded per field or per frame; sessions are reused.
- Nothing heavy on the main thread.
- APK size/ABI unchanged unless intended (arm64-v8a only).

## 8. Hygiene and handoff

- No dead code, debug logs, commented-out blocks, or unused imports added.
- `docs/STATUS.md`: your track's section updated in the same commit if the change matters.
- Commits small, prefixed with the track letter (`[C] ...`); `git pull --rebase` before push; push to `test`, never commit to `main` directly; never force-push `main` or `test`.

## 9. Final gate

Answer each in one sentence you could say out loud:

- Would I be fine demoing this live to judges right now?
- Is there a meaningfully simpler version? Why wasn't it used?
- Did I verify it works, or just not find a reason it doesn't?
- Could a teammate's agent modify this tomorrow without asking me?

Any "I don't know" counts as ❌.

## Output format

A markdown table with one row per section; skip rows excluded by scope calibration.

| Section | Status | Notes |
|---|---|---|
| 0. Scope | — | Moderate: triage logic in `:engine` only, no contract change. |
| 1. Correctness | ✅ | Tried 0 passed fields, null image_score, boundary 1000 cells. |
| 2. Hard rules | ⚠️ | Thresholds still `provisional: true` (Track E). |
| 6. Tests | ❌ | Not yet run on the demo phone. |
| 9. Final gate | ❌ | Not merge-ready: see 6. |

Legend: ✅ verified · ⚠️ acceptable with a caveat worth flagging · ❌ blocks merge · — skipped.

After the table, list each ❌ as one actionable bullet (what, where, fix). Declare merge-ready only if no applicable row is ❌ and nothing was left unknown.
