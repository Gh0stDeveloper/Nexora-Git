#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! declare -F log_info >/dev/null 2>&1; then
  # shellcheck source=lib.sh
  source "$SCRIPT_DIR/lib.sh"
fi

NEXORA_ANDROID_CONFIG_FILE="${NEXORA_ANDROID_CONFIG_FILE:-/etc/nexora-git/android-builder.conf}"
NEXORA_ANDROID_STATE_ROOT="${NEXORA_ANDROID_STATE_ROOT:-/var/lib/nexora-git/android}"
NEXORA_ANDROID_SDK_ROOT="${NEXORA_ANDROID_SDK_ROOT:-/opt/nexora-android-sdk}"
NEXORA_ANDROID_GRADLE_HOME="${NEXORA_ANDROID_GRADLE_HOME:-/var/cache/nexora-git/gradle}"
NEXORA_ANDROID_BUILD_USER="${NEXORA_ANDROID_BUILD_USER:-nexora-build}"
NEXORA_ANDROID_JAVA_HOME="${NEXORA_ANDROID_JAVA_HOME:-}"

NEXORA_ANDROID_PLATFORM="${NEXORA_ANDROID_PLATFORM:-android-37.0}"
NEXORA_ANDROID_BUILD_TOOLS="${NEXORA_ANDROID_BUILD_TOOLS:-37.0.0}"
NEXORA_ANDROID_NDK="${NEXORA_ANDROID_NDK:-27.2.12479018}"
NEXORA_ANDROID_CMAKE="${NEXORA_ANDROID_CMAKE:-3.22.1}"

NEXORA_ANDROID_CMDLINE_TOOLS_REV="${NEXORA_ANDROID_CMDLINE_TOOLS_REV:-15859902}"
NEXORA_ANDROID_CMDLINE_TOOLS_SHA256="${NEXORA_ANDROID_CMDLINE_TOOLS_SHA256:-4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583}"
NEXORA_ANDROID_CMDLINE_TOOLS_URL="${NEXORA_ANDROID_CMDLINE_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-${NEXORA_ANDROID_CMDLINE_TOOLS_REV}_latest.zip}"

load_android_config() {
  if [[ -r "$NEXORA_ANDROID_CONFIG_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$NEXORA_ANDROID_CONFIG_FILE"
  fi
}

android_required_packages() {
  printf '%s\n' \
    "platform-tools" \
    "platforms;${NEXORA_ANDROID_PLATFORM}" \
    "build-tools;${NEXORA_ANDROID_BUILD_TOOLS}" \
    "ndk;${NEXORA_ANDROID_NDK}" \
    "cmake;${NEXORA_ANDROID_CMAKE}"
}

discover_java17_home() {
  local candidate
  for candidate in \
    /usr/lib/jvm/java-17-openjdk-* \
    /usr/lib/jvm/temurin-17-jdk-* \
    /usr/lib/jvm/jdk-17*; do
    if [[ -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

android_java_major() {
  local java_bin="${1:-java}"
  local spec
  spec="$("$java_bin" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2; exit}')"
  [[ -n "$spec" ]] || return 1
  if [[ "$spec" == 1.* ]]; then
    printf '%s\n' "${spec#1.}" | cut -d. -f1
  else
    printf '%s\n' "$spec" | cut -d. -f1
  fi
}

android_sdkmanager() {
  printf '%s\n' "$NEXORA_ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
}

android_component_path() {
  case "$1" in
    platform-tools) printf '%s\n' "$NEXORA_ANDROID_SDK_ROOT/platform-tools/adb" ;;
    platform) printf '%s\n' "$NEXORA_ANDROID_SDK_ROOT/platforms/$NEXORA_ANDROID_PLATFORM/android.jar" ;;
    build-tools) printf '%s\n' "$NEXORA_ANDROID_SDK_ROOT/build-tools/$NEXORA_ANDROID_BUILD_TOOLS/apksigner" ;;
    ndk) printf '%s\n' "$NEXORA_ANDROID_SDK_ROOT/ndk/$NEXORA_ANDROID_NDK" ;;
    cmake) printf '%s\n' "$NEXORA_ANDROID_SDK_ROOT/cmake/$NEXORA_ANDROID_CMAKE/bin/cmake" ;;
    *) return 1 ;;
  esac
}

ensure_android_builder_user() {
  if id "$NEXORA_ANDROID_BUILD_USER" >/dev/null 2>&1; then
    log_ok "Android builder user already exists: $NEXORA_ANDROID_BUILD_USER"
    return
  fi

  useradd \
    --system \
    --user-group \
    --home-dir "$NEXORA_ANDROID_STATE_ROOT/worker" \
    --create-home \
    --shell /usr/sbin/nologin \
    "$NEXORA_ANDROID_BUILD_USER"
  log_ok "Created isolated Android builder user: $NEXORA_ANDROID_BUILD_USER"
}

ensure_android_layout() {
  local group
  group="$(id -gn "$NEXORA_ANDROID_BUILD_USER")"

  install -d -o root -g root -m 0755 "$(dirname "$NEXORA_ANDROID_STATE_ROOT")"
  install -d -o root -g "$group" -m 0750 "$NEXORA_ANDROID_STATE_ROOT"
  install -d -o "$NEXORA_ANDROID_BUILD_USER" -g "$group" -m 0750 \
    "$NEXORA_ANDROID_STATE_ROOT/worker" \
    "$NEXORA_ANDROID_STATE_ROOT/builds" \
    "$NEXORA_ANDROID_STATE_ROOT/artifacts" \
    "$NEXORA_ANDROID_STATE_ROOT/logs" \
    "$NEXORA_ANDROID_STATE_ROOT/tmp"

  install -d -o root -g root -m 0755 "$NEXORA_ANDROID_SDK_ROOT"
  install -d -o "$NEXORA_ANDROID_BUILD_USER" -g "$group" -m 0750 "$NEXORA_ANDROID_GRADLE_HOME"
}

install_android_cmdline_tools() {
  local sdkmanager
  sdkmanager="$(android_sdkmanager)"
  if [[ -x "$sdkmanager" ]]; then
    log_ok "Android command-line tools already installed."
    return
  fi

  local temp_dir archive extracted version_dir
  temp_dir="$(mktemp -d)"
  archive="$temp_dir/commandlinetools.zip"
  extracted="$temp_dir/extracted"
  version_dir="$NEXORA_ANDROID_SDK_ROOT/cmdline-tools/$NEXORA_ANDROID_CMDLINE_TOOLS_REV"
  trap 'rm -rf "$temp_dir"' RETURN

  log_info "Downloading pinned Android command-line tools revision $NEXORA_ANDROID_CMDLINE_TOOLS_REV."
  curl --fail --location --retry 3 --retry-delay 2 \
    "$NEXORA_ANDROID_CMDLINE_TOOLS_URL" \
    --output "$archive"

  printf '%s  %s\n' "$NEXORA_ANDROID_CMDLINE_TOOLS_SHA256" "$archive" | sha256sum --check --status ||
    die "Android command-line tools checksum verification failed."

  mkdir -p "$extracted"
  unzip -q "$archive" -d "$extracted"
  [[ -x "$extracted/cmdline-tools/bin/sdkmanager" ]] ||
    die "Downloaded Android command-line tools archive has an unexpected layout."

  mkdir -p "$NEXORA_ANDROID_SDK_ROOT/cmdline-tools"
  if [[ ! -d "$version_dir" ]]; then
    mv "$extracted/cmdline-tools" "$version_dir"
  fi
  rm -rf "$NEXORA_ANDROID_SDK_ROOT/cmdline-tools/latest"
  ln -s "$NEXORA_ANDROID_CMDLINE_TOOLS_REV" "$NEXORA_ANDROID_SDK_ROOT/cmdline-tools/latest"

  [[ -x "$(android_sdkmanager)" ]] || die "sdkmanager was not installed correctly."
  log_ok "Android command-line tools installed and checksum verified."
}

ensure_android_sdk_packages() {
  local sdkmanager
  sdkmanager="$(android_sdkmanager)"
  [[ -x "$sdkmanager" ]] || die "sdkmanager is unavailable."

  local packages=()
  mapfile -t packages < <(android_required_packages)

  log_info "Accepting Android SDK licenses for this build host."
  yes 2>/dev/null | \
    JAVA_HOME="$NEXORA_ANDROID_JAVA_HOME" \
    ANDROID_SDK_ROOT="$NEXORA_ANDROID_SDK_ROOT" \
    "$sdkmanager" --sdk_root="$NEXORA_ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

  log_info "Ensuring pinned Android SDK/NDK/CMake packages are installed."
  JAVA_HOME="$NEXORA_ANDROID_JAVA_HOME" \
  ANDROID_SDK_ROOT="$NEXORA_ANDROID_SDK_ROOT" \
    "$sdkmanager" \
      --sdk_root="$NEXORA_ANDROID_SDK_ROOT" \
      --channel=3 \
      "${packages[@]}"
}

write_android_config() {
  local dir temp
  dir="$(dirname "$NEXORA_ANDROID_CONFIG_FILE")"
  install -d -m 0755 "$dir"
  temp="$(mktemp "$dir/.android-builder.conf.XXXXXX")"

  {
    printf 'NEXORA_ANDROID_STATE_ROOT=%q\n' "$NEXORA_ANDROID_STATE_ROOT"
    printf 'NEXORA_ANDROID_SDK_ROOT=%q\n' "$NEXORA_ANDROID_SDK_ROOT"
    printf 'NEXORA_ANDROID_GRADLE_HOME=%q\n' "$NEXORA_ANDROID_GRADLE_HOME"
    printf 'NEXORA_ANDROID_BUILD_USER=%q\n' "$NEXORA_ANDROID_BUILD_USER"
    printf 'NEXORA_ANDROID_JAVA_HOME=%q\n' "$NEXORA_ANDROID_JAVA_HOME"
    printf 'NEXORA_ANDROID_PLATFORM=%q\n' "$NEXORA_ANDROID_PLATFORM"
    printf 'NEXORA_ANDROID_BUILD_TOOLS=%q\n' "$NEXORA_ANDROID_BUILD_TOOLS"
    printf 'NEXORA_ANDROID_NDK=%q\n' "$NEXORA_ANDROID_NDK"
    printf 'NEXORA_ANDROID_CMAKE=%q\n' "$NEXORA_ANDROID_CMAKE"
    printf 'NEXORA_ANDROID_CMDLINE_TOOLS_REV=%q\n' "$NEXORA_ANDROID_CMDLINE_TOOLS_REV"
    printf 'NEXORA_ANDROID_CMDLINE_TOOLS_SHA256=%q\n' "$NEXORA_ANDROID_CMDLINE_TOOLS_SHA256"
    printf 'NEXORA_ANDROID_CMDLINE_TOOLS_URL=%q\n' "$NEXORA_ANDROID_CMDLINE_TOOLS_URL"
  } > "$temp"

  chmod 0644 "$temp"
  mv -f "$temp" "$NEXORA_ANDROID_CONFIG_FILE"
}

android_check_file() {
  local label="$1"
  local path="$2"
  if [[ -e "$path" ]]; then
    log_ok "$label: $path"
    return 0
  fi
  log_error "$label missing: $path"
  return 1
}

android_doctor() {
  load_android_config
  local failed=0

  if id "$NEXORA_ANDROID_BUILD_USER" >/dev/null 2>&1; then
    log_ok "Builder user available: $NEXORA_ANDROID_BUILD_USER"
  else
    log_error "Builder user missing: $NEXORA_ANDROID_BUILD_USER"
    failed=1
  fi

  if [[ -x "$NEXORA_ANDROID_JAVA_HOME/bin/java" ]]; then
    local major
    major="$(android_java_major "$NEXORA_ANDROID_JAVA_HOME/bin/java" || true)"
    if [[ "$major" == "17" ]]; then
      log_ok "JDK 17 available: $NEXORA_ANDROID_JAVA_HOME"
    else
      log_error "Configured Java is not JDK 17: $NEXORA_ANDROID_JAVA_HOME (major=${major:-unknown})"
      failed=1
    fi
  else
    log_error "Configured JDK is missing: $NEXORA_ANDROID_JAVA_HOME"
    failed=1
  fi

  android_check_file "sdkmanager" "$(android_sdkmanager)" || failed=1
  android_check_file "platform-tools" "$(android_component_path platform-tools)" || failed=1
  android_check_file "Android platform" "$(android_component_path platform)" || failed=1
  android_check_file "Build Tools / apksigner" "$(android_component_path build-tools)" || failed=1
  android_check_file "NDK" "$(android_component_path ndk)" || failed=1
  android_check_file "CMake" "$(android_component_path cmake)" || failed=1

  if [[ -x "${INSTALL_DIR:-$NEXORA_DEFAULT_INSTALL_DIR}/gradlew" ]]; then
    log_ok "Gradle wrapper available in managed repository"
  else
    log_error "Gradle wrapper missing or not executable in managed repository"
    failed=1
  fi

  for path in \
    "$NEXORA_ANDROID_STATE_ROOT/worker" \
    "$NEXORA_ANDROID_STATE_ROOT/builds" \
    "$NEXORA_ANDROID_STATE_ROOT/artifacts" \
    "$NEXORA_ANDROID_STATE_ROOT/logs" \
    "$NEXORA_ANDROID_GRADLE_HOME"; do
    if [[ -d "$path" ]]; then
      log_ok "Persistent path available: $path"
    else
      log_error "Persistent path missing: $path"
      failed=1
    fi
  done

  return "$failed"
}

android_status() {
  load_android_config

  printf 'Builder user:       %s\n' "$NEXORA_ANDROID_BUILD_USER"
  printf 'State root:         %s\n' "$NEXORA_ANDROID_STATE_ROOT"
  printf 'Android SDK:        %s\n' "$NEXORA_ANDROID_SDK_ROOT"
  printf 'Gradle home:        %s\n' "$NEXORA_ANDROID_GRADLE_HOME"
  printf 'Java home:          %s\n' "${NEXORA_ANDROID_JAVA_HOME:-not configured}"
  printf 'Platform:           %s\n' "$NEXORA_ANDROID_PLATFORM"
  printf 'Build Tools:        %s\n' "$NEXORA_ANDROID_BUILD_TOOLS"
  printf 'NDK:                %s\n' "$NEXORA_ANDROID_NDK"
  printf 'CMake:              %s\n' "$NEXORA_ANDROID_CMAKE"
  printf 'Command-line tools: %s\n' "$NEXORA_ANDROID_CMDLINE_TOOLS_REV"

  local component path
  for component in platform-tools platform build-tools ndk cmake; do
    path="$(android_component_path "$component")"
    if [[ -e "$path" ]]; then
      printf '%-19s %s\n' "${component}:" "installed"
    else
      printf '%-19s %s\n' "${component}:" "missing"
    fi
  done
}

android_config() {
  load_android_config
  cat <<EOF
NEXORA_ANDROID_STATE_ROOT=$NEXORA_ANDROID_STATE_ROOT
NEXORA_ANDROID_SDK_ROOT=$NEXORA_ANDROID_SDK_ROOT
NEXORA_ANDROID_GRADLE_HOME=$NEXORA_ANDROID_GRADLE_HOME
NEXORA_ANDROID_BUILD_USER=$NEXORA_ANDROID_BUILD_USER
NEXORA_ANDROID_JAVA_HOME=$NEXORA_ANDROID_JAVA_HOME
NEXORA_ANDROID_PLATFORM=$NEXORA_ANDROID_PLATFORM
NEXORA_ANDROID_BUILD_TOOLS=$NEXORA_ANDROID_BUILD_TOOLS
NEXORA_ANDROID_NDK=$NEXORA_ANDROID_NDK
NEXORA_ANDROID_CMAKE=$NEXORA_ANDROID_CMAKE
NEXORA_ANDROID_CMDLINE_TOOLS_REV=$NEXORA_ANDROID_CMDLINE_TOOLS_REV
EOF
}

ensure_android_foundation() {
  require_root
  detect_supported_os

  apt_install_missing ca-certificates curl unzip openjdk-17-jdk-headless
  ensure_android_builder_user
  ensure_android_layout

  NEXORA_ANDROID_JAVA_HOME="$(discover_java17_home)" ||
    die "JDK 17 was installed but its JAVA_HOME could not be resolved."

  export JAVA_HOME="$NEXORA_ANDROID_JAVA_HOME"
  export ANDROID_SDK_ROOT="$NEXORA_ANDROID_SDK_ROOT"

  install_android_cmdline_tools
  ensure_android_sdk_packages
  write_android_config

  android_doctor || die "Android build foundation verification failed."
  log_ok "Android build foundation is ready and reusable."
}
