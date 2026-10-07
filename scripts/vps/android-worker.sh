#!/usr/bin/env bash

set -Eeuo pipefail

SELF="$(readlink -f "${BASH_SOURCE[0]}")"
SCRIPT_DIR="$(cd -- "$(dirname -- "$SELF")" && pwd)"

# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"
# shellcheck source=android-build-lib.sh
source "$SCRIPT_DIR/android-build-lib.sh"
# shellcheck source=android-worker-lib.sh
source "$SCRIPT_DIR/android-worker-lib.sh"

android_worker_run_forever
