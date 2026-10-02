"""Golden case for the smoke pack, derived from the desktop PyTorch output in smoke/expected.json (never from Kotlin).

Writes android/engine/src/androidTest/assets/smoke/golden/{case1.png,case1.json,tolerance.json}.
Input is the 64x64 pattern of TensorPreprocessorDeviceTest.smokeImage(); its desktop output is probs[0:2] of
expected.json. The model already emits probabilities and the manifest declares softmax, so the decoder softmaxes
them again; that is what the golden encodes. quality.blur_score/exposure_score are 0.0 placeholders: the golden
comparator ignores them (it compares quality.pass and reasons).

Run: python ml/eval/make_smoke_golden.py   (stdlib only)
"""
import json
import math
import struct
import zlib
from pathlib import Path

SMOKE = Path(__file__).resolve().parents[2] / "android/engine/src/androidTest/assets/smoke"
GOLDEN = SMOKE / "golden"
SIDE = 64


def byte(i: int) -> int:
    return i * 7919 % 256


def write_png(path: Path) -> None:
    channel = SIDE * SIDE
    rows = []
    for y in range(SIDE):
        row = bytearray([0])  # filter type 0
        for x in range(SIDE):
            p = y * SIDE + x
            row += bytes((byte(p), byte(channel + p), byte(2 * channel + p)))
        rows.append(bytes(row))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", SIDE, SIDE, 8, 2, 0, 0, 0))  # 8-bit RGB
    png += chunk(b"IDAT", zlib.compress(b"".join(rows)))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


def main() -> None:
    probs = json.loads((SMOKE / "expected.json").read_text())["probs"][:2]
    e0, e1 = math.exp(probs[0]), math.exp(probs[1])
    p = [e0 / (e0 + e1), e1 / (e0 + e1)]  # [negative, positive]
    top = 0 if p[0] >= p[1] else 1
    labels = ["negative", "positive"]
    field_result = {
        "case_id": "golden",
        "field_id": "case1",
        "pack_id": "smoke",
        "pack_version": "0.1.0",
        "quality": {"pass": True, "blur_score": 0.0, "exposure_score": 0.0, "reasons": []},
        "router": {"verdict": "match", "score": 1.0},
        "objects": [{"label": labels[top], "score": p[top]}],
        "counts": {"negative": int(top == 0), "positive": int(top == 1)},
        "image_score": p[1],
        "uncertainty": {"flag": False, "reason": None},
        "timing_ms": {},
    }
    GOLDEN.mkdir(exist_ok=True)
    write_png(GOLDEN / "case1.png")
    (GOLDEN / "case1.json").write_text(json.dumps(field_result, indent=2) + "\n")
    (GOLDEN / "tolerance.json").write_text(json.dumps({"score": 0.02, "box_px": 2.0}, indent=2) + "\n")


if __name__ == "__main__":
    main()
