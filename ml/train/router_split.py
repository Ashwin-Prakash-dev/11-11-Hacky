"""Router split manifest (#8 output, #22 input): file lists with a named held-out source per class.

Format (no images, just paths relative to the dataset root):
  {"schema_version": 1, "reject_class": "reject",
   "heldout": {"<class>": "<source>", ...},
   "files": [{"path": "...", "class": "<pack id or reject>", "source": "<dataset name>"}, ...]}
Labels are derived from `files`, never hardcoded: one per pack id, with the reject class last.
"""
import json
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

from ml.tools.malaria_reservations import assert_no_reserved_patients


class SplitError(ValueError):
    pass


@dataclass(frozen=True)
class SplitFile:
    path: str
    cls: str
    source: str


@dataclass(frozen=True)
class Split:
    labels: list[str]
    train: list[SplitFile]
    test: list[SplitFile]  # the held-out source of every class


def load_split(path: Path) -> Split:
    doc = json.loads(Path(path).read_text())
    if doc.get("schema_version") != 1:
        raise SplitError("schema_version must be 1")
    reject = doc.get("reject_class", "reject")
    try:
        assert_no_reserved_patients(doc["files"])
    except ValueError as error:
        raise SplitError(str(error)) from error
    files = [SplitFile(f["path"], f["class"], f["source"]) for f in doc["files"]]
    paths = [f.path for f in files]
    if len(set(paths)) != len(paths):
        raise SplitError("duplicate path in files")

    sources = defaultdict(set)
    for f in files:
        sources[f.cls].add(f.source)
    if reject not in sources:
        raise SplitError(f"no files for the reject class '{reject}'")
    heldout = doc.get("heldout", {})
    for cls, srcs in sources.items():
        if len(srcs) < 2:
            raise SplitError(f"class '{cls}' needs at least 2 sources, has {sorted(srcs)}")
        if heldout.get(cls) not in srcs:
            raise SplitError(f"heldout source for '{cls}' must be one of {sorted(srcs)}, got {heldout.get(cls)!r}")

    labels = sorted(c for c in sources if c != reject) + [reject]
    train = [f for f in files if f.source != heldout[f.cls]]
    test = [f for f in files if f.source == heldout[f.cls]]
    return Split(labels, train, test)
