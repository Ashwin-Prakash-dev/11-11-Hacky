"""Train the router: a small pretrained classifier with one class per pack id plus `reject` (#22).

Run: python ml/train/train_router.py --split <split.json> --data-root <dir> --out <dir>
Writes router.onnx (input "input" NCHW float32, output softmax over labels), labels.json and eval.json
(accuracy on the held-out source of each class, from ml/eval/router_eval.py). Never commit the outputs.
Preprocessing is stretch-resize + ImageNet mean/std, baked into the ONNX so the phone feeds RGB in [0,1].
"""
import argparse
import json
from pathlib import Path

import torch
from PIL import Image
from torch import nn
from torchvision import models, transforms

from ml.eval.router_eval import Prediction, accuracy_by_source
from ml.train.router_split import SplitFile, load_split

MEAN, STD = [0.485, 0.456, 0.406], [0.229, 0.224, 0.225]


class Router(nn.Module):
    def __init__(self, backbone: nn.Module, n_classes: int):
        super().__init__()
        self.backbone = backbone
        self.register_buffer("mean", torch.tensor(MEAN).view(1, 3, 1, 1))
        self.register_buffer("std", torch.tensor(STD).view(1, 3, 1, 1))
        self.head = nn.Linear(1280, n_classes)  # MobileNetV2 feature width

    def forward(self, x):
        z = self.backbone((x - self.mean) / self.std)
        z = torch.flatten(nn.functional.adaptive_avg_pool2d(z, 1), 1)
        return torch.softmax(self.head(z), dim=1)


def _load(files: list[SplitFile], labels: list[str], root: Path, size: int):
    tf = transforms.Compose([transforms.Resize((size, size)), transforms.ToTensor()])
    x = torch.stack([tf(Image.open(root / f.path).convert("RGB")) for f in files])
    y = torch.tensor([labels.index(f.cls) for f in files])
    return x, y


def run(split_path: Path, data_root: Path, out: Path, epochs=5, image_size=224, pretrained=True, seed=0):
    torch.manual_seed(seed)
    split = load_split(split_path)
    xtr, ytr = _load(split.train, split.labels, data_root, image_size)
    xte, _ = _load(split.test, split.labels, data_root, image_size)

    # ponytail: ImageNet weights licence UNVERIFIED (torchvision); record in ml/models.json before shipping
    backbone = models.mobilenet_v2(weights=models.MobileNet_V2_Weights.DEFAULT if pretrained else None).features
    model = Router(backbone, len(split.labels))
    opt = torch.optim.AdamW(model.parameters(), lr=1e-3)
    for _ in range(epochs):
        model.train()
        for idx in torch.randperm(len(xtr)).split(32):
            opt.zero_grad()
            # the model outputs probabilities, so the loss is NLL on their log
            nn.functional.nll_loss(torch.log(model(xtr[idx]) + 1e-9), ytr[idx]).backward()
            opt.step()

    model.eval()
    with torch.no_grad():
        pred = model(xte).argmax(dim=1).tolist()
    report = accuracy_by_source([Prediction(f.cls, f.source, split.labels[p]) for f, p in zip(split.test, pred)])

    out.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(model, torch.rand(1, 3, image_size, image_size), str(out / "router.onnx"),
                      input_names=["input"], output_names=["output"], opset_version=17,
                      dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}}, dynamo=False)
    (out / "labels.json").write_text(json.dumps(split.labels))
    (out / "eval.json").write_text(json.dumps(report, indent=2))
    return report, model


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--split", type=Path, required=True)
    ap.add_argument("--data-root", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--epochs", type=int, default=5)
    a = ap.parse_args()
    print(json.dumps(run(a.split, a.data_root, a.out, a.epochs)[0], indent=2))
