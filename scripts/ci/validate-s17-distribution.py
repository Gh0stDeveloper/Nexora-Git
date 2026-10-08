#!/usr/bin/env python3
"""Fail-closed S.17 store metadata validation and optional release asset gate.

Default mode validates repository-prepared metadata. --release requires real
user-approved Play assets and a live privacy URL; it must never be bypassed
by placeholders or screenshots generated solely to satisfy CI.
"""

import argparse
from pathlib import Path
import re
import struct
import sys
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
LANGUAGES = ("en-US", "es-MX")
REQUIRED_FILES = (
    "docs/PLAY_STORE_READINESS.md",
    "docs/FDROID_READINESS.md",
    "docs/PRIVACY.md",
    "docs/store/DATA_SAFETY_REVIEW.md",
    "docs/store/ASSET_DELIVERY.md",
    "docs/store/SIGNING_TRANSPARENCY.md",
    "metadata/com.nexora.git.yml",
)


def fail(message: str) -> None:
    raise ValueError(message)


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"required file missing: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8").strip()


def validate_listing() -> None:
    for path in REQUIRED_FILES:
        read(ROOT / path)
    for lang in LANGUAGES:
        directory = ROOT / "fastlane/metadata/android" / lang
        title = read(directory / "title.txt")
        short = read(directory / "short_description.txt")
        full = read(directory / "full_description.txt")
        if title != "Nexora Git" or len(title) > 30:
            fail(f"{lang}: title must be the reviewed brand and <=30 characters")
        if not 20 <= len(short) <= 80 or "\n" in short:
            fail(f"{lang}: short description must be 20–80 characters on one line")
        if not 120 <= len(full) <= 4000:
            fail(f"{lang}: full description must be 120–4000 characters")
        if "GitHub" not in full or ("Android" not in full):
            fail(f"{lang}: full description must identify GitHub and Android")
        if not any(x in full.lower() for x in ("independent", "independiente")):
            fail(f"{lang}: independent-from-GitHub disclosure is missing")

    fdroid = read(ROOT / "metadata/com.nexora.git.yml")
    if "NonFreeNet:" not in fdroid:
        fail("F-Droid GitHub-dependent NonFreeNet declaration must be retained")
    if "disable:" not in fdroid:
        fail("F-Droid build is not reproducibly qualified: keep build entry disabled")
    if "com.nexora.git" not in (ROOT / "metadata/com.nexora.git.yml").name:
        fail("F-Droid application ID drift")


def image_size(path: Path) -> tuple[int, int]:
    if not path.is_file() or path.stat().st_size < 10000:
        fail(f"image missing/too small: {path}")
    with path.open("rb") as source:
        header = source.read(24)
    if header[:8] != bytes((137, 80, 78, 71, 13, 10, 26, 10)) or header[12:16] != b"IHDR":
        fail(f"not a PNG: {path}")
    return struct.unpack(">II", header[16:24])


def https_url(value: str) -> bool:
    try:
        info = urlsplit(value)
        return (
            info.scheme == "https"
            and bool(info.netloc)
            and not info.username
            and not info.password
            and info.hostname not in {"example.com", "localhost"}
            and not (info.hostname or "").endswith((".example", ".invalid", ".test"))
        )
    except ValueError:
        return False


def validate_release_assets(privacy_url: str, source_url: str) -> None:
    if not https_url(privacy_url) or not https_url(source_url):
        fail("release requires real HTTPS Privacy and Support/Source URLs")
    if not privacy_url.rstrip("/").endswith("/privacy"):
        fail("privacy URL must identify a dedicated public Privacy policy page")

    for lang in LANGUAGES:
        directory = ROOT / "fastlane/metadata/android" / lang / "images"
        screenshots = sorted((directory / "phoneScreenshots").glob("*.png"))
        if not 2 <= len(screenshots) <= 8:
            fail(f"{lang}: expected 2–8 owner-approved phone screenshots")
        shapes = [image_size(path) for path in screenshots]
        if any(w < 320 or h < 320 or max(w, h) / min(w, h) > 2.5 for w, h in shapes):
            fail(f"{lang}: invalid Play phone screenshot dimensions")
        if image_size(directory / "icon.png") != (512, 512):
            fail(f"{lang}: high-resolution icon must be 512x512")
        if image_size(directory / "featureGraphic.png") != (1024, 500):
            fail(f"{lang}: feature graphic must be 1024x500")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--privacy-url", default="")
    parser.add_argument("--source-url", default="")
    args = parser.parse_args()
    try:
        validate_listing()
        if args.release:
            validate_release_assets(args.privacy_url, args.source_url)
    except ValueError as exc:
        print(f"S.17 distribution gate: {exc}", file=sys.stderr)
        return 1
    if args.release:
        print("S.17 metadata and real store asset release gate passed.")
    else:
        print("S.17 development metadata passed. Store assets and URLs remain release-gated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
