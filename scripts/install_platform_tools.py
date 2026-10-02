#!/usr/bin/env python3
"""Install pinned Android SDK Platform-Tools into the active Python environment."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import platform
import shutil
import stat
import sys
import tempfile
import urllib.request
import zipfile


VERSION = "37.0.1"
BASE_URL = "https://dl.google.com/android/repository"
MANAGED_MARKER = "Managed by DeepSight install_platform_tools.py"

# SHA-256 values measured from Google's versioned archives. Google's repository metadata
# publishes the matching SHA-1 values for these same files.
ARCHIVES = {
    "Darwin": (
        f"platform-tools_r{VERSION}-darwin.zip",
        "ee39ad5967e95c2a07f04dbcbde96b1a0c916ba376096db5d2f498b7727a5d1d",
    ),
    "Linux": (
        f"platform-tools_r{VERSION}-linux.zip",
        "d230f13842f60f782a8645f9c813f8f845bf36089ea7289f28c48f17979313f1",
    ),
    "Windows": (
        f"platform-tools_r{VERSION}-win.zip",
        "45f4d63113e895ebde0c90f194099a4676b6ac653bd28d54314a9e022bbc1a99",
    ),
}


def archive_spec(system: str) -> tuple[str, str]:
    try:
        return ARCHIVES[system]
    except KeyError as exc:
        supported = ", ".join(sorted(ARCHIVES))
        raise RuntimeError(f"Unsupported operating system {system!r}; expected {supported}") from exc


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, destination: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "DeepSight-setup/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def extract_archive(archive: Path, destination: Path) -> Path:
    root = destination.resolve()
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            target = (destination / member.filename).resolve()
            if root != target and root not in target.parents:
                raise RuntimeError(f"Unsafe archive member: {member.filename}")
        bundle.extractall(destination)

    platform_tools = destination / "platform-tools"
    if not platform_tools.is_dir():
        raise RuntimeError("Archive does not contain platform-tools/")
    return platform_tools


def launcher_paths(prefix: Path, system: str) -> tuple[Path, Path]:
    if system == "Windows":
        return prefix / "Scripts" / "adb.cmd", prefix / "Scripts" / "fastboot.cmd"
    return prefix / "bin" / "adb", prefix / "bin" / "fastboot"


def write_launcher(path: Path, tool: str, install_dir: Path, system: str) -> None:
    if path.exists() and MANAGED_MARKER not in path.read_text(errors="ignore"):
        raise RuntimeError(f"Refusing to replace unmanaged executable: {path}")

    path.parent.mkdir(parents=True, exist_ok=True)
    if system == "Windows":
        target = install_dir / f"{tool}.exe"
        content = f"@rem {MANAGED_MARKER}\r\n@\"{target}\" %*\r\n"
    else:
        target = install_dir / tool
        content = f'#!/bin/sh\n# {MANAGED_MARKER}\nexec "{target}" "$@"\n'
    path.write_text(content)
    if system != "Windows":
        path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def install(prefix: Path, system: str, archive: Path | None = None) -> Path:
    filename, expected_sha256 = archive_spec(system)
    libexec = "Library/libexec" if system == "Windows" else "libexec"
    install_root = prefix / libexec / "deepsight" / f"android-platform-tools-{VERSION}"
    install_dir = install_root / "platform-tools"

    if not (install_dir / ("adb.exe" if system == "Windows" else "adb")).is_file():
        if install_root.exists():
            raise RuntimeError(f"Incomplete installation exists at {install_root}; remove it and retry")
        install_root.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=install_root.parent) as temporary:
            temporary_path = Path(temporary)
            downloaded = archive or temporary_path / filename
            if archive is None:
                download(f"{BASE_URL}/{filename}", downloaded)
            actual_sha256 = sha256(downloaded)
            if actual_sha256 != expected_sha256:
                raise RuntimeError(
                    f"Checksum mismatch for {filename}: expected {expected_sha256}, got {actual_sha256}"
                )
            extracted = extract_archive(downloaded, temporary_path / "extract")
            shutil.move(str(extracted.parent), install_root)

    for executable in ("adb", "fastboot"):
        binary = install_dir / (f"{executable}.exe" if system == "Windows" else executable)
        if not binary.is_file():
            raise RuntimeError(f"Installed archive is missing {binary.name}")
        if system != "Windows":
            binary.chmod(binary.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    for launcher, tool in zip(launcher_paths(prefix, system), ("adb", "fastboot")):
        write_launcher(launcher, tool, install_dir, system)
    return install_dir


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=f"Install Android SDK Platform-Tools {VERSION} into the active environment."
    )
    parser.add_argument("--archive", type=Path, help="Use an already downloaded archive")
    parser.add_argument("--prefix", type=Path, default=Path(sys.prefix), help=argparse.SUPPRESS)
    parser.add_argument("--system", choices=sorted(ARCHIVES), default=platform.system(), help=argparse.SUPPRESS)
    parser.add_argument("--print-spec", action="store_true", help="Print the selected archive and exit")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    filename, expected_sha256 = archive_spec(args.system)
    if args.print_spec:
        print(f"{args.system}: {filename} sha256={expected_sha256}")
        return
    install_dir = install(args.prefix.resolve(), args.system, args.archive)
    print(f"Android SDK Platform-Tools {VERSION} installed at {install_dir}")


if __name__ == "__main__":
    main()
