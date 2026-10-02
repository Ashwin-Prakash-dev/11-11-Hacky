"""Reproduce README metrics. Data: git clone --depth 1 https://github.com/PerceptiLabs/breakhis-400x
Usage: python eval_breakhis.py breakhis-400x/data"""
import glob, os, sys, numpy as np, onnxruntime as ort
from PIL import Image
fs = sorted(glob.glob(os.path.join(sys.argv[1], "*", "*", "*.png"))); y = np.array(["/malignant/" in f for f in fs], int)
s = ort.InferenceSession(os.path.join(os.path.dirname(__file__), "..", "model.onnx"))
p = np.concatenate([s.run(None, {"input": np.stack([np.asarray(Image.open(f).convert("RGB").resize((224, 224), Image.BILINEAR), np.float32).transpose(2, 0, 1) / 255 for f in fs[i:i + 32]])})[0][:, 1] for i in range(0, len(fs), 32)])
pr = p > 0.5; P, N = y.sum(), (1 - y).sum(); r = p.argsort().argsort() + 1
print(f"n={len(y)} acc={(pr == y).mean():.4f} sens={(pr & (y == 1)).sum() / P:.4f} spec={(~pr & (y == 0)).sum() / N:.4f} AUC={(r[y == 1].sum() - P * (P + 1) / 2) / (P * N):.4f}")
