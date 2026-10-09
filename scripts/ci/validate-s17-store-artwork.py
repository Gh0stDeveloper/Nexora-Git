#!/usr/bin/env python3
"""Verify owner-review candidate Play PNGs without treating them as screenshots."""

import hashlib
from pathlib import Path
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[2]
LOCALES = ("en-US", "es-MX")


def check_png(path: Path, expected: tuple[int, int]) -> str:
    if not path.is_file():
        raise ValueError(f"Store image missing: {path}")
    raw = path.read_bytes()
    if len(raw) < 8_000 or raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"PNG missing signature or too small: {path}")
    if raw[12:16] != b"IHDR" or struct.unpack_from(">II", raw, 16) != expected:
        raise ValueError(f"Wrong image dimensions: {path}")
    index = 8
    saw_idat = saw_iend = False
    while index < len(raw):
        if index + 12 > len(raw):
            raise ValueError(f"Truncated PNG: {path}")
        size = struct.unpack_from(">I", raw, index)[0]
        chunk_type = raw[index + 4:index + 8]
        end = index + 12 + size
        if end > len(raw):
            raise ValueError(f"Bad PNG chunk size: {path}")
        data = raw[index + 8:index + 8 + size]
        crc = struct.unpack_from(">I", raw, index + 8 + size)[0]
        if (zlib.crc32(chunk_type + data) & 0xFFFFFFFF) != crc:
            raise ValueError(f"PNG CRC mismatch: {path}")
        if chunk_type == b"IDAT":
            saw_idat = True
        if chunk_type == b"IEND":
            saw_iend = True
            if end != len(raw):
                raise ValueError(f"Trailing PNG data: {path}")
            break
        index = end
    if not saw_idat or not saw_iend:
        raise ValueError(f"Incomplete PNG: {path}")
    return hashlib.sha256(raw).hexdigest()


def main() -> int:
    try:
        icons = []
        banners = []
        for locale in LOCALES:
            folder = ROOT / "fastlane/metadata/android" / locale / "images"
            icons.append(check_png(folder / "icon.png", (512, 512)))
            banners.append(check_png(folder / "featureGraphic.png", (1024, 500)))
        if icons[0] != icons[1]:
            raise ValueError("Localized icon artwork must have identical brand identity")
        if banners[0] == banners[1]:
            raise ValueError("English and Spanish feature artwork must have distinct localization")
    except ValueError as error:
        print(f"S.17 store artwork validation failed: {error}", file=sys.stderr)
        return 1
    print("S.17 Play Store art assets validated: 2 icons and 2 localized feature graphics")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
