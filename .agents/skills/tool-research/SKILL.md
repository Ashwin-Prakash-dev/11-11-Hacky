---
name: tool-research
description: Research and recommend the single best library or tool for one specific, scoped engineering problem instead of hand-rolling it. Use when asked "what's the best tool for X" or "is there a library for this", or proactively before writing non-trivial custom code (validation, retries, image processing, parsing) that a mature open-source library likely already solves. Not for open-ended whole-stack decisions.
---

# Tool Research

Find the single best tool for one specific, scoped problem — not a tech-stack survey, not a listicle. The goal is a confident recommendation the user can adopt in five minutes, backed by evidence rather than training-data guesses (which go stale on which tool "won" a space).

Always research live. Even when you're confident (e.g. Pydantic for Python validation), verify it's still current, maintained, and the consensus.

## Step 0: Pin down the problem

Restate the problem in one sentence and identify hidden constraints:

- **Language/ecosystem** — almost always required.
- **The specific pain point**, not the category. "Type safety" could mean runtime validation, static checking, or schema-to-type generation; each has a different best answer.
- **Hard constraints.** Always apply DeepSight's (see AGENTS.md):
  - Runs **offline on the phone**: Android, arm64-v8a, minSdk 24 (check `android/app/build.gradle.kts`).
  - **APK size** matters: note the native library size of any Android dependency.
  - **Licence** must allow reuse; record it.
  - Python tooling goes in `ml/requirements.txt` and must not conflict with its pins (onnx needs protobuf>=6.31; onnxruntime is pinned to 1.30.0).
  - 30-hour hackathon: integration time beats theoretical best.

If the request already has enough context, state your interpretation in one line and proceed. Ask at most one question, only if guessing would waste the whole pass.

## Step 1: Search from multiple angles

1. Direct "best tool for X" query (framing only; listicles are SEO-gamed).
2. "X vs Y" comparisons for candidates that recur.
3. Official docs / GitHub repo for each serious candidate: last release, issue responsiveness, stability claims.
4. Community sentiment (Reddit, Hacker News, Stack Overflow): "why I switched from X to Y".
5. Adoption signal: is it a dependency of other major tools in the ecosystem?

Don't stop at the first conclusive-looking search; confirm with one comparison and one maintenance check.

## Step 2: Evaluate 2–4 candidates against fixed criteria

| Criterion | What to check |
|---|---|
| Solves the exact problem | Targets this pain point without heavy extra config/code |
| Industry standard | What production codebases in this ecosystem actually use |
| Maintenance health | Recent releases, responsive issues, no "looking for maintainer" |
| Licence & cost | State the licence (MIT/Apache-2.0/BSD…); flag paid gates or restrictive licences (AGPL, "small teams only") |
| Boilerplate reduction | Net less code including setup; verbose config is not a win |
| Integration fit | Fits the stack and constraints from Step 0 (offline, minSdk, APK size, pins) |
| Community sentiment | Recurring complaints that change the answer for this context |

## Step 3: Recommend one tool, not a menu

1. **Recommendation** — one tool, one-line reason it wins.
2. **Why it's the standard** (or why a challenger beats the incumbent).
3. **Licence and cost.**
4. **What it replaces** — the hand-rolled code it eliminates; a short before/after only if it clarifies.
5. **Quickstart** — install line (Gradle coordinate or pip) and the minimal snippet.
6. **Runner-up** — one alternative and the one-line reason it lost.

Don't end with "it depends". If it truly depends on an unknown, ask that in Step 0.

## Example

**User:** "Do we need OpenCV for the blur check?"

**Step 0:** Android/Kotlin. Pain point: variance of Laplacian and clipped-pixel fraction on one bitmap. Constraints: offline, APK size.

**Output:**
> **Recommendation: no library — plain Kotlin over `Bitmap.getPixels`.** A 3×3 Laplacian and a histogram are ~20 lines; OpenCV's Android SDK adds a large native library for two loops.
> **Runner-up:** OpenCV Android SDK — worth it only if we also need watershed cell segmentation (spike S1).

(Illustrative; sizes must be measured before they go in a doc.)

## Guardrails

- Never recommend a tool you haven't verified is maintained today.
- Never recommend a tool solely because it ranked first; cross-check one comparison and one maintenance signal.
- If no strong free/open-source candidate exists, say so; don't silently recommend a paid one.
- If two tools are genuinely best depending on X, recommend for the likely case and name the fork explicitly.
- Every version, size or licence claim needs a source, per AGENTS.md "Facts".
