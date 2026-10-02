---
name: concise-plan
description: Write implementation plans as short, concrete, file-by-file steps a teammate can scan in 10 seconds. Use whenever producing a plan before coding.
---

# Concise Planning

Write the plan like a senior engineer handing a task to another engineer. We are in a 30-hour hackathon: plan the smallest change that moves a gate (G1/G2 in `docs/STATUS.md`) forward.

## Rules

- Concise, concrete, implementation-focused. Short sentences and fragments.
- One numbered item = one concrete change.
- Name exact files, modules, classes, functions or symbols whenever known.
- Say **what changes and where**. Add **why** only when non-obvious.
- Bullets over paragraphs. Each step 1–3 sentences, ideally 1.
- Don't narrate reasoning, restate the request, or explain obvious code.
- No filler: "In order to...", "It is important to...", "We need to make sure...", "This will allow us to...", "The goal of this change is...", "As part of this...".
- Don't repeat the same rationale across steps.
- No speculative implementation details. If something is uncertain, say so in one line (or mark it UNVERIFIED, per AGENTS.md).

## Structure

1. **Short action title**
   - `path/to/File.kt` — concrete change.
   - `path/to/other.py` — concrete change.
2. **Short action title**
   - `path/to/File.kt` — concrete change.
3. **Validation**
   - Tests/checks to run (see the Commands table in AGENTS.md).

## Example

### Good

1. **Add triage evaluator**
   - `android/engine/.../triage/Triage.kt` — `fun triage(fields: List<FieldResult>, manifest: Manifest): Triage`, steps in `contracts/README.md` order.
2. **Test it**
   - `android/engine/src/test/.../TriageTest.kt` — one case per triage step, using `contracts/examples/manifest.malaria_thin.json`.
3. **Validate**
   - `./gradlew :engine:testDebugUnitTest`.

### Bad

1. **Introduce a triage abstraction layer**

   We need to establish a clear abstraction for triage. This is important because directly coupling the rules to the engine would make it harder to test independently. In order to address this, we should introduce a rule-strategy interface...

## Final constraint

The plan should be understandable in a 10-second scan.
