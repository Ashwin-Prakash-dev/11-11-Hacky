# Segmentation comparison on RBCNet field photos

Flagged = P(parasitized) above the threshold. Truth is the patient-level label.

## Model `model.onnx`

| Patient | Truth | Segmentation | Images | Cells/image | % flagged p>0.5 | % flagged p>0.8 |
|---|---|---|---|---|---|---|
| 239C12NThinF | negative | simple | 4 | 210 | 13.1 | 6.8 |
| 239C12NThinF | negative | nlm | 4 | 260 | 18.0 | 10.5 |
| 234C92P53ThinF | positive | simple | 4 | 138 | 10.4 | 6.5 |
| 234C92P53ThinF | positive | nlm | 4 | 222 | 15.8 | 9.9 |

## Model `malaria_thin_44_sudan.onnx`

| Patient | Truth | Segmentation | Images | Cells/image | % flagged p>0.5 | % flagged p>0.8 |
|---|---|---|---|---|---|---|
| 239C12NThinF | negative | simple | 4 | 210 | 1.4 | 1.0 |
| 239C12NThinF | negative | nlm | 4 | 260 | 5.6 | 3.0 |
| 234C92P53ThinF | positive | simple | 4 | 138 | 0.7 | 0.0 |
| 234C92P53ThinF | positive | nlm | 4 | 222 | 3.6 | 2.3 |

## Per image

| Model | Patient | Image | Segmentation | Cells | % >0.5 | % >0.8 | Seconds |
|---|---|---|---|---|---|---|---|
| model.onnx | 239C12NThinF | IMG_20150614_124212.jpg | simple | 205 | 15.6 | 9.8 | 0.99 |
| model.onnx | 239C12NThinF | IMG_20150614_124212.jpg | nlm | 225 | 22.2 | 12.4 | 1.04 |
| model.onnx | 239C12NThinF | IMG_20150614_124244.jpg | simple | 232 | 10.8 | 5.2 | 1.93 |
| model.onnx | 239C12NThinF | IMG_20150614_124244.jpg | nlm | 278 | 14.0 | 9.0 | 3.11 |
| model.onnx | 239C12NThinF | IMG_20150614_125703.jpg | simple | 226 | 11.9 | 7.1 | 2.15 |
| model.onnx | 239C12NThinF | IMG_20150614_125703.jpg | nlm | 269 | 14.5 | 8.2 | 2.5 |
| model.onnx | 239C12NThinF | IMG_20150614_125741.jpg | simple | 177 | 14.7 | 5.1 | 2.29 |
| model.onnx | 239C12NThinF | IMG_20150614_125741.jpg | nlm | 270 | 22.2 | 12.6 | 2.95 |
| model.onnx | 234C92P53ThinF | IMG_20150821_150718.jpg | simple | 166 | 10.2 | 6.6 | 1.94 |
| model.onnx | 234C92P53ThinF | IMG_20150821_150718.jpg | nlm | 217 | 10.1 | 6.5 | 2.28 |
| model.onnx | 234C92P53ThinF | IMG_20150821_151224.jpg | simple | 154 | 6.5 | 5.2 | 1.98 |
| model.onnx | 234C92P53ThinF | IMG_20150821_151224.jpg | nlm | 215 | 14.0 | 10.2 | 2.24 |
| model.onnx | 234C92P53ThinF | IMG_20150821_151646.jpg | simple | 113 | 12.4 | 8.8 | 1.86 |
| model.onnx | 234C92P53ThinF | IMG_20150821_151646.jpg | nlm | 225 | 17.8 | 9.8 | 2.38 |
| model.onnx | 234C92P53ThinF | IMG_20150821_151722.jpg | simple | 117 | 13.7 | 6.0 | 1.88 |
| model.onnx | 234C92P53ThinF | IMG_20150821_151722.jpg | nlm | 231 | 20.8 | 13.0 | 2.56 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_124212.jpg | simple | 205 | 2.0 | 2.0 | 2.45 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_124212.jpg | nlm | 225 | 6.2 | 3.6 | 2.31 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_124244.jpg | simple | 232 | 0.4 | 0.0 | 1.11 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_124244.jpg | nlm | 278 | 7.9 | 3.6 | 1.19 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_125703.jpg | simple | 226 | 0.9 | 0.4 | 1.04 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_125703.jpg | nlm | 269 | 3.0 | 1.9 | 1.06 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_125741.jpg | simple | 177 | 2.8 | 1.7 | 1.04 |
| malaria_thin_44_sudan.onnx | 239C12NThinF | IMG_20150614_125741.jpg | nlm | 270 | 5.2 | 3.0 | 1.23 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_150718.jpg | simple | 166 | 0.6 | 0.0 | 0.94 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_150718.jpg | nlm | 217 | 2.8 | 1.4 | 0.86 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_151224.jpg | simple | 154 | 0.0 | 0.0 | 0.94 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_151224.jpg | nlm | 215 | 3.3 | 2.3 | 0.78 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_151646.jpg | simple | 113 | 0.9 | 0.0 | 0.87 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_151646.jpg | nlm | 225 | 2.7 | 1.8 | 0.83 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_151722.jpg | simple | 117 | 1.7 | 0.0 | 0.87 |
| malaria_thin_44_sudan.onnx | 234C92P53ThinF | IMG_20150821_151722.jpg | nlm | 231 | 5.6 | 3.5 | 0.82 |
