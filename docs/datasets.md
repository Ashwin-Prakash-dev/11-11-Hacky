# Datasets and Licences

Every licence is UNVERIFIED until someone has read the source terms and linked them here. A grouping key is what a held-out split must keep together (all of one patient or one source image) so train and test never share a subject.

The router needs at least two sources per test type plus a reject set, with one whole source per class held out. Its split manifest (file lists, no images) lives under `ml/eval/` once issue #8 lands.

| Dataset | Link | Licence | Attribution | What we use it for | Held-out grouping key |
|---|---|---|---|---|---|
| NIH-NLM Malaria Screener (Cells) | [link](https://lhncbc.nlm.nih.gov/LHC-publications/pubs/MalariaDatasets.html) | UNVERIFIED | NIH / NLM | Malaria pack (cell classifier) | patient |
| NIH-NLM ThinBloodSmearsPf (Fields) | [link](https://lhncbc.nlm.nih.gov/LHC-publications/pubs/MalariaDatasets.html) | UNVERIFIED | NIH / NLM | Malaria pack (WBC/RBC segmentation golden tests) | source image |
| DeFungi | [link](https://archive.ics.uci.edu/dataset/872/defungi) | UNVERIFIED | UCI ML Repository / DeFungi authors | Fungal pack | source image |
| Router sources | UNVERIFIED | UNVERIFIED | UNVERIFIED | Router guard | UNVERIFIED |
| C-NMC Leukaemia (ISBI 2019) | [link](https://www.kaggle.com/datasets/andrewmvd/leukemia-classification) | CC BY 4.0 (UNVERIFIED) | ISBI 2019 C-NMC Challenge | Leukaemia pack (cell classifier) | patient |
