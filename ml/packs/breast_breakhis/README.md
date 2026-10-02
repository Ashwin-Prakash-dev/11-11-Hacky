# breast_breakhis: Breast tumour (benign vs malignant) on H&E histology

**Demo pack, not a screening tool.** It works in the app like malaria (pick it, add fields, analyse, report, sign-off), but its triage is provisional and its accuracy is unverified (see Triage and Metrics). "Ready" on the home screen means the pipeline matches the reference on a phone, not that the model is clinically validated.

**Source.** DenseNet-121 checkpoint from https://github.com/mrdvince/breast_cancer_detection. The repo is MIT-licensed; the file is `saved/models/BCDensenet/0224_034642/model_best.pth`, at commit `8c5028c`. It was trained on BreakHis.
**Conversion.** The checkpoint was loaded into torchvision `densenet121` with a 2-class head and exported to ONNX (opset 17); the output matches PyTorch to within 3.7e-7. `prepare_pack.py` then strips the final Softmax (`ml/tools/onnx_logits.py`), because the engine applies softmax itself.

## Files

| Path | In git | What |
|---|---|---|
| `model_logits.onnx` | yes (private repo only) | The runtime model, 28 MB, sha256 in `manifest.json` |
| `manifest.json` | yes | Pack manifest; passes `contracts/validate.py` |
| `prepare_pack.py` | yes | Rebuilds `model_logits.onnx`, the manifest hash and the golden JSONs from the upstream export |
| `reference/pipeline.py` | yes | Desktop reference of the app's preprocessing, quality gate and `field_result` |
| `reference/eval_breakhis.py` | yes | Metrics on BreakHis 400x with the pack's model |
| `golden/<name>.png` + `<name>.json` | yes | 6 BreakHis fields and their expected `field_result` (what `PackGoldenTest` runs on the phone) |
| `NOTICE_MIT_mrdvince.txt` | yes | MIT notice; must ship with the app |
| The upstream export | **no** | 28 MB, sha256 `0a6274f45f33a644b5ba11fe852bcc30a8ec7beb018f02a262564d81fa6573c0`. Keep it at `ml/models/breakhis_densenet121_original.onnx` (git-ignored); only `prepare_pack.py` needs it |

## Model contract (verified)

| | |
|---|---|
| Input | `input` `[N,3,224,224]` float32, **NCHW**, **RGB**, `pixel/255` |
| Normalisation | **Built into the graph.** Feed plain /255 values; don't normalise again. (The repo uses `Normalize(0.1307, 0.3081)` on all channels.) |
| Output | `logits` `[N,2]`; the engine applies softmax once |
| Labels | **col 0 = benign, col 1 = malignant** (torchvision `ImageFolder`'s alphabetical order) |
| Preprocessing | Stretch the **whole field** to 224×224: bilinear on half-pixel centres, **no anti-aliasing**, as `Preprocessor` does (`reference/pipeline.py`) |
| Input type | Whole microscope field, not single cells (`source: field`) |

## Triage
One provisional rule: `malignant_seen`, `malignant >= 1` gives `ABNORMAL_FLAG` (a review flag on the model's prediction, not a finding). **There is no normal rule, on purpose:** a benign prediction on one field is not evidence of a normal screen, and the model calls two of three benign golden images malignant. Any case without a malignant field therefore gets `NEEDS_EXPERT` (`engine.no_rule_matched`); the pack never issues `NORMAL_SCREEN`. Counts add up across fields, so a case with several fields flags if any one is predicted malignant. No threshold is validated on held-out patients. Quality rejection is switched off (`min_blur` 0, `max_clipped_fraction` 1) until it is calibrated.

## Preprocessing changes the scores
The scores in earlier notes came from **Pillow's** resize, which anti-aliases when shrinking. The app's resize doesn't. On the 6 golden images the malignant score moves by up to 0.07 (0.0045 to 0.0723), so goldens made with Pillow fail the phone test's 0.02 tolerance. The goldens here use the app's resize (`prepare_pack.py`). Any accuracy figure measured with Pillow does not describe the app.

## Metrics (BreakHis 400x, 1,693 images, threshold 0.5)

Measured 2026-10-03 with `reference/eval_breakhis.py` on https://github.com/PerceptiLabs/breakhis-400x (`data/train` + `data/test`), with the pack's own model:

| Preprocessing | Accuracy | Sensitivity | Specificity | AUC |
|---|---:|---:|---:|---:|
| **The app's** (half-pixel bilinear, no anti-aliasing) | **89.3%** | 93.6% | **80.3%** | 0.958 |
| Pillow bilinear (how earlier notes measured) | 90.5% | 93.1% | 85.0% | 0.965 |

The Pillow row reproduces the earlier figures exactly, so the method is the same; the app's preprocessing costs about 5 points of specificity. **About one benign image in five is called malignant.**

**These numbers are not held-out.** The upstream repo trained on a random 90/10 image-level split of all BreakHis magnifications, and its split is not this dataset's `train`/`test` folders, so most of these images were in training. Real performance on new patients will be lower. The app was not measured on phone photos at all.

## Known limits
- **Specificity is weak (80%).** On the 6 golden images, two of the three benign fields are called malignant (p_malignant 0.94 and 0.88), so `malignant_seen` will flag many benign fields.
- **Mostly ImageNet features.** The repo froze the ImageNet DenseNet features and trained only the final layer.
- **Single source.** One lab's H&E images (BreakHis: 82 patients, 40x–400x). A phone photo through an eyepiece will look different.
- **Patch-level output.** It classifies one field at a time, not a slide or a patient.
- **No router, no uncertainty.** The router is the always-match stub and `uncertainty.method` is `none`.

## Licences
- **Model weights and code:** MIT (`NOTICE_MIT_mrdvince.txt`). The copyright notice must ship with the app.
- **Training data:** BreakHis is from UFPR/P&D Lab. Its terms for redistributing derived models and images are **UNVERIFIED**. The 6 golden images and the model are in git only because the repo is private ([LICENSING.md](../../../LICENSING.md)).

## Golden
- **Desktop:** `python ml/packs/breast_breakhis/golden/verify.py` checks that the PNGs, the reference preprocessing, the model and the committed JSONs agree (within 1e-4).
- **Phone:** `PackGoldenTest` runs each PNG through the real pipeline and compares with its JSON within 0.02 (`:engine:connectedDebugAndroidTest`). **All 6 cases passed on the edge 50 fusion on 2026-10-03.**
- **Regenerate:** `python ml/packs/breast_breakhis/prepare_pack.py` (needs the upstream export above).
