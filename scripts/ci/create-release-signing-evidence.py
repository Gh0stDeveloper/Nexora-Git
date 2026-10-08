#!/usr/bin/env python3
"""Produce public APK/AAB signing identity evidence from verified release bytes.

No keystore, password, private key, OAuth token or signing configuration is read.
Outputs are public, deterministic for the supplied APK/AAB and source commit.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

CERT_RE = re.compile(r"Signer #\d+ certificate SHA-256 digest:\s*([A-Fa-f0-9:]{64,95})", re.I)
KEYTOOL_RE = re.compile(r"\bSHA256:\s*([A-Fa-f0-9:]{64,95})", re.I)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def cert_digest(output: str, pattern: re.Pattern[str], origin: str) -> str:
    matches = pattern.findall(output)
    if len(matches) != 1:
        raise ValueError(f"{origin}: exactly one signing certificate is required, found {len(matches)}")
    result = matches[0].replace(":", "").upper()
    if not re.fullmatch(r"[A-F0-9]{64}", result):
        raise ValueError(f"{origin}: invalid certificate fingerprint")
    return result


def command_output(args: list[str]) -> str:
    result = subprocess.run(
        args, capture_output=True, text=True, env={**os.environ, "LC_ALL": "C"},
        check=False, timeout=60,
    )
    if result.returncode != 0:
        # Never echo command output that could contain environmental secrets.
        raise ValueError(f"signing verification command failed: {Path(args[0]).name} (code {result.returncode})")
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--apksigner", default="apksigner")
    args = parser.parse_args()

    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-(?:beta|rc)\.[0-9]+)?", args.version):
        parser.error("invalid public release version")
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("commit must be a full lowercase Git SHA")
    if not args.apk.is_file() or not args.aab.is_file():
        parser.error("signed APK and AAB must both exist")

    try:
        apk_cert = cert_digest(
            command_output([args.apksigner, "verify", "--verbose", "--print-certs", str(args.apk)]),
            CERT_RE,
            "APK",
        )
        aab_cert = cert_digest(
            command_output(["keytool", "-printcert", "-jarfile", str(args.aab)]),
            KEYTOOL_RE,
            "AAB",
        )
    except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
        print(f"Signing identity evidence rejected: {exc}", file=sys.stderr)
        return 1
    if apk_cert != aab_cert:
        print("Signing identity mismatch between APK and AAB", file=sys.stderr)
        return 1

    record = {
        "schemaVersion": 1,
        "applicationId": "com.nexora.git",
        "version": args.version,
        "sourceCommit": args.commit,
        "certificateSha256": apk_cert,
        "certificateScope": "github-release-direct-apk-and-play-upload-aab",
        "apk": {"file": args.apk.name, "sha256": sha256(args.apk)},
        "aab": {"file": args.aab.name, "sha256": sha256(args.aab)},
        "notice": "Google Play App Signing may use a distinct app-signing certificate; verify Play Console independently.",
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "SIGNING-CERTIFICATE-SHA256.txt").write_text(apk_cert + "\n", encoding="utf-8")
    (args.output / "SIGNING-IDENTITY.json").write_text(
        json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8",
    )
    print(f"Verified public APK/AAB signing identity: {apk_cert}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
