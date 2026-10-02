# breast_breakhis: Breast tumour (benign vs malignant) on H&E histology

**Source.** DenseNet-121 checkpoint from https://github.com/mrdvince/breast_cancer_detection. The repo is MIT-licensed; the file is `saved/models/BCDensenet/0224_034642/model_best.pth`, at commit `8c5028c`. It was trained on BreakHis.
**Conversion.** I loaded the checkpoint into torchvision `densenet121` with a 2-class head, then exported it to ONNX (opset 17). The ONNX output matches PyTorch to within 3.7e-7.

## Model contract (verified)

| | |
|---|---|
| File | `model.onnx`, 28 MB, sha256 `0a6274f45f33a644b5ba11fe852bcc30a8ec7beb018f02a262564d81fa6573c0` |
| Input | `input` `[N,3,224,224]` float32, **NCHW**, **RGB**, `pixel/255` |
| Normalisation | **Built into the graph.** Feed plain /255 values; don't normalise again. (The repo uses `Normalize(0.1307, 0.3081)` on all channels.) |
| Output | `probs` `[N,2]`. Softmax is built in |
| Labels | **col 0 = benign, col 1 = malignant.** This is torchvision `ImageFolder`'s alphabetical order on the `benign/` and `malignant/` folders |
| Preprocessing | Resize the **whole field** to 224×224 (bilinear, aspect ratio not kept). This scored better than a centre crop: 90.5% vs 90.3% accuracy |
| Input type | Whole microscope field, not single cells (`source: field`) |

## Metrics (BreakHis 400x, 1,693 images)

At threshold 0.5: **accuracy 90.5%, sensitivity 93.1%, specificity 85.0%, AUC 0.965.** Reproduce with `python reference/eval_breakhis.py breakhis-400x/data`.

**These numbers are not held-out.** The repo trained on a random 90/10 image-level split of all BreakHis magnifications, so most of these images were in training. Real performance on new patients will be lower. The repo itself reports no metrics; its best checkpoint has a validation cross-entropy of 0.251.

## Known limits
- **Specificity is weak (85%).** Two of the three benign golden images are called malignant.
- **Mostly ImageNet features.** The repo froze the ImageNet DenseNet features and trained only the final layer.
- **Single source.** Trained on one lab's H&E images (BreakHis: 82 patients, 40x–400x). A phone photo through an eyepiece will look different from these.
- **Patch-level output.** It classifies one field at a time, not a slide or a patient.

## Licences
- **Model weights and code:** MIT (`NOTICE_MIT_mrdvince.txt`). The copyright notice must ship with the app.
- **Training data:** BreakHis is from UFPR/P&D Lab. Its terms for redistributing derived models are **UNVERIFIED**; resolve this under S1. The golden images are BreakHis samples.

## Golden
Run `golden/verify.py`.
- Test A feeds the raw tensor; outputs must match within 1e-4.
- Test B starts from the PNGs and runs the preprocessing; results must match within 0.02.
