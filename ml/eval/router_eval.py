"""Router accuracy on the held-out sources, per source and overall (the numbers #22 records in #31)."""
from collections import defaultdict
from dataclasses import dataclass


@dataclass(frozen=True)
class Prediction:
    true: str
    source: str
    predicted: str


def _acc(n: int, correct: int) -> dict:
    return {"n": n, "correct": correct, "accuracy": correct / n}


def accuracy_by_source(preds: list[Prediction]) -> dict:
    if not preds:
        raise ValueError("no predictions")
    per = defaultdict(lambda: [0, 0])
    confusion: dict[str, dict[str, int]] = {}
    for p in preds:
        hit = p.true == p.predicted
        per[f"{p.true}/{p.source}"][0] += 1
        per[f"{p.true}/{p.source}"][1] += hit
        if not hit:
            row = confusion.setdefault(p.true, {})
            row[p.predicted] = row.get(p.predicted, 0) + 1
    return {
        "by_source": {k: _acc(*v) for k, v in sorted(per.items())},
        "overall": _acc(len(preds), sum(v[1] for v in per.values())),
        "confusion": confusion,
    }
