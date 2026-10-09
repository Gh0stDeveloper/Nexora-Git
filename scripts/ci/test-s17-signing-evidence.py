#!/usr/bin/env python3
"""Unit coverage of public-signing certificate parsing without private keys."""

import runpy
from pathlib import Path
import tempfile

module = runpy.run_path(str(Path(__file__).with_name("create-release-signing-evidence.py")))
digest = module["cert_digest"]
cert_pattern = module["CERT_RE"]
keytool_pattern = module["KEYTOOL_RE"]
sha256 = module["sha256"]

certificate = "F" * 64
assert digest("Signer #1 certificate SHA-256 digest: " + certificate, cert_pattern, "APK") == certificate
assert digest("  SHA256: " + ":".join(["ff"] * 32), keytool_pattern, "AAB") == certificate

for output, pattern in [
    ("unsigned", cert_pattern),
    ("Signer #1 certificate SHA-256 digest: " + certificate + "\nSigner #2 certificate SHA-256 digest: " + certificate, cert_pattern),
    ("SHA256: not-a-valid-fingerprint", keytool_pattern),
]:
    try:
        digest(output, pattern, "negative fixture")
    except ValueError:
        pass
    else:
        raise AssertionError("Invalid or ambiguous signing certificate was accepted")

with tempfile.TemporaryDirectory() as directory:
    file = Path(directory) / "apk"
    file.write_bytes(b"safe-public-digest-test")
    assert sha256(file) == __import__("hashlib").sha256(file.read_bytes()).hexdigest()

print("S.17 signing identity parser tests passed.")
