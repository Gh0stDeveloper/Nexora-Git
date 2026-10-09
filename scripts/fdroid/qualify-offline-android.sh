#!/usr/bin/env bash
set -Eeuo pipefail

# S.17.3 experimental qualification, NOT an F-Droid release.
# Usage: NEXORA_NATIVE_SOURCE_ROOT=/absolute/pinned \
#   GRADLE_USER_HOME=/absolute/gradle-cache bash scripts/fdroid/qualify-offline-android.sh warm|offline
#
# warm: allowed to access Maven and the Android SDK; fills isolated Gradle cache.
# offline: drops all network interfaces, deletes compiled outputs, then recompiles
#          unsigned Android release APK/AAB using --offline and pre-pinned sources.
#
# This proof does NOT replace two independent fdroidserver builds.

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

phase="${1:-}"
[[ "$phase" == warm || "$phase" == offline ]] || {
  printf 'Usage: qualify-offline-android.sh warm|offline\n' >&2
  exit 2
}

: "${NEXORA_NATIVE_SOURCE_ROOT:?Required external native source root}"
: "${GRADLE_USER_HOME:?Required explicit isolated Gradle cache}"
: "${ANDROID_SDK_ROOT:?Required Android SDK root}"

[[ "$NEXORA_NATIVE_SOURCE_ROOT" == /* && "$GRADLE_USER_HOME" == /* && "$ANDROID_SDK_ROOT" == /* ]] || {
  printf 'All SDK, native and Gradle cache paths must be absolute.\n' >&2
  exit 2
}

# Fail closed on invalid source checkouts before either stage.
python3 scripts/fdroid/prepare-native-sources.py --verify-only \
  --source-root "$NEXORA_NATIVE_SOURCE_ROOT"

lock_sha="$(sha256sum native/git/fdroid-sources.lock.json | awk '{print $1}')"
patch_sha="$(sha256sum native/git/cmake/libgit2-no-install.patch | awk '{print $1}')"
catalog_sha="$(sha256sum gradle/libs.versions.toml | awk '{print $1}')"
wrapper_sha="$(sha256sum gradle/wrapper/gradle-wrapper.properties | awk '{print $1}')"
source_commit="$(git rev-parse HEAD)"

if [[ "$phase" == warm ]]; then
  mkdir -p "$GRADLE_USER_HOME"
  chmod 0700 "$GRADLE_USER_HOME"
  # Warm both artifact graphs. Merely resolving runtimeClasspath is NOT enough
  # for KSP, lint, AGP plugins, native tooling and packaging task dependencies.
  ./gradlew --no-daemon --no-build-cache \
    -Pnexora.nativeSourceRoot="$NEXORA_NATIVE_SOURCE_ROOT" \
    :app:assembleRelease :app:bundleRelease
  cat > "$GRADLE_USER_HOME/nexora-fdroid-preflight.txt" <<EOF
${source_commit}
${lock_sha}
${patch_sha}
${catalog_sha}
${wrapper_sha}
EOF
  printf 'Networked dependency cache warm-up complete; no F-Droid qualification claimed.\n'
  exit 0
fi

# The warm-up and offline build must be from the same checked-out source,
# pinned native lock/patch and Gradle binary/plugin/dependency declarations.
expected="${source_commit}
${lock_sha}
${patch_sha}
${catalog_sha}
${wrapper_sha}"
[[ -s "$GRADLE_USER_HOME/nexora-fdroid-preflight.txt" ]] || {
  printf 'Missing matching networked warm-up marker.\n' >&2
  exit 1
}
[[ "$(cat "$GRADLE_USER_HOME/nexora-fdroid-preflight.txt")" == "$expected" ]] || {
  printf 'Warm-up source or dependency inputs changed: preflight mismatch.\n' >&2
  exit 1
}

command -v sudo >/dev/null && command -v runuser >/dev/null && command -v ip >/dev/null || {
  printf 'sudo, runuser and iproute2 are required for verified network namespace isolation.\n' >&2
  exit 1
}
sudo unshare --net -- true || {
  printf 'A network namespace is unavailable; refusing to call the build offline.\n' >&2
  exit 1
}

# Fail instead of "passing" thanks to existing compiled APKs/.so files or a
# project-local build cache. Keep only the explicitly prewarmed Maven/Gradle
# dependency store and Android SDK outside the build tree.
rm -rf app/.cxx app/build baselineprofile/build build .gradle

# unshare runs as root solely to create a network namespace. Build immediately
# drops privileges to the ordinary checkout owner to avoid root-owned caches,
# git unsafe-directory errors and unexpected permissions.
# Gradle's FileLockContentionHandler requires a usable local IP address.
# An entirely DOWN loopback device causes "Could not determine a usable
# wildcard IP". Bring up only lo INSIDE the isolated namespace; no external
# interface or default route is allowed. The namespace remains offline.
sudo unshare --net -- bash -euo pipefail -c '
  ip link set dev lo up
  [[ "$(ip -o link show | wc -l)" -eq 1 ]] || {
    echo "Unexpected network interface inside isolated namespace" >&2
    exit 1
  }
  [[ -z "$(ip -4 route show table main)" && -z "$(ip -6 route show table main)" ]] || {
    echo "Unexpected external route inside isolated namespace" >&2
    exit 1
  }
  exec "$@"
' _ runuser -u "$(id -un)" -- env \
  HOME="$HOME" \
  PATH="$PATH" \
  JAVA_HOME="${JAVA_HOME:-}" \
  GRADLE_USER_HOME="$GRADLE_USER_HOME" \
  ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" \
  ANDROID_HOME="$ANDROID_SDK_ROOT" \
  NEXORA_NATIVE_SOURCE_ROOT="$NEXORA_NATIVE_SOURCE_ROOT" \
  NEXORA_GITHUB_CLIENT_ID="${NEXORA_GITHUB_CLIENT_ID:-}" \
  NEXORA_AUTH_BROKER_BASE_URL="${NEXORA_AUTH_BROKER_BASE_URL:-}" \
  NEXORA_GITHUB_CALLBACK_URL="${NEXORA_GITHUB_CALLBACK_URL:-}" \
  bash -euo pipefail -c '
    python3 scripts/fdroid/prepare-native-sources.py --verify-only \
      --source-root "$NEXORA_NATIVE_SOURCE_ROOT"
    ./gradlew --offline --no-daemon --no-build-cache --rerun-tasks \
      -Pnexora.nativeSourceRoot="$NEXORA_NATIVE_SOURCE_ROOT" \
      :app:assembleRelease :app:bundleRelease
  '

apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*-unsigned.apk' -print -quit)"
aab="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -print -quit)"
[[ -s "$apk" && -s "$aab" ]] || {
  printf 'Network-isolated build did not produce both unsigned APK and AAB.\n' >&2
  exit 1
}
printf 'Offline unsigned Android release artifacts built from source (APK and AAB).\n'
