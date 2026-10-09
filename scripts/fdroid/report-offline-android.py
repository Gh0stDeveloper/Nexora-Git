#!/usr/bin/env python3
"""Generate strictly non-release evidence for a network-isolated Android build.

The report is evidence of one compilation, not F-Droid reproducibility or a
publishable artifact. Do not include an Android keystore or authentication data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

REPO = Path(__file__).resolve().parents[2]
ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64"}
FINGERPRINT_PATTERN = re.compile(r"^package: name='([^']+)'")
HASH_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def artifact(path: Path) -> dict[str, object]:
    if not path.is_file() or path.stat().st_size <= 0:
        raise ValueError(f"Missing or empty artifact: {path.name}")
    if not zipfile.is_zipfile(path):
        raise ValueError(f"Invalid ZIP package: {path.name}")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f"Duplicate ZIP entries: {path.name}")
        native = sorted(n for n in names if n.startswith("lib/") and n.endswith(".so"))
    return {
        "filename": path.name,
        "sizeBytes": path.stat().st_size,
        "sha256": sha256(path),
        "nativeLibraries": native,
    }


def package_id(apk: Path, aapt: Path) -> str:
    proc = subprocess.run([str(aapt), "dump", "badging", str(apk)],
                          capture_output=True, text=True, timeout=30, check=False)
    if proc.returncode != 0:
        raise ValueError("Android package badging check failed")
    for line in proc.stdout.splitlines():
        match = FINGERPRINT_PATTERN.match(line)
        if match:
            return match.group(1)
    raise ValueError("APK package ID missing")


def qualify(apk: Path, aab: Path, aapt: Path, warm_apk: Path | None, warm_aab: Path | None) -> dict[str, object]:
    actual_package = package_id(apk, aapt)
    if actual_package != "com.nexora.git":
        raise ValueError("Unexpected Android application ID in offline build")
    apk_data, aab_data = artifact(apk), artifact(aab)
    abi_dirs = {name.split("/")[1] for name in apk_data["nativeLibraries"]}
    if not ABIS.issubset(abi_dirs):
        raise ValueError(f"Offline APK is missing native ABI coverage: {sorted(ABIS - abi_dirs)}")
    warm_comparison = None
    if warm_apk is not None and warm_aab is not None:
        wa, wb = artifact(warm_apk), artifact(warm_aab)
        warm_comparison = {
            "apkByteIdenticalToOnlineWarmup": wa["sha256"] == apk_data["sha256"],
            "aabByteIdenticalToOnlineWarmup": wb["sha256"] == aab_data["sha256"],
            "warmApkSha256": wa["sha256"],
            "warmAabSha256": wb["sha256"],
        }
    return {
        "schemaVersion": 1,
        "phase": "S.17.3",
        "status": "one-network-isolated-android-gradle-build",
        "sourceCommit": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=REPO, text=True
        ).strip(),
        "packageId": actual_package,
        "offlineGradle": True,
        "networkNamespaceIsolated": True,
        "releaseQualified": False,
        "independentBuildsCompared": False,
        "fdroidserverQualified": False,
        "inputs": {
            "nativeSourcesLockSha256": sha256(REPO / "native/git/fdroid-sources.lock.json"),
            "libgit2PatchSha256": sha256(REPO / "native/git/cmake/libgit2-no-install.patch"),
            "gradleVersionsSha256": sha256(REPO / "gradle/libs.versions.toml"),
            "gradleWrapperPropertiesSha256": sha256(REPO / "gradle/wrapper/gradle-wrapper.properties"),
        },
        "apk": apk_data,
        "aab": aab_data,
        "warmupVsOffline": warm_comparison,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--aab", required=True, type=Path)
    parser.add_argument("--aapt", required=True, type=Path)
    parser.add_argument("--warm-apk", type=Path)
    parser.add_argument("--warm-aab", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        if (args.warm_apk is None) != (args.warm_aab is None):
            raise ValueError("Warm-up artifact paths must be provided together")
        result = qualify(args.apk, args.aab, args.aapt, args.warm_apk, args.warm_aab)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        print("Offline Android evidence recorded (not a release or reproducibility qualification).")
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        print(f"S.17.3 qualification failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
