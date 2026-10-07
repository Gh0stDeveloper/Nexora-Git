#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export NEXORA_ANDROID_CONFIG_FILE="$TEMP_ROOT/etc/android-builder.conf"
export NEXORA_ANDROID_STATE_ROOT="$TEMP_ROOT/state"
export NEXORA_ANDROID_SDK_ROOT="$TEMP_ROOT/sdk"
export NEXORA_ANDROID_GRADLE_HOME="$TEMP_ROOT/gradle"
export NEXORA_ANDROID_BUILD_USER="nexora-build-test"

# shellcheck source=android-build-lib.sh
source "$SCRIPT_DIR/android-build-lib.sh"

[[ "$NEXORA_ANDROID_PLATFORM" == "android-37.0" ]]
[[ "$NEXORA_ANDROID_BUILD_TOOLS" == "37.0.0" ]]
[[ "$NEXORA_ANDROID_NDK" == "27.2.12479018" ]]
[[ "$NEXORA_ANDROID_CMAKE" == "3.22.1" ]]
[[ "$NEXORA_ANDROID_CMDLINE_TOOLS_REV" == "15859902" ]]
[[ "$NEXORA_ANDROID_CMDLINE_TOOLS_SHA256" =~ ^[a-f0-9]{64}$ ]]

mapfile -t packages < <(android_required_packages)
[[ "${#packages[@]}" -eq 5 ]]
[[ "${packages[0]}" == "platform-tools" ]]
[[ "${packages[1]}" == "platforms;android-37.0" ]]
[[ "${packages[2]}" == "build-tools;37.0.0" ]]
[[ "${packages[3]}" == "ndk;27.2.12479018" ]]
[[ "${packages[4]}" == "cmake;3.22.1" ]]

[[ "$(android_sdkmanager)" == "$TEMP_ROOT/sdk/cmdline-tools/latest/bin/sdkmanager" ]]
[[ "$(android_component_path build-tools)" == "$TEMP_ROOT/sdk/build-tools/37.0.0/apksigner" ]]
[[ "$(android_component_path ndk)" == "$TEMP_ROOT/sdk/ndk/27.2.12479018" ]]

NEXORA_ANDROID_JAVA_HOME="/usr/lib/jvm/example-java-17"
write_android_config
[[ -s "$NEXORA_ANDROID_CONFIG_FILE" ]]

NEXORA_ANDROID_JAVA_HOME=""
load_android_config
[[ "$NEXORA_ANDROID_JAVA_HOME" == "/usr/lib/jvm/example-java-17" ]]

printf 'Android build helper tests passed.\n'
