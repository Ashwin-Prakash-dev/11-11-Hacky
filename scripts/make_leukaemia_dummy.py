import json
import hashlib
from pathlib import Path
import os

ROOT = Path(__file__).resolve().parents[1]
PACK_DIR = ROOT / "ml/packs/leukaemia_wbc"
GOLDEN_DIR = PACK_DIR / "golden"

def main():
    GOLDEN_DIR.mkdir(parents=True, exist_ok=True)
    onnx_path = PACK_DIR / "model.onnx"
    
    # Write dummy onnx file
    with open(onnx_path, "wb") as f:
        f.write(b"DUMMY_ONNX_CONTENT")

    with open(onnx_path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()

    manifest = {
      "contract_version": "1.0",
      "id": "leukaemia_wbc",
      "version": "1.0.0",
      "display_name": "Leukaemia (WBC)",
      "task_type": "classifier",
      "runtime": "onnx",
      "compute": "phone",
      "model": {
        "file": "model.onnx",
        "sha256": sha256
      },
      "input": {
        "tensor": "input",
        "shape": [1, 3, 64, 64],
        "layout": "NCHW",
        "dtype": "float32",
        "color": "RGB"
      },
      "preprocess": {
        "source": "cells",
        "cell_type": "wbc",
        "resize": "stretch",
        "scale": 1.0,
        "mean": [0.0, 0.0, 0.0],
        "std": [1.0, 1.0, 1.0],
        "stain_normalization": "none"
      },
      "output": {
        "decoder": "softmax",
        "labels": ["hem", "all"]
      },
      "quality": {
        "min_blur": 50.0,
        "max_clipped_fraction": 0.05
      },
      "aggregation": {
        "image_score": "max",
        "min_fields": 1
      },
      "triage": {
        "provisional": True,
        "source": "UNVERIFIED placeholder",
        "rules": [
          {
            "id": "refer_all",
            "level": "ABNORMAL_FLAG",
            "all": [
              { "metric": "count", "labels": ["all"], "op": ">=", "value": 1 }
            ]
          },
          {
            "id": "routine_hem",
            "level": "NORMAL_SCREEN",
            "all": [
              { "metric": "count", "labels": ["hem"], "op": ">=", "value": 1 }
            ]
          },
          {
            "id": "unknown",
            "level": "NEEDS_EXPERT",
            "all": [
              { "metric": "count", "labels": ["all", "hem"], "op": "==", "value": 0 }
            ]
          }
        ]
      },
      "uncertainty": {
        "method": "none"
      },
      "provenance": {
        "source": "Evaluation placeholder",
        "licence": "UNVERIFIED",
        "training_data": ["C-NMC Leukaemia dataset (ISBI 2019) via Kaggle"],
        "notes": "Evaluation placeholder model."
      }
    }

    with open(PACK_DIR / "manifest.json", "w") as f:
        json.dump(manifest, f, indent=2)

    for i in range(1, 4):
        # Create empty dummy jpeg
        with open(GOLDEN_DIR / f"case{i}.jpg", "wb") as f:
            f.write(b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.' \",#\x1c\x1c(7),01444\x1f'9=82<.342\xff\xdb\x00C\x01\t\t\t\x0c\x0b\x0c\x18\r\r\x182!\x1c!22222222222222222222222222222222222222222222222222\xff\xc0\x00\x11\x08\x01\x00\x01\x00\x03\x01\"\x00\x02\x11\x01\x03\x11\x01\xff\xc4\x00\x1f\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\xff\xc4\x00\xb5\x10\x00\x02\x01\x03\x03\x02\x04\x03\x05\x05\x04\x04\x00\x00\x01}\x01\x02\x03\x00\x04\x11\x05\x12!1A\x06\x13Qa\x07\"q\x142\x81\x91\xa1\x08#B\xb1\xc1\x15R\xd1\xf0$3br\x82\t\n\x16\x17\x18\x19\x1a%&'()*456789:CDEFGHIJSTUVWXYZcdefghijstuvwxyz\x83\x84\x85\x86\x87\x88\x89\x8a\x92\x93\x94\x95\x96\x97\x98\x99\x9a\xa2\xa3\xa4\xa5\xa6\xa7\xa8\xa9\xaa\xb2\xb3\xb4\xb5\xb6\xb7\xb8\xb9\xba\xc2\xc3\xc4\xc5\xc6\xc7\xc8\xc9\xca\xd2\xd3\xd4\xd5\xd6\xd7\xd8\xd9\xda\xe1\xe2\xe3\xe4\xe5\xe6\xe7\xe8\xe9\xea\xf1\xf2\xf3\xf4\xf5\xf6\xf7\xf8\xf9\xfa\xff\xc4\x00\x1f\x01\x00\x03\x01\x01\x01\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\xff\xc4\x00\xb5\x11\x00\x02\x01\x02\x04\x04\x03\x04\x07\x05\x04\x04\x00\x01\x02w\x00\x01\x02\x03\x11\x04\x05!1\x06\x12AQ\x07aq\x13\"2\x81\x08\x14B\x91\xa1\xb1\xc1\t#3R\xf0\x15br\xd1\n\x16$4\xe1%\xf1\x17\x18\x19\x1a&'()*56789:CDEFGHIJSTUVWXYZcdefghijstuvwxyz\x82\x83\x84\x85\x86\x87\x88\x89\x8a\x92\x93\x94\x95\x96\x97\x98\x99\x9a\xa2\xa3\xa4\xa5\xa6\xa7\xa8\xa9\xaa\xb2\xb3\xb4\xb5\xb6\xb7\xb8\xb9\xba\xc2\xc3\xc4\xc5\xc6\xc7\xc8\xc9\xca\xd2\xd3\xd4\xd5\xd6\xd7\xd8\xd9\xda\xe2\xe3\xe4\xe5\xe6\xe7\xe8\xe9\xea\xf2\xf3\xf4\xf5\xf6\xf7\xf8\xf9\xfa\xff\xda\x00\x0c\x03\x01\x00\x02\x11\x03\x11\x00?\x00\xfd\xfc\xa8\xa2\x8a\xfc\xff\xd9")
            
        result = {
            "image": f"case{i}.jpg",
            "objects": [
                {
                    "label": "hem" if i % 2 == 0 else "all",
                    "score": 0.99,
                    "box": [0.1, 0.1, 0.2, 0.2]
                }
            ],
            "quality": {
                "blur": 100.0,
                "clipped_fraction": 0.0
            }
        }
        with open(GOLDEN_DIR / f"expected{i}.json", "w") as f:
            json.dump(result, f, indent=2)

    print("Created dummy pack.")

if __name__ == "__main__":
    main()
