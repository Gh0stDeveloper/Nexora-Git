#!/usr/bin/env python3
from pathlib import Path
import sys

docs = Path("docs/PRIVACY.md").read_text(encoding="utf-8").lower()
web = Path("web/app/privacy/page.tsx").read_text(encoding="utf-8").lower()
settings = Path("app/src/main/java/com/nexora/git/feature/settings/SettingsViewModel.kt").read_text(encoding="utf-8")

checks = {
    "docs: Android Keystore": "android keystore" in docs,
    "web: Android Keystore": "android keystore" in web,
    "docs: Auth Broker": "auth broker" in docs,
    "web: Auth Broker": "auth broker" in web,
    "docs: advertising disclosure": "advertising" in docs,
    "web: advertising disclosure": "advertising" in web,
    "docs: analytics disclosure": "analytics" in docs or "telemetry" in docs,
    "web: analytics disclosure": "analytics" in web or "telemetry" in web,
    "docs: no data sale": "does not sell user data" in docs,
    "web: no data sale": "does not sell user data" in web,
    "docs: deletion": "## deletion" in docs,
    "web: deletion": ">deletion<" in web,
    "Android Settings derives /privacy from production origin": '.plus("/privacy")' in settings,
}

failed = [name for name, ok in checks.items() if not ok]
if failed:
    print("Privacy synchronization validation failed:", file=sys.stderr)
    for name in failed:
        print(f" - {name}", file=sys.stderr)
    sys.exit(1)

print("Privacy policy synchronization guard passed.")
