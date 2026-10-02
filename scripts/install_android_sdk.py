#!/usr/bin/env python3
"""Install the pinned Android SDK needed by this repository."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import platform
import shutil
import stat
import subprocess
import sys
import tempfile

import install_platform_tools


COMMAND_LINE_TOOLS_VERSION = "23.0"
SDK_PACKAGES = ("build-tools/36.0.0@36.0.0", "platforms/android-37.0@2.0.0")
COMMAND_LINE_ARCHIVES = {
    ("Darwin", "arm64"): (
        "commandlinetools-mac_arm64-16111833_latest.zip",
        "ad03dc49bfacfd52c110b14104ea548b8a07e830",
    ),
    ("Darwin", "x86_64"): (
        "commandlinetools-mac_x86_64-16111833_latest.zip",
        "112cf9618794a997ff273537d55bee02c22abffe",
    ),
    ("Linux", "x86_64"): (
        "commandlinetools-linux-16111833_latest.zip",
        "e025545c62a8e64c7559119566a569fb1dec5f60",
    ),
    ("Windows", "x86_64"): (
        "commandlinetools-win-16111833_latest.zip",
        "57d04f2d75eb8e8fffc5000a987e5de4b5a63e9d",
    ),
}


def normalized_architecture(machine: str) -> str:
    normalized = machine.lower()
    if normalized in {"arm64", "aarch64"}:
        return "arm64"
    if normalized in {"amd64", "x86_64"}:
        return "x86_64"
    raise RuntimeError(f"Unsupported host architecture: {machine}")


def command_line_spec(system: str, machine: str) -> tuple[str, str]:
    key = (system, normalized_architecture(machine))
    try:
        return COMMAND_LINE_ARCHIVES[key]
    except KeyError as exc:
        raise RuntimeError(f"Unsupported Android SDK host: {key[0]} {key[1]}") from exc


def sha1(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sdk_root(prefix: Path) -> Path:
    return prefix / "share" / "deepsight" / "android-sdk"


def extract_command_line_tools(archive: Path, destination: Path) -> Path:
    import zipfile

    root = destination.resolve()
    with zipfile.ZipFile(archive) as bundle:
        for member in bundle.infolist():
            target = (destination / member.filename).resolve()
            if root != target and root not in target.parents:
                raise RuntimeError(f"Unsafe archive member: {member.filename}")
        bundle.extractall(destination)
    tools = destination / "cmdline-tools"
    if not tools.is_dir():
        raise RuntimeError("Archive does not contain cmdline-tools/")
    return tools


def install_command_line_tools(
    root: Path,
    system: str,
    machine: str,
    archive: Path | None = None,
) -> Path:
    filename, expected_sha1 = command_line_spec(system, machine)
    destination = root / "cmdline-tools" / COMMAND_LINE_TOOLS_VERSION
    executable = destination / "bin" / ("android.exe" if system == "Windows" else "android")
    if executable.is_file():
        return executable
    if destination.exists():
        raise RuntimeError(f"Incomplete command-line tools installation at {destination}")

    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent) as temporary:
        temporary_path = Path(temporary)
        downloaded = archive or temporary_path / filename
        if archive is None:
            install_platform_tools.download(
                f"{install_platform_tools.BASE_URL}/{filename}", downloaded
            )
        actual_sha1 = sha1(downloaded)
        if actual_sha1 != expected_sha1:
            raise RuntimeError(
                f"Checksum mismatch for {filename}: expected {expected_sha1}, got {actual_sha1}"
            )
        extracted = extract_command_line_tools(downloaded, temporary_path / "extract")
        shutil.move(str(extracted), destination)

    if system != "Windows":
        for binary in (destination / "bin").iterdir():
            binary.chmod(binary.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    return executable


def write_local_properties(project_root: Path, root: Path) -> None:
    local_properties = project_root / "android" / "local.properties"
    escaped = str(root).replace("\\", "\\\\").replace(":", "\\:")
    sdk_line = f"sdk.dir={escaped}"
    lines = local_properties.read_text().splitlines() if local_properties.exists() else []
    replaced = False
    updated = []
    for line in lines:
        if line.startswith("sdk.dir="):
            updated.append(sdk_line)
            replaced = True
        else:
            updated.append(line)
    if not replaced:
        updated.append(sdk_line)
    local_properties.write_text("\n".join(updated) + "\n")


def install_sdk(
    prefix: Path,
    project_root: Path,
    system: str,
    machine: str,
    archive: Path | None = None,
) -> Path:
    root = sdk_root(prefix)
    android = install_command_line_tools(root, system, machine, archive)

    platform_tools = install_platform_tools.install(prefix, system)
    sdk_platform_tools = root / "platform-tools"
    if not sdk_platform_tools.exists():
        shutil.copytree(platform_tools, sdk_platform_tools)

    environment = os.environ.copy()
    environment["ANDROID_HOME"] = str(root)
    environment["ANDROID_SDK_ROOT"] = str(root)
    for package in SDK_PACKAGES:
        subprocess.run(
            [str(android), "--no-metrics", "--sdk", str(root), "sdk", "install", "--beta", package],
            check=True,
            env=environment,
        )
    write_local_properties(project_root, root)
    return root


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Install the pinned Android SDK platform and build tools for DeepSight."
    )
    parser.add_argument("--project-root", type=Path, required=True)
    parser.add_argument("--archive", type=Path, help="Use an already downloaded command-line archive")
    parser.add_argument("--prefix", type=Path, default=Path(sys.prefix), help=argparse.SUPPRESS)
    parser.add_argument("--system", default=platform.system(), help=argparse.SUPPRESS)
    parser.add_argument("--machine", default=platform.machine(), help=argparse.SUPPRESS)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    root = install_sdk(
        args.prefix.resolve(),
        args.project_root.resolve(),
        args.system,
        args.machine,
        args.archive,
    )
    print(f"Android SDK ready at {root}")


if __name__ == "__main__":
    main()
