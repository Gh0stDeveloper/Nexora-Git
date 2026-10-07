#!/usr/bin/env bash
set -euo pipefail

test -s README.md
test -s LICENSE
test -s NOTICE
test -s CONTRIBUTING.md
test -s CODE_OF_CONDUCT.md
test -s SECURITY.md
test -s docs/PROJECT_SPEC.md
test -s docs/ARCHITECTURE.md
test -s docs/AUTH.md
test -s docs/GIT_ENGINE.md
test -s docs/STORAGE.md
test -s docs/SECURITY.md
test -s docs/THREAT_MODEL.md
test -s docs/UI_UX.md
test -s docs/PRODUCTION_READINESS.md
test -s docs/VPS_ANDROID_BUILDER.md
test -s docs/PRODUCTION_SECURITY_AUDIT.md
test -s docs/PERFORMANCE_AUDIT.md
test -s docs/ACCESSIBILITY_AUDIT.md
test -s docs/RELEASE_PROCESS.md
test -s docs/PRIVACY.md
test -s docs/PLAY_STORE_READINESS.md
test -s docs/FDROID_READINESS.md
test -s docs/PHASE_R_REPORT.md
test -s docs/ROADMAP.md
test -s docs/BRANDING.md
test -s docs/GITHUB_APP.md
test -s docs/adr/0001-native-android-kotlin-compose.md
test -s docs/adr/0002-github-app-oauth-pkce.md
test -s docs/adr/0003-libgit2-real-git-engine.md

if grep -RInE --exclude-dir=.git --exclude='*.md' '(gh[pousr]_[A-Za-z0-9_]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)' .; then
  echo 'Potential committed secret detected.'
  exit 1
fi

bash -n scripts/vps/*.sh scripts/vps/nexora-git
bash scripts/vps/test-lib.sh
bash scripts/vps/test-android-build-lib.sh
bash scripts/vps/test-android-signing-lib.sh
bash scripts/vps/test-android-worker-lib.sh
bash scripts/vps/test-android-artifacts-lib.sh
bash scripts/vps/test-android-github-lib.sh
bash scripts/vps/test-android-ops-lib.sh

if [[ -f gradlew ]]; then
  chmod +x gradlew
  ./gradlew help --stacktrace
else
  echo 'Gradle project not present yet; Android checks activate in Phase A.'
fi

echo 'Foundation validation passed.'
