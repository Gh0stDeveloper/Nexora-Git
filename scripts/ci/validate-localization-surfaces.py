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
    "app/src/main/java/com/nexora/git/feature/repositories/RepositoriesScreen.kt",
    "app/src/main/java/com/nexora/git/feature/git/GitWorkspaceScreen.kt",
    "app/src/main/java/com/nexora/git/feature/issues/IssuesScreen.kt",
    "app/src/main/java/com/nexora/git/feature/pulls/PullRequestsScreen.kt",
    "app/src/main/java/com/nexora/git/feature/actions/ActionsScreen.kt",
    "app/src/main/java/com/nexora/git/feature/releases/ReleasesScreen.kt",
    "app/src/main/java/com/nexora/git/feature/editor/MobileEditorScreen.kt",
    "app/src/main/java/com/nexora/git/feature/activity/ActivityScreen.kt",
    "app/src/main/java/com/nexora/git/feature/explore/ExploreScreen.kt",
    "app/src/main/java/com/nexora/git/feature/repositories/RepositoryDetailScreen.kt",
    "app/src/main/java/com/nexora/git/feature/issues/IssueDetailScreen.kt",
    "app/src/main/java/com/nexora/git/feature/pulls/PullRequestDetailScreen.kt",
    "app/src/main/java/com/nexora/git/feature/releases/ReleaseDetailScreen.kt",
    "app/src/main/java/com/nexora/git/feature/actions/WorkflowRunDetailScreen.kt",
    "app/src/main/java/com/nexora/git/feature/advanced/AdvancedGitHubScreen.kt",
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
