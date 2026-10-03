# DeepSight architecture

This is the target design from the team's build plan (2026-10-02). For what is built and verified so far, see [STATUS.md](STATUS.md). Interfaces are in [contracts/README.md](../contracts/README.md).

## Pipeline (phone-first, optional local hub)

```text
Pick test type  →  Capture (CameraX) or gallery import, one or more fields
  → Quality gate      blur (variance of Laplacian), exposure (clipped pixels)   :engine
  → Router guard      does the field match the chosen test? (small ONNX classifier)  :engine
  → Pack runner       reads ml/packs/<id>/manifest.json, runs ONNX Runtime (CPU/XNNPACK)  :engine
                      compute: hub packs call the laptop hub over HTTP instead
  → Aggregation       field results → one case                                   :engine
  → Triage            deterministic rules from the manifest, no LLM              :engine
  → Report            Gemma 4 E2B via LiteRT-LM narrates; template fallback      :report
  → Human review and sign-off, stored locally (Room)                             :app
```

Optional hub: a laptop on its own hotspot, running Ollama and PathOS for breast histopathology. The app needs no internet; hub traffic stays on the local hotspot.

## Design rules

1. The user picks the test. The router only verifies; it never selects.
2. Triage is rules only. Gemma narrates and can't change the level.
3. Every result needs a clinician's sign-off. This is screening support, not diagnosis.
4. Every pack, including hub packs, returns the same result JSON.
5. Nothing outside `ml/packs/` and the manifests knows about a specific disease.

## Router guard

- **Model:** a small pretrained classifier (MobileNetV2 backbone, ONNX) with one class per pack id plus `reject`. Training and export: `ml/train/train_router.py`.
- **Verdict:** the top class equal to the chosen pack is `match`; another pack id is `mismatch` and names it in `predicted`; `reject` means the image is no known test type. Verdicts and the effect on triage are in [contracts/README.md](../contracts/README.md).
- **Labels:** derived from the split manifest, never hardcoded. Adding a pack means retraining the router.
- **Evaluation:** accuracy is reported on a held-out source per class, not a random split, because a router trained on mixed sources learns dataset fingerprints. Numbers go in `docs/claims.md` only once measured.
- **Today:** the engine still uses a stub that always returns `match`; the trained model and its golden cases are pending (see [STATUS.md](STATUS.md)).

## Packs

```text
ml/packs/<id>/
  manifest.json   validated by contracts/manifest.schema.json
  model.onnx      or the file named in the manifest (hub packs: an endpoint)
  golden/         3-5 input images + expected output JSON from the Python reference
  README.md       dataset, licence, held-out metrics, known limits
```

- **Golden tests:** the Kotlin engine must reproduce the Python reference outputs on a physical Android phone, within a stated tolerance. The plan's example is boxes within 2 px and scores within 0.02. This catches preprocessing mismatches, which are the usual way on-device ports fail.
- **Done:** a pack that hasn't passed its golden tests on a physical phone doesn't appear in the demo.
- **Test-first:** every behavior change adds or updates the test that proves it (AGENTS.md, Test-first workflow).

## Report

- **Input:** the case result JSON. **Output:** narrative text.
- **Triage check:** the report must contain the exact triage level string and no different level.
- **Fallback:** if the check fails or Gemma times out, the app fills a fixed template from the JSON.
- **Implemented (branch `eval`):** `:report` `CaseReport.kt` (template, prompt, check) and the app's `ReportWriter`, with Gemma loaded at app start and a 30 s generation limit. The report the clinician saw is saved with the sign-off (Room v2).

## Planned packs and honest claims

| Module | Runs on | Basis |
|---|---|---|
| Malaria (thin smear) | Phone (target) | Android prior art and an evaluation-model fallback exist; S1 remains partial ([status](STATUS.md)) |
| Leukaemia | Phone (probably) | Single-cell classifier on WBC crops; needs segmentation or tiling. Placeholder model today |
| Breast (PathOS) | Hub, unless S6 passes | Fine-tuned Gemma VLM via Ollama |
| Gemma reports | Phone | S3 passed: Gemma 4 E2B streams in our app next to an ONNX pack ([evidence](spikes/S3-litertlm-gemma.md)); the template stays the fallback |

Deck wording: "Phone-only screening for the lightweight modules, with an optional local hub for heavier ones." Don't claim everything runs on a budget phone unless every module has passed on it.
