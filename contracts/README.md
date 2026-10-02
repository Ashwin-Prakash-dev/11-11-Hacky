# DeepSight contracts v1.0 (frozen)

These are the interfaces between packs (`ml/`), the on-device engine (`android/engine`) and the report (`android/report`).

| File | What it is |
|---|---|
| `manifest.schema.json` | One manifest per pack, at `ml/packs/<id>/manifest.json` |
| `result.schema.json` | `field_result` (one per captured field) and `case_result` (one per case) |
| `examples/` | Valid examples. The engine unit tests parse them. The numbers in them are placeholders. |
| `validate.py` | Checks files against the schemas, plus the cross-field rules below |

- **Validate:** run `pip install jsonschema` once, then `python contracts/validate.py ml/packs/*/manifest.json`.
- **Kotlin types:** `android/engine/src/main/java/com/deepsight/engine/contract/`. Run `gradlew :engine:testDebugUnitTest` from `android/`.

## Versioning
- `contract_version` is `1.x`.
- Adding an optional field is a minor bump (`1.1`). Old app builds ignore fields they don't know.
- Renaming or removing a field, or changing what one means, is `2.0`. Agree it with the whole team first.

## Conventions
- **Names:** ids and labels are `lower_snake_case`.
- **Packs and test types:** a pack id is also the router class for its test type. One case runs one pack. The router adds one more class, `reject`, for images that are no known test type.
- **Values:** scores, probabilities and fractions are 0-1.
- **bbox:** `bbox` is `[x, y, w, h]`, scaled 0-1 relative to the field image, with the origin at top left.
- **Weights:** phone packs keep weights next to the manifest (`model.file`). Hub packs use `model.endpoint` and set both `runtime` and `compute` to `hub`.
- **Unknowns:** write `UNVERIFIED` for any unknown licence, source or threshold.

## Per field: `field_result`
1. **Quality.**
   - `blur_score` is the variance of the Laplacian; higher is sharper.
   - `exposure_score` is the clipped-pixel fraction; lower is better.
   - Thresholds come from the manifest's `quality` block. Calibrate them on that pack's images: sparse fields have fewer edges, so a global threshold rejects them as blurry.
2. **Rejection.** A field that fails quality is rejected. `router`, `image_score` and `uncertainty` are null, `objects` and `counts` are empty, and the user recaptures. A rejected field is never triaged.
3. **Router.** It returns one of three verdicts:
   - `match`: this pack's test type.
   - `mismatch`: looks like another known test type; `predicted` names it.
   - `reject`: not a known test type.
4. **Pack.** Produces `objects`, `counts` per label and `image_score`. `image_score` is the score of `output.image_score_label`:
   - For a whole-field classifier, the class probability.
   - Over cells or objects, the maximum score for that label.
   - If the pack has no image score, it's null.
5. **Uncertainty (`score_band`).** A field is uncertain if `image_score` falls inside `band`, or if more than `max_fraction` of `objects` have a score inside `band`.

`timing_ms` keys are `quality`, `router`, `preprocess`, `pack` and `total`. You can add others.

## Per case: `case_result` and triage
Triage is deterministic. The engine runs these steps in order and stops at the first one that decides:

1. `fields_passed` < `aggregation.min_fields`: **NEEDS_EXPERT**, `engine.insufficient_fields`.
2. Any passed field has router verdict `reject`: **NEEDS_EXPERT**, `engine.router_reject`. Otherwise, if any has `mismatch`: **NEEDS_EXPERT**, `engine.router_mismatch`.
3. Aggregate the passed fields:
   - `counts` are summed.
   - `image_score` is the max or mean, per `aggregation.image_score`.
   - The case is uncertain if any field is uncertain.
4. Try the manifest rules in order. The first rule whose conditions in `all` all hold sets `level` and `rule_id`.
   - A `count` condition compares the summed case counts of its `labels`; a missing label counts as 0.
   - An `image_score` condition is false when `image_score` is null.
5. No rule matched: **NEEDS_EXPERT**, `engine.no_rule_matched`.
6. Safety guard: a NORMAL_SCREEN on an uncertain case becomes **NEEDS_EXPERT**, `engine.uncertain`. An uncertain case is never screened normal.

`provisional` always equals the manifest's `triage.provisional`. Manifest rule ids can't contain `.`, so they never collide with `engine.*` ids.
