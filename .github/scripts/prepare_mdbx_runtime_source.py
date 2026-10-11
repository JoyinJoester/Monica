#!/usr/bin/env python3
"""Reconstruct the Android runtime source from its pinned base and verified overlay."""

import argparse
import json
import subprocess
from pathlib import Path

from verify_mdbx3_bundled_runtime import source_sha256, verify


def prepare(root: Path, source: Path) -> None:
    verify(root)
    engine = root / "Monica for Android/mdbx-engine"
    manifest = json.loads((engine / "GLITTER_SOURCE_MANIFEST.json").read_text(encoding="utf-8"))
    head = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if head != manifest["base_commit"]:
        raise ValueError("MDBX checkout does not match the Android source manifest")
    if subprocess.check_output(["git", "-C", str(source), "status", "--porcelain"], text=True).strip():
        raise ValueError("Apply the runtime overlay only to a clean, isolated checkout")
    patch = (engine / manifest["patch"]).resolve()
    if not patch.is_relative_to(engine) or source_sha256(patch) != manifest["patch_sha256"]:
        raise ValueError("MDBX source overlay SHA-256 mismatch")
    subprocess.run(["git", "-C", str(source), "apply", "--check", str(patch)], check=True)
    subprocess.run(["git", "-C", str(source), "apply", str(patch)], check=True)
    for name, expected in manifest["files"].items():
        path = (source / name).resolve()
        if not path.is_relative_to(source) or source_sha256(path) != expected:
            raise ValueError(f"Reconstructed MDBX source mismatch: {name}")
    print(f"Verified {len(manifest['files'])} MDBX source files for the current Android bindings")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--source", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.root.resolve(), args.source.resolve())
