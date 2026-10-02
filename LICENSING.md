# Licensing

Last checked: 2026-10-02. Every licence below links to where it was read. Anything not checked says UNVERIFIED and how to check it. This is a record for the team, not legal advice.

## Decisions the team needs to make

1. **DeepSight has no licence.** There is no LICENSE file, so by default all rights are reserved. Pick one before the repo goes public or anyone outside the team gets the APK.
2. **The NLM segmentation port is GPLv3.** [`ml/reference/nlm_segmentation.py`](ml/reference/nlm_segmentation.py) is a line-by-line port of GPLv3 Java, so it is GPLv3 too.
   - If it, or a Kotlin port of it, ships in the APK, the whole app must be distributed under GPLv3, with source (see [GPL obligations](#gpl-obligations)).
   - The alternative is to keep it as a desktop reference only.
3. **NLM Malaria Screener weights:** the licence is unclear (S1). They stay out of git until Track E resolves it.

## NLM Malaria Screener (model and segmentation)

Source: <https://github.com/nlm-malaria/MalariaScreener> (archived), commit `c485a211230c9acfe3f67280f7bb2831c4fd15e5`. The S1 spike checked the same commit.

| Part | What it says | Status |
|---|---|---|
| Root `LICENSE` | BSD-style "Informational Notice" from NLM. Redistribution is allowed if the notice and disclaimer are kept and the app credits "Courtesy of the U.S. National Library of Medicine". Copied in [`ml/packs/malaria_thin/NOTICE_NLM.txt`](ml/packs/malaria_thin/NOTICE_NLM.txt) | Read 2026-10-02 |
| Java source files | Headers say "Copyright 2020 The Malaria Screener Authors. All Rights Reserved. This software was developed under contract funded by the National Library of Medicine [...] Licensed under GNU General Public License v3.0". S1 counted 85+ such files. They include the 5 we ported from (`MarkerBasedWatershed`, `SegmentWatershed`, `OtsuThreshold`, `Histogram`, `Cells`) and `ThinSmearProcessor`, which we read for the call order | Read 2026-10-02. Headers name v3.0 without "or later" |
| Model weights (`malaria_thin_44.onnx`, `malaria_thin_44_sudan.onnx`) | No licence or provenance file of their own | **UNVERIFIED**. In git only because the repo is private (`model.onnx` of the malaria pack; the raw `ml/models/` conversions stay out). To resolve: ask NLM (LHNCBC) which licence covers the bundled models |

The headers say the code was written under contract. Works written by federal employees have no US copyright (17 U.S.C. §105), but contractors' work can, so treat the GPL headers as binding.

## DeepSight code that carries a third-party licence

| File | Licence | Why |
|---|---|---|
| [`ml/reference/nlm_segmentation.py`](ml/reference/nlm_segmentation.py) | GPL-3.0-only, text in [`LICENSES/GPL-3.0.txt`](LICENSES/GPL-3.0.txt) | Port of the GPLv3 files above. The file header records the source, the original notice and our changes (GPLv3 §5a) |
| [`ml/reference/malaria_pipeline.py`](ml/reference/malaria_pipeline.py) | Ours; no licence chosen yet | It imports the GPL module only for `--seg nlm`. Distributing the two together makes a combined work, which has to be GPLv3 |
| [`RbcDetector.kt`](android/engine/src/main/java/com/deepsight/engine/segmentation/RbcDetector.kt), [`NlmHistogram.kt`](android/engine/src/main/java/com/deepsight/engine/segmentation/NlmHistogram.kt) | GPL-3.0-only | Kotlin port of the Python port; the headers record the source and our changes. They ship in the APK, so any APK given out must follow [GPL obligations](#gpl-obligations) |

The parity harness that ran NLM's original Java against the port lives in a scratch folder outside the repo. No NLM Java source is in the repo.

## Models

| Model | Licence | Source | In git |
|---|---|---|---|
| NLM thin-smear CNN (`ml/packs/malaria_thin/model.onnx`) | UNVERIFIED (above) | S1 | Yes, private repo only |
| NLM Sudan-retrained CNN (`ml/models/`, evaluation only) | UNVERIFIED (above) | S1 | No |
| LocalMedScan MobileNetV2 | MIT | Model card and source repo, recorded in [`ml/models.json`](ml/models.json) | No (downloaded by `setup_dev.sh`) |
| Lara YOLOv8n | UNVERIFIED: the model card says MIT, but the linked repo has no licence | [`ml/models.json`](ml/models.json) | No (opt-in only) |
| Gemma 4 E2B (report, via LiteRT-LM; `gemma-4-E2B-it.litertlm`) | Apache-2.0 | Hugging Face [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) model API (`license: apache-2.0`, ungated), 2026-10-02; sha256 in [S3](docs/spikes/S3-litertlm-gemma.md) | No (`*.litertlm` is never committed; pushed to the phone) |

## Datasets

| Data | Licence | Source | In git |
|---|---|---|---|
| NIH-NLM Thin Blood Smears Pf | Use and redistribution allowed with the notice and attribution kept | [S1 spike](docs/spikes/S1-malaria-screener.md) | No |
| RBCNet sample images (8 field photos; `ml/data/rbcnet/`, evaluation only) | RBCNet's `LICENSE` is the same NLM BSD-style notice (its attribution line says "MetaMap", a copy-paste slip). Its readme says the images come from NIH-NLM Thin Blood Smears Pf | <https://github.com/nlm-malaria/RBCNet>, commit `b98941d` | No |
| NIH malaria `cell_images` (the 32 golden chips in `ml/packs/malaria_thin/golden/chips/`) | **UNVERIFIED**: the licence file on data.lhncbc.nlm.nih.gov returned 403 | — | No (`.git/info/exclude`) |
| DeFungi | CC BY 4.0 | [UCI dataset page](https://archive.ics.uci.edu/dataset/773/defungi) | No |

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
| JUnit (tests only) | 4.13.2 | EPL-1.0 |
| Android SDK tools | pinned in `setup_dev.sh` | [Android SDK terms](https://developer.android.com/studio/terms) |

## GPL obligations

They apply only when we **convey** the work, which [GPLv3 §0](LICENSES/GPL-3.0.txt) defines as propagation that lets other parties make or receive copies.
- **Not conveying:** a demo on our own phone, because nobody gets a copy.
- **Conveying:** giving someone the APK, publishing it, or making the repo public with the port in it.

When we convey the GPL code or anything built from it:
1. License the whole combined work under GPLv3 (§5c).
2. Give recipients the licence text (§4, §5) and keep all notices.
3. Mark our changes with a date (§5a). `nlm_segmentation.py` already does.
4. For an APK, provide the Corresponding Source, meaning the full source of the app (§6).

## Rules for adding anything
- **New dependency, model or dataset:** add a row here, with where you read the licence. Unknown means UNVERIFIED, and it stays out of git.
- **Never copy GPL-marked code** into a file that isn't marked GPL-3.0 and listed above.
- **Weights and datasets:** never committed (AGENTS.md).
