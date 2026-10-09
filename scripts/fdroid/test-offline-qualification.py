#!/usr/bin/env python3
"""Safety regression tests for S.17.3 unsigned offline Android evidence."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

SCRIPT = Path(__file__).with_name("report-offline-android.py")
spec = importlib.util.spec_from_file_location("offline_report", SCRIPT)
assert spec and spec.loader
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def write_zip(path: Path, abi_set: tuple[str, ...] = ("arm64-v8a", "armeabi-v7a", "x86_64")) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        for abi in abi_set:
            archive.writestr(f"lib/{abi}/libnexoragit_native.so", b"fake-test-data")
        archive.writestr("AndroidManifest.xml", b"test-only")


class EvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.apk = self.root / "app-release-unsigned.apk"
        self.aab = self.root / "app-release.aab"
        self.aapt = self.root / "aapt"
        self.aapt.write_text(
            "#!/bin/sh\necho \"package: name='com.nexora.git' versionCode='10000'\"\n",
            encoding="utf-8",
        )
        self.aapt.chmod(0o755)
        write_zip(self.apk)
        write_zip(self.aab)

    def test_unsigned_artifact_evidence_never_qualifies_release(self) -> None:
        data = report.qualify(self.apk, self.aab, self.aapt, self.apk, self.aab)
        self.assertEqual("com.nexora.git", data["packageId"])
        self.assertFalse(data["releaseQualified"])
        self.assertFalse(data["independentBuildsCompared"])
        self.assertFalse(data["fdroidserverQualified"])
        self.assertTrue(data["warmupVsOffline"]["apkByteIdenticalToOnlineWarmup"])

    def test_reject_missing_native_abi(self) -> None:
        write_zip(self.apk, ("arm64-v8a",))
        with self.assertRaisesRegex(ValueError, "native ABI"):
            report.qualify(self.apk, self.aab, self.aapt, None, None)

    def test_reject_wrong_package_identity(self) -> None:
        self.aapt.write_text(
            "#!/bin/sh\necho \"package: name='com.nexora.git.ci' versionCode='10000'\"\n",
            encoding="utf-8",
        )
        with self.assertRaisesRegex(ValueError, "application ID"):
            report.qualify(self.apk, self.aab, self.aapt, None, None)

    def test_reject_invalid_zip(self) -> None:
        self.apk.write_text("not an APK", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "ZIP"):
            report.qualify(self.apk, self.aab, self.aapt, None, None)

    def test_reject_duplicate_zip_entries(self) -> None:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apk, "a") as archive:
                archive.writestr("AndroidManifest.xml", b"duplicate")
        with self.assertRaisesRegex(ValueError, "Duplicate ZIP"):
            report.qualify(self.apk, self.aab, self.aapt, None, None)

    def test_reject_empty_artifact(self) -> None:
        self.aab.write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "empty"):
            report.qualify(self.apk, self.aab, self.aapt, None, None)


if __name__ == "__main__":
    unittest.main(verbosity=2)
