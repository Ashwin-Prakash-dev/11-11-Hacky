# Licensing

Last checked: 2026-10-02. Every licence below links to where it was read. Anything not checked says UNVERIFIED and how to check it. This is a record for the team, not legal advice.

## Project licence

**DeepSight is GPL-3.0-only.** The licence text is in [`LICENSE`](LICENSE) (the same file as [`LICENSES/GPL-3.0.txt`](LICENSES/GPL-3.0.txt); both match <https://www.gnu.org/licenses/gpl-3.0.txt>, sha256 `3972dc97…6986` with LF line endings). The README's Licence section carries the standard notice.
- **Why:** the app ships the Kotlin port of NLM's GPLv3 segmentation ([`RbcDetector.kt`](android/engine/src/main/java/com/deepsight/engine/segmentation/RbcDetector.kt)), so the combined app must be GPLv3 whenever it is conveyed (§5c).
- **Why "only":** NLM's headers say "GNU General Public License v3.0" without "or any later version", and the ported files are already marked GPL-3.0-only.
- **Agreement:** proposed on the `eval` branch, 2026-10-02. Each teammate holds copyright in their own commits, so **every contributor must agree before this reaches `test`**. Record that agreement here (names and date).
- **Still open:** the NLM Malaria Screener weights. Their licence is unclear (S1, below), and they are in git only while the repo is private. The GPL covers our code; it does not settle the right to redistribute the weights.

## NLM Malaria Screener (model and segmentation)

Source: <https://github.com/nlm-malaria/MalariaScreener> (archived), commit `c485a211230c9acfe3f67280f7bb2831c4fd15e5`. The S1 spike checked the same commit.

| Part | What it says | Status |
|---|---|---|
| Root `LICENSE` | BSD-style "Informational Notice" from NLM. Redistribution is allowed if the notice and disclaimer are kept and the app credits "Courtesy of the U.S. National Library of Medicine". Copied in [`ml/packs/malaria_thin/NOTICE_NLM.txt`](ml/packs/malaria_thin/NOTICE_NLM.txt) | Read 2026-10-02 |
| Java source files | Headers say "Copyright 2020 The Malaria Screener Authors. All Rights Reserved. This software was developed under contract funded by the National Library of Medicine [...] Licensed under GNU General Public License v3.0". S1 counted 85+ such files. They include the 5 we ported from (`MarkerBasedWatershed`, `SegmentWatershed`, `OtsuThreshold`, `Histogram`, `Cells`) and `ThinSmearProcessor`, which we read for the call order | Read 2026-10-02. Headers name v3.0 without "or later" |
| Model weights (`malaria_thin_44.onnx`, `malaria_thin_44_sudan.onnx`) | No licence or provenance file of their own | **UNVERIFIED**. In git only because the repo is private (`ml/models/`, and the malaria pack's `model.onnx`). To resolve: ask NLM (LHNCBC) which licence covers the bundled models |

The headers say the code was written under contract. Works written by federal employees have no US copyright (17 U.S.C. §105), but contractors' work can, so treat the GPL headers as binding.

## DeepSight code that carries a third-party licence

| File | Licence | Why |
|---|---|---|
| [`ml/reference/nlm_segmentation.py`](ml/reference/nlm_segmentation.py) | GPL-3.0-only, text in [`LICENSES/GPL-3.0.txt`](LICENSES/GPL-3.0.txt) | Port of the GPLv3 files above. The file header records the source, the original notice and our changes (GPLv3 §5a) |
| [`ml/reference/malaria_pipeline.py`](ml/reference/malaria_pipeline.py) | GPL-3.0-only (the project licence) | It imports the GPL module for `--seg nlm`; the combined work is GPLv3 |
| [`RbcDetector.kt`](android/engine/src/main/java/com/deepsight/engine/segmentation/RbcDetector.kt), [`NlmHistogram.kt`](android/engine/src/main/java/com/deepsight/engine/segmentation/NlmHistogram.kt) | GPL-3.0-only | Kotlin port of the Python port; the headers record the source and our changes. They ship in the APK, so any APK given out must follow [GPL obligations](#gpl-obligations) |

The parity harness that ran NLM's original Java against the port lives in a scratch folder outside the repo. No NLM Java source is in the repo.

## Models

| Model | Licence | Source | In git |
|---|---|---|---|
| NLM thin-smear CNN (`ml/packs/malaria_thin/model.onnx`) | UNVERIFIED (above) | S1 | Yes, private repo only |
| NLM Sudan-retrained CNN (`ml/models/`, evaluation only) | UNVERIFIED (above) | S1 | Yes, private repo only |
| LocalMedScan MobileNetV2 | MIT | Model card and source repo, recorded in [`ml/models.json`](ml/models.json) | No (downloaded by `setup_dev.sh`) |
| BreakHis DenseNet-121 (`breast_breakhis`) | Upstream repository declares MIT; dataset-derived model redistribution remains **UNVERIFIED** | [Upstream licence](https://github.com/mrdvince/breast_cancer_detection/blob/master/License); [pack provenance and preparation](ml/packs/breast_breakhis/README.md). Keep `NOTICE_MIT_mrdvince.txt` with the model. Resolve dataset terms before distributing weights | Yes (`model_logits.onnx`), private repo only; the 28 MB upstream export stays out of git |
| Lara YOLOv8n | UNVERIFIED: the model card says MIT, but the linked repo has no licence | [`ml/models.json`](ml/models.json) | No (opt-in only) |
| B-ALL MobileNetV2 (`ml/packs/leukaemia_wbc/model.onnx`) | **UNVERIFIED** for the artifact/model code; upstream training data is C-NMC 2019, described as CC BY-NC 4.0 | Pinned source and revision in [`ml/models.json`](ml/models.json); pack details in [`ml/packs/leukaemia_wbc/README.md`](ml/packs/leukaemia_wbc/README.md) | Yes, private repo only |
| SatellaDet-Blood (`ml/packs/leukaemia_wbc/wbc_detector.onnx`) | **UNVERIFIED**: no explicit artifact licence was found | Pinned Hugging Face revision in [`ml/models.json`](ml/models.json) | Yes, private repo only |
| Gemma 4 E2B (report, via LiteRT-LM; `gemma-4-E2B-it.litertlm`) | Apache-2.0 | Hugging Face [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) model API (`license: apache-2.0`, ungated), 2026-10-02; sha256 in [S3](docs/spikes/S3-litertlm-gemma.md) | No (`*.litertlm` is never committed; pushed to the phone) |

## Datasets

| Data | Licence | Source | In git |
|---|---|---|---|
| NIH-NLM Thin Blood Smears Pf | NLM Informational Notice permits commercial/non-commercial use and redistribution with notice, conditions and disclaimer retained; no endorsement; requires NLM attribution and RBCNet citation. Independently read 2026-10-02 | [Original dataset licence](https://data.lhncbc.nlm.nih.gov/public/Malaria/NIH-NLM-ThinBloodSmearsPf/Data%20License%20Agreement.docx); [retained notice](ml/fixtures/NOTICE_NLM_THIN_FIELDS.txt); [selected fields and attribution](docs/datasets.md#reserved-malaria-fields-6) | Metadata and notice only; images and annotation files are ignored |
| RBCNet sample images (8 field photos; `ml/data/rbcnet/`, evaluation only) | RBCNet's `LICENSE` is the same NLM BSD-style notice (its attribution line says "MetaMap", a copy-paste slip). Its readme says the images come from NIH-NLM Thin Blood Smears Pf | <https://github.com/nlm-malaria/RBCNet>, commit `b98941d` | Yes (`ml/data/`), private repo only |
| NIH malaria `cell_images` (the 32 golden chips in `ml/packs/malaria_thin/golden/chips/`) | **UNVERIFIED**: the licence file on data.lhncbc.nlm.nih.gov returned 403 | — | Yes, private repo only |
| BreakHis (six breast-pack reference images and derived tensors) | **UNVERIFIED for redistribution:** the UFPR page contains non-commercial-research wording and a CC BY 4.0 footer. Confirm applicable terms with the dataset authors before redistribution; cite Spanhol et al., TBME 63(7):1455-1462, 2016 | [UFPR dataset terms](https://web.inf.ufpr.br/vri/databases/breast-cancer-histopathological-database-breakhis/) | Yes (the 6 golden PNGs), private repo only |

## Software dependencies

Read from PyPI and Maven metadata for the pinned versions, 2026-10-02.
- **What ships in the APK** (onnxruntime-android, OpenCV, kotlinx-serialization, CameraX, Room, LiteRT-LM) uses MIT, Apache-2.0 or BSD-3-Clause. The [FSF licence list](https://www.gnu.org/licenses/license-list.html) rates all of these GPLv3-compatible.
- **JUnit's EPL-1.0** is GPL-incompatible on that list. It's used in tests only and never shipped.
- **The Python packages** run on laptops only.

| Dependency | Version | Licence |
|---|---|---|
| onnxruntime / onnxruntime-android | 1.30.0 | MIT |
| OpenCV: opencv-python-headless / org.opencv:opencv | 4.14.0.94 / 4.14.0 | Apache-2.0 |
| onnx | 1.23.1 | Apache-2.0 |
| numpy | 2.5.3 | BSD-3-Clause AND 0BSD AND MIT AND Zlib AND CC0-1.0 |
| Pillow | 12.3.0 | MIT-CMU |
| jsonschema | 4.26.0 | MIT |
| torch / torchvision | 2.9.1 / 0.24.1 | BSD-3-Clause / BSD |
| kotlinx-serialization-json | 1.9.0 | Apache-2.0 |
| AndroidX CameraX (camera-core) | 1.6.2 | Apache-2.0 (the POM also lists BSD-3-Clause) |
| AndroidX Room | 2.8.5 | Apache-2.0 |
| LiteRT-LM (`litertlm-android`), with its gson 2.14.0, kotlin-reflect 2.4.0 and kotlinx-coroutines-android 1.11.0 | 0.17.1 | Apache-2.0 (all four POMs) |
| AndroidX Lifecycle (`lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`) | 2.6.1 | Apache-2.0 (POMs) |
| Compose Material icons (`material-icons-core`, version from the Compose BOM) | 1.7.8 | Apache-2.0 (POM) |
| Material icon paths copied into `android/app/src/main/res/drawable/ic_*.xml` (camera, image, history, description, science, auto_awesome) | — | Apache-2.0 (Google Material Icons); each file says so |
| JUnit (tests only) | 4.13.2 | EPL-1.0 |
| Android SDK tools | pinned in `setup_dev.sh` | [Android SDK terms](https://developer.android.com/studio/terms) |

## GPL obligations

They apply when we **convey** the work, which [GPLv3 §0](LICENSE) defines as propagation that lets other parties make or receive copies.
- **Not conveying:** a demo on our own phone, because nobody gets a copy.
- **Conveying:** giving someone the APK, publishing it, or making the repo public.

How DeepSight meets each obligation:

| Obligation | How | Checked by |
|---|---|---|
| Licence text with the work (§4, §5) | [`LICENSE`](LICENSE) in the repo; `GPL-3.0.txt` in the APK's assets (staged from `LICENSES/`) | `HomeAndAboutTest` opens it in the app |
| Appropriate Legal Notices in the interactive UI (§0, §5d): copyright, no warranty, the right to convey under the GPL, how to view the licence | The About screen (Home, top right) | `HomeAndAboutTest` |
| Modified files carry prominent notices with a date (§5a) | Headers of `nlm_segmentation.py`, `RbcDetector.kt`, `NlmHistogram.kt` record the source and our changes | Read the headers |
| Whole combined work under the GPL (§5c) | The project licence above | — |
| Corresponding Source for anyone given the APK (§6) | The About screen links the repository. **While the repo is private,** give recipients the source of the APK's exact commit some other way (an archive, or access), as §6(a) or a written offer under §6(b) | Do it when you hand out an APK |
| Keep third-party notices | NLM's notice ships with the pack (`packs/malaria_thin/NOTICE_NLM.txt` in the APK) and the About screen gives the credit it asks for. Apache-2.0 `NOTICE` files of dependencies: **UNVERIFIED** whether any must be reproduced; check each AAR before a public release | `HomeAndAboutTest` opens the NLM notice |

## Rules for adding anything
- **New dependency, model or dataset:** add a row here, with where you read the licence. Unknown means UNVERIFIED, and it stays out of git.
- **New code is GPL-3.0-only.** Don't add code under a GPL-incompatible licence (the FSF list flags, for example, EPL-1.0 and GPLv2-only); tests-only tools are the exception, as with JUnit.
- **Design references aren't code:** the UI follows the guidance in `claude-android-skill` (MIT, kept outside the repo); nothing from it was copied.
- **Weights and datasets:** never committed (AGENTS.md).
