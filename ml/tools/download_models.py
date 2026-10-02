#!/usr/bin/env python3
"""Download pinned DeepSight model candidates and verify their SHA-256 digests."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


APPROVED_STATUS = "approved_for_evaluation"
REQUIRED_FIELDS = {"id", "filename", "url", "sha256", "status"}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


class ChecksumMismatch(RuntimeError):
    """Raised when a downloaded artifact does not match its pinned digest."""


@dataclass(frozen=True)
class ModelArtifact:
    id: str
    filename: str
    url: str
    sha256: str
    status: str
    license: str = "UNVERIFIED"


@dataclass(frozen=True)
class ModelManifest:
    schema_version: int
    artifacts: tuple[ModelArtifact, ...]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_manifest(path: Path) -> ModelManifest:
    raw: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    if raw.get("schema_version") != 1:
        raise ValueError("manifest schema_version must be 1")
    raw_artifacts = raw.get("artifacts")
    if not isinstance(raw_artifacts, list):
        raise ValueError("manifest artifacts must be a list")

    seen_ids: set[str] = set()
    seen_filenames: set[str] = set()
    artifacts: list[ModelArtifact] = []
    for index, item in enumerate(raw_artifacts):
        if not isinstance(item, dict):
            raise ValueError(f"artifact {index} must be an object")
        missing = REQUIRED_FIELDS - set(item)
        if missing:
            raise ValueError(f"artifact {index} missing fields: {sorted(missing)}")
        required_values = {field: item[field] for field in REQUIRED_FIELDS}
        if not all(isinstance(value, str) for value in required_values.values()):
            raise ValueError(f"artifact {index} required fields must be strings")
        artifact = ModelArtifact(
            id=item["id"],
            filename=item["filename"],
            url=item["url"],
            sha256=item["sha256"],
            status=item["status"],
            license=item.get("license", "UNVERIFIED"),
        )
        if artifact.id in seen_ids:
            raise ValueError(f"duplicate artifact id: {artifact.id}")
        if artifact.filename in seen_filenames:
            raise ValueError(f"duplicate artifact filename: {artifact.filename}")
        if Path(artifact.filename).name != artifact.filename:
            raise ValueError(f"artifact filename must be a basename: {artifact.filename}")
        if not SHA256_RE.fullmatch(artifact.sha256):
            raise ValueError(f"artifact {artifact.id} has an invalid sha256")
        seen_ids.add(artifact.id)
        seen_filenames.add(artifact.filename)
        artifacts.append(artifact)
    return ModelManifest(schema_version=1, artifacts=tuple(artifacts))


def select_artifacts(
    manifest: ModelManifest,
    *,
    include_unverified: bool,
    requested_ids: set[str] | None = None,
) -> list[ModelArtifact]:
    artifacts = manifest.artifacts
    selected = [
        artifact
        for artifact in artifacts
        if include_unverified or artifact.status == APPROVED_STATUS
    ]
    if requested_ids is not None:
        known_ids = {artifact.id for artifact in artifacts}
        unknown = requested_ids - known_ids
        if unknown:
            raise ValueError(f"unknown model ids: {sorted(unknown)}")
        selected = [artifact for artifact in selected if artifact.id in requested_ids]
        unavailable = requested_ids - {artifact.id for artifact in selected}
        if unavailable:
            raise ValueError(
                "unverified models require --include-unverified: "
                f"{sorted(unavailable)}"
            )
    return selected


def download_artifact(artifact: ModelArtifact, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / artifact.filename
    expected = artifact.sha256
    if target.exists() and sha256_file(target) == expected:
        print(f"ok   {artifact.id}: already verified at {target}")
        return target

    partial = output_dir / f"{artifact.filename}.part"
    partial.unlink(missing_ok=True)
    request = urllib.request.Request(
        artifact.url,
        headers={"User-Agent": "DeepSight-model-downloader/1.0"},
    )
    try:
        with urllib.request.urlopen(request) as response, partial.open("wb") as stream:
            while chunk := response.read(1024 * 1024):
                stream.write(chunk)
        actual = sha256_file(partial)
        if actual != expected:
            raise ChecksumMismatch(
                f"{artifact.id}: expected {expected}, downloaded {actual}"
            )
        os.replace(partial, target)
    except Exception:
        partial.unlink(missing_ok=True)
        raise

    print(f"ok   {artifact.id}: downloaded and verified at {target}")
    return target


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument(
        "--model",
        action="append",
        default=[],
        help="Download only this model id; may be supplied more than once.",
    )
    parser.add_argument(
        "--include-unverified",
        action="store_true",
        help="Allow candidates whose licensing or provenance is not fully verified.",
    )
    parser.add_argument("--list", action="store_true", help="List artifacts without downloading.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    manifest = load_manifest(args.manifest)
    requested = set(args.model) if args.model else None
    artifacts = select_artifacts(
        manifest,
        include_unverified=args.include_unverified,
        requested_ids=requested,
    )
    for artifact in artifacts:
        print(
            f"{artifact.id}: status={artifact.status} "
            f"license={artifact.license}"
        )
        if not args.list:
            download_artifact(artifact, args.output_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
