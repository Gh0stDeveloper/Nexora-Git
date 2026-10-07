#!/usr/bin/env python3
from pathlib import Path
import re
import sys

FILES = [
    "app/src/main/java/com/nexora/git/ui/NexoraGitApp.kt",
    "app/src/main/java/com/nexora/git/ui/navigation/NexoraDestination.kt",
    "app/src/main/java/com/nexora/git/feature/auth/LoginScreen.kt",
    "app/src/main/java/com/nexora/git/feature/home/HomeScreen.kt",
    "app/src/main/java/com/nexora/git/feature/settings/SettingsScreen.kt",
    "app/src/main/java/com/nexora/git/feature/onboarding/OnboardingScreen.kt",
]

PATTERNS = [
    re.compile(r'Text\(\s*(?:text\s*=\s*)?"[A-Za-z][^"]*"'),
    re.compile(r'contentDescription\s*=\s*"[A-Za-z][^"]*"'),
    re.compile(r'label\s*=\s*\{\s*Text\("[A-Za-z][^"]*"'),
    re.compile(r'placeholder\s*=\s*\{\s*Text\("[A-Za-z][^"]*"'),
]

violations = []
for relative in FILES:
    path = Path(relative)
    text = path.read_text(encoding="utf-8")
    for number, line in enumerate(text.splitlines(), start=1):
        if any(pattern.search(line) for pattern in PATTERNS):
            violations.append(f"{relative}:{number}: {line.strip()}")

if violations:
    print("Hardcoded user-facing text found in migrated localization surfaces:", file=sys.stderr)
    for violation in violations:
        print(violation, file=sys.stderr)
    sys.exit(1)

print(f"Localization guard passed for {len(FILES)} migrated surfaces.")
