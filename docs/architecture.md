# DeepSight architecture

This is the target design from the team's build plan (2026-10-02). For what is built and verified so far, see [STATUS.md](STATUS.md). Interfaces are in [contracts/README.md](../contracts/README.md).

## Pipeline (all on the phone)

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

Optional hub: a laptop on its own hotspot, running Ollama and PathOS for breast histopathology. The phone never uses the internet.

## Design rules

1. The user picks the test. The router only verifies; it never selects.
2. Triage is rules only. Gemma narrates and can't change the level.
3. Every result needs a clinician's sign-off. This is screening support, not diagnosis.
4. Every pack, including hub packs, returns the same result JSON.
5. Nothing outside `ml/packs/` and the manifests knows about a specific disease.

## Packs

```text
ml/packs/<id>/
  manifest.json   validated by contracts/manifest.schema.json
  model.onnx      or the file named in the manifest (hub packs: an endpoint)
  golden/         3-5 input images + expected output JSON from the Python reference
  README.md       dataset, licence, held-out metrics, known limits
```

- **Golden tests:** the Kotlin engine must reproduce the Python reference outputs on the demo phone, within a stated tolerance. The plan's example is boxes within 2 px and scores within 0.02. This catches preprocessing mismatches, which are the usual way on-device ports fail.
- **Done:** a pack that hasn't passed its golden tests on the device doesn't appear in the demo.

## Report

- **Input:** the case result JSON. **Output:** narrative text.
- **Triage check:** the report must contain the exact triage level string and no different level.
- **Fallback:** if the check fails or Gemma times out, the app fills a fixed template from the JSON.

## Planned packs and honest claims

| Module | Runs on | Basis |
|---|---|---|
| Malaria (thin smear) | Phone (target) | Prior art runs on Android (NLM Malaria Screener); confirm in spike S1 |
| Fungal (DeFungi) | Phone (probably) | Small patch classifier; confirm after S2, S5 |
| Leukaemia | Phone (probably) | Single-cell classifier; needs segmentation or tiling (S1) |
| Breast (PathOS) | Hub, unless S6 passes | Fine-tuned Gemma VLM via Ollama |
| Gemma reports | Phone | Measured in AI Edge Gallery, see STATUS.md facts |

Deck wording: "Phone-only screening for the lightweight modules, with an optional local hub for heavier ones." Don't claim everything runs on a budget phone unless every module has passed on it.
