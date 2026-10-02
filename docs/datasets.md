# Datasets and Licences

Every licence is UNVERIFIED until someone has read the source terms and linked them here. A grouping key is what a held-out split must keep together (all of one patient or one source image) so train and test never share a subject.

The router needs at least two sources per test type plus a reject set, with one whole source per class held out. Its split manifest (file lists, no images) lives under `ml/eval/` once issue #8 lands.

| Dataset | Link | Licence | Attribution | What we use it for | Held-out grouping key |
|---|---|---|---|---|---|
| NIH-NLM Malaria Screener (Cells) | [link](https://lhncbc.nlm.nih.gov/LHC-publications/pubs/MalariaDatasets.html) | UNVERIFIED | NIH / NLM | Malaria pack (cell classifier) | patient |
| NIH-NLM ThinBloodSmearsPf (Fields) | [dataset](https://data.lhncbc.nlm.nih.gov/public/Malaria/NIH-NLM-ThinBloodSmearsPf/index.html) | NLM Informational Notice: commercial/non-commercial use and redistribution permitted; retain notice, conditions and disclaimer; no endorsement. [Original terms](https://data.lhncbc.nlm.nih.gov/public/Malaria/NIH-NLM-ThinBloodSmearsPf/Data%20License%20Agreement.docx), read 2026-10-02 | “Courtesy of the U.S. National Library of Medicine” or “Source: U.S. National Library of Medicine”; cite Kassim et al., RBCNet ([DOI](https://doi.org/10.1109/JBHI.2020.3034863)). [Retained notice](../ml/fixtures/NOTICE_NLM_THIN_FIELDS.txt) | Reserved full-field goldens and demo (#6) | patient (all fields and derived cell crops) |
| DeFungi | [link](https://archive.ics.uci.edu/dataset/872/defungi) | UNVERIFIED | UCI ML Repository / DeFungi authors | Fungal pack | source image |
| Router sources | UNVERIFIED | UNVERIFIED | UNVERIFIED | Router guard | UNVERIFIED |
| C-NMC Leukaemia (ISBI 2019) | [link](https://www.kaggle.com/datasets/andrewmvd/leukemia-classification) | CC BY 4.0 (UNVERIFIED) | ISBI 2019 C-NMC Challenge | Leukaemia pack (cell classifier) | patient |

## Reserved malaria fields (#6)

Source of IDs, URLs, SHA-256 digests and annotation counts: [`ml/fixtures/malaria_fields.json`](../ml/fixtures/malaria_fields.json). All six originals were visually inspected and their complete annotation files counted on 2026-10-02. Selection used annotations, not classifier predictions.

| Role | Category | Patient directory | Field ID | Parasitized RBCs | Uninfected RBCs | WBCs |
|---|---|---|---|---:|---:|---:|
| Golden | Positive | `211C70P31_ThinF` | `IMG_20150813_130332` | 15 | 99 | 2 |
| Golden | Negative | `244C7NthinF` | `IMG_20150611_104404` | 0 | 204 | 0 |
| Golden | Sparse | `211C70P31_ThinF` | `IMG_20150813_130849` | 9 | 70 | 0 |
| Demo | Positive | `142C38P3thinF_original` | `IMG_20150621_112043` | 2 | 207 | 0 |
| Demo | Negative | `244C7NthinF` | `IMG_20150611_104510` | 0 | 201 | 0 |
| Demo | Sparse | `211C70P31_ThinF` | `IMG_20150813_130510` | 14 | 80 | 0 |

**Counting rule:** the [dataset README](https://data.lhncbc.nlm.nih.gov/public/Malaria/NIH-NLM-ThinBloodSmearsPf/ReadMe.pdf) defines each `Parasitized` record as one infected RBC. These are annotation-derived infected-cell counts, **not individual parasite counts**; the annotations do not enumerate parasites inside each cell. The preparer validates the header's record count and geometry and preserves counts for every supplied label. Sparse means relatively few RBCs (79/94 here), not a clinical threshold. Zero infected RBCs is a field annotation fact, not a patient diagnosis.

Prepare from the repository root in the project Conda environment:

```sh
conda run -n deepsight python ml/tools/prepare_malaria_fields.py
```

Outputs go to ignored `ml/data/malaria_fields/`: unchanged source JPEGs, annotations, original licence DOCX and README PDF, retained text notice, and two synthetic PNG failures. `demo_blurred` uses an 81×81 Gaussian kernel with sigma 20; `demo_overexposed` clips `4 * pixel + 252` to 255 (a severe, explicitly synthetic saturation failure). Both derive from `demo_positive`. EXIF is applied once when generating variants; original annotation coordinates remain in the original JPEG raster. `prepared.json` records the selection hash and generated image hashes. Do not copy these images into tracked pack directories.

**Reservations:** exclude `C70P31`, `C7N`, and `C38P3` from every training/validation/evaluation split, including all other fields and derived cell crops. Golden positive/sparse fixtures share the first patient; negative fixtures use the second; demo positive uses the third. These fixtures do not establish independent accuracy. The router split loader enforces reservations before splitting; renamed NLM files require `patient_id`. Other split builders must call `assert_no_reserved_patients` or validate their `{ "files": [...] }` manifest with:

```sh
conda run -n deepsight python ml/tools/malaria_reservations.py <split.json>
```

The previously evaluated patients `C92P53` and `C12N` were not selected. No project split manifest exists yet; future split owners must retain this guard. Upstream pretrained-model patient overlap is **UNVERIFIED**; check the model's original training manifest before claiming an independent accuracy result.

Tests: `conda run -n deepsight python -m unittest discover -s ml/tests -v` includes preparation, annotation and patient-exclusion regressions without dataset downloads. Actual quality-gate evidence is collected with `ReservedMalariaFieldsTest` after preparation (from `android/`, set `DEEPSIGHT_FIELDS` to the absolute output directory and run `./gradlew :engine:testDebugUnitTest --tests '*ReservedMalariaFieldsTest' --rerun-tasks`). On Windows use `gradlew.bat`. Without local fields this evidence test skips. Model-output goldens and physical-phone G1 validation remain separate integration work.

**Recorded evidence, 2026-10-02:** a fresh preparation fetched and hash-verified all six originals, annotations and notices; an offline repeat (network calls disabled) reproduced both generated PNG hashes. `:engine:testDebugUnitTest` passed 74/74 with local fields, including `ReservedMalariaFieldsTest`: all six originals passed the current pack quality thresholds. The blurred variant returned `[BLUR, UNDEREXPOSED]` (blur 0.65035; clipped fraction 0.49282); the overexposed variant returned `[BLUR, OVEREXPOSED]` (blur 7.74794; clipped fraction 0.62806). These are JVM measurements, not phone verification. The additional rejection reasons reflect the existing gate's treatment of vignette/clipping and severe saturation; the failure demos do not isolate a single reason.
