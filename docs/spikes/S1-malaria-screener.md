# S1: NLM Malaria Screener feasibility

**Checked:** 2026-10-02  
**State:** partial - thin-model conversion proven and a licensed fallback identified; end-to-end golden parity remains open

## Question

Can DeepSight reuse the NLM Malaria Screener implementation or weights for the malaria pack, and can its model run through ONNX Runtime with matching output?

## Evidence inspected

- Official archived repository: <https://github.com/LHNCBC/MalariaScreener>
- Repository commit: `c485a211230c9acfe3f67280f7bb2831c4fd15e5`
- Paper: <https://bmcinfectdis.biomedcentral.com/articles/10.1186/s12879-020-05453-1>
- Inspection used a temporary checkout. No upstream source, model, or dataset was copied into this repository.

## Findings

### Repository and licence

- The repository is archived and explicitly warns that it is unmaintained and unsuitable for production use.
- The root `LICENSE` grants BSD-like redistribution rights and asks for NLM attribution.
- At least 85 Java/C++ source files instead carry GPLv3 headers. The model assets do not have their own provenance or licence file.
- Therefore the licence of the implementation and bundled weights is **UNVERIFIED** for DeepSight reuse. Do not copy either into this repository until the team resolves the conflicting notices and model provenance.

### Bundled pipeline

- Thin smear: Java/OpenCV marker-based watershed, connected-cell extraction, then a binary patch classifier.
- Thick smear: native C++/OpenCV candidate extraction and WBC counting, then a binary patch classifier.
- Both classifiers consume RGB float patches normalized to `[0, 1]`, shaped as dynamic batch x 44 x 44 x 3.
- Batch size is 8. The default classification thresholds are 0.5.
- Thin output order is `infected, normal`; thick output order is `normal, infected`.

### Bundled model files

| Model | Format | Size | SHA-256 | Result |
|---|---|---:|---|---|
| `malaria_thinsmear_44.tflite` | TFLite | 1.5 MB | `d4a75d749d67fed9d8146f8a408f9509f2675012bcd0b1801ff15819fc56c94d` | Converted to ONNX; parity passed |
| `malaria_thinsmear_44_retrainSudan_20P_4000C_separate.pb` | frozen TensorFlow GraphDef | 1.5 MB | `0e7d1369583d2584357e52c4b5652a0618b269e16b7025f111c82754e76c7912` | Active app model; conversion not yet proven |
| `ThickSmearModel.tflite` | TFLite | 8.1 MB | `c6710c1672fcffce543fbf3a28551eb9e3d9e3c07d51a0097b5c6b52c3081515` | Converter emitted an invalid ONNX graph |
| `ThickSmearModel.h5.pb` | frozen TensorFlow GraphDef | 8.1 MB | `620fecbd20aa269aab819593b19565c2342a15af856981e9245053b4b562addc` | Conversion not yet proven |

The app's active thin path loads the Sudan-retrained `.pb`, not the TFLite artifact that passed conversion. That distinction prevents treating the conversion result as full app parity.

## Reproducible conversion check

Tools were installed in the `deepsight` Conda environment: `ai-edge-litert==2.2.0`, `tflite2onnx==0.4.1`, `onnx==1.23.1`, and `onnxruntime==1.30.0`.

For the bundled thin TFLite model:

- TFLite input: `conv2d_20_input`, `[1, 44, 44, 3]`
- ONNX input: `conv2d_20_input`, `[1, 3, 44, 44]`; NHWC-to-NCHW transpose is required
- Output: `[1, 2]`
- Deterministic random input, seed `20261002`: maximum absolute output difference `0.0`
- Result: exact output parity on that input

For the thick TFLite model, `tflite2onnx` produced a graph that ONNX Runtime rejected at a `Gemm` node because its first input was not rank 2. Result: fail with this conversion path.

## Spike verdict

**S1 remains open.** The reusable algorithm structure and model I/O are known, and thin TFLite-to-ONNX conversion is technically viable. Reusing the upstream implementation or weights is blocked by conflicting licence notices, missing model provenance, lack of a representative upstream test image, and lack of end-to-end count parity with the active `.pb` model.

## Licensed fallback assets

- Dataset: [NIH-NLM Thin Blood Smears Pf](https://data.lhncbc.nlm.nih.gov/public/Malaria/NIH-NLM-ThinBloodSmearsPf/), 193 patients with five annotated fields per patient. Its supplied data licence permits commercial and non-commercial use and redistribution with the notice and attribution retained.
- Evaluation model: [LocalMedScan MobileNetV2](https://huggingface.co/Svetozar1993/LocalMedScan-malaria-mobilenetv2), pinned in `ml/models.json` to revision `2403c195c4901d179221ee9d5f17ec1128d3cbe1`. The model card and linked source repository declare MIT. Its published metrics are **UNVERIFIED** by DeepSight.
- The downloaded state dictionary loads strictly into torchvision MobileNetV2 and returns a finite `[1, 2]` output for a synthetic `224 x 224` RGB tensor. This proves loadability only, not clinical quality or Android parity.
- A second ONNX detector candidate is listed as `unverified` because its linked repository has no licence and its dataset provenance is inconsistent. The setup script excludes it unless a teammate explicitly passes `--include-unverified`.

`scripts/setup_dev.sh` creates the project Conda environment and downloads only checksum-pinned models approved for evaluation. Downloaded weights are ignored by Git.

## H0-H2 decision

1. Do not vendor Malaria Screener code or weights yet.
2. Treat the converted thin TFLite result as a feasibility proof only.
3. Ask Track E to resolve the upstream licence/model provenance and independently evaluate the fallback model.
4. Build the first malaria pack from the licensed NLM dataset and evaluation model, retaining the classical segmentation idea without copying GPL-marked source.
5. Close S1 only after either:
   - the upstream artefact licence is resolved and an end-to-end representative image matches, or
   - the fallback model produces its first golden case.
