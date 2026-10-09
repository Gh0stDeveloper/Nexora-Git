# F-Droid Readiness — S.17.3

**Status: PARTIAL. F-Droid metadata build stays DISABLED.** Nexora Git is open source but an open repository alone is not a reproducible F-Droid build. Do not submit or enable the metadata entry until the packaging system independently builds the signed, repeatable APK without fetching source during Gradle/CMake execution.

## Prepared foundations

- Apache-2.0 project licensing, versioned Android application ID and metadata skeleton: `metadata/com.nexora.git.yml`.
- GitHub-dependent service features disclosed via **NonFreeNet**; keep this declaration in the metadata.
- Native libgit2, Mbed TLS, Tree-sitter and grammars are pinned to exact commits in `native/git/CMakeLists.txt`.
- New optional native source injection through **`-DNEXORA_NATIVE_SOURCE_ROOT=/path/to/pinned-checkouts`**.
- In offline mode, CMake verifies nine source folders and their Git HEAD against the exact pinned revisions, requires the checked-in libgit2 no-install patch to be preapplied, and sets `FETCHCONTENT_FULLY_DISCONNECTED=ON`.
- Default developer/Android CI builds continue using their existing online pinned FetchContent process.

### Required offline checkout layout

```text
/path/to/pinned-checkouts/
  mbedtls/
  libgit2/                   # apply native/git/cmake/libgit2-no-install.patch
  tree_sitter/
  nexora_ts_kotlin_source/
  nexora_ts_java_source/
  nexora_ts_json_source/
  nexora_ts_python_source/
  nexora_ts_javascript_source/
  nexora_ts_typescript_source/
```

All directories must be real, revision-pinned Git checkouts (including `.git`, or a valid worktree link). CMake rejects missing/mismatched source revisions and **never falls back to downloading** in this mode.

Example, after independent source checkout and audit:

```bash
cmake -S native/git -B build/offline-native \
  -DNEXORA_NATIVE_SOURCE_ROOT=/path/to/pinned-checkouts \
  -DNEXORA_BUILD_NATIVE_TESTS=ON
cmake --build build/offline-native
ctest --test-dir build/offline-native --output-on-failure
```

Android Gradle can now forward this source directory using `-Pnexora.nativeSourceRoot=/absolute/path` or the `NEXORA_NATIVE_SOURCE_ROOT` environment variable. Only **absolute paths** are accepted. The source checkouts must be prepared **before** Gradle configuration.

```bash
./gradlew --offline :app:assembleRelease \
  -Pnexora.nativeSourceRoot=/path/to/pinned-checkouts
```

This command is illustrative: full Gradle/Maven offline dependency acquisition, controlled signing and end-to-end fdroidserver reproducibility are **not yet certified**.

## Qualified native offline build — 2026-10-08

The source-level preparation has advanced beyond fail-closed negative checks:

- `native/git/fdroid-sources.lock.json` identifies all **nine pinned native Git checkouts**. Mbed TLS additionally includes one pinned `framework` Git submodule; the Git superproject's gitlink SHA is independently checked.
- `scripts/fdroid/prepare-native-sources.py --prepare` downloads sources **before** entering the isolated build environment, validates each Git SHA/origin, pre-applies the repository's libgit2 patch and emits a public source-provenance JSON.
- `--verify-only` performs verification without any source downloads and explicitly rejects absent or mismatched dependencies.
- `F-Droid Offline Native Qualification` runs host CMake, Ninja and CTest inside `sudo unshare --net`, ensuring the compilation has **no network interfaces**. It fails if source acquisition, isolation, compilation or tests fail.
- **Verified success:** [GitHub Actions run #37806822320](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37806822320), source commit `3bf603aa482684b7edd0fff089f13ffa3333ccf4`. The workflow published `fdroid-native-revision-provenance` as an artifact.

```bash
# Stage 1: networked prefetch on a separate, trusted host or acquisition step.
python3 scripts/fdroid/prepare-native-sources.py --prepare \
  --source-root /absolute/native-source-cache \
  --report /absolute/source-provenance.json

# Stage 2: no network calls; must match the pinned source bundle exactly.
python3 scripts/fdroid/prepare-native-sources.py --verify-only \
  --source-root /absolute/native-source-cache

# Stage 3: CMake/CTest in network-isolated execution (validated in CI).
sudo unshare --net -- bash -euo pipefail -c '
  cmake -S native/git -B build/fdroid-offline -G Ninja \
    -DNEXORA_NATIVE_SOURCE_ROOT=/absolute/native-source-cache \
    -DNEXORA_BUILD_NATIVE_TESTS=ON \
    -DFETCHCONTENT_FULLY_DISCONNECTED=ON
  cmake --build build/fdroid-offline --parallel 2
  ctest --test-dir build/fdroid-offline --output-on-failure
'
```

**Boundary:** This establishes only host-native build isolation and pinned-source acquisition. It does **not** establish an Android APK compiled with Gradle fully offline, reproducible binaries from two isolated builds, or acceptance by the F-Droid repository. The metadata remains **disabled**.

## Experimental Android Gradle offline qualification — S.17.3 follow-up

A separate [F-Droid Offline Android Qualification (Experimental)](../.github/workflows/fdroid-offline-android-ci.yml) workflow now exercises the previously missing Android/Gradle side of the dependency boundary. It is an **experimental acceptance test, not proof of F-Droid eligibility**. The outcome must be read from its actual GitHub Actions run; source code and workflow definitions alone are not evidence of success.

1. A connected acquisition stage obtains the same nine pinned native source trees and Mbed TLS submodule with `prepare-native-sources.py --prepare`, then builds both unsigned APK and AAB to prewarm Gradle plugin, KSP, Android and Maven dependencies. The warm-up binds to the current Git commit, native source lock, libgit2 patch, Gradle version catalog and wrapper properties.
2. Before offline rebuild, `qualify-offline-android.sh offline` rejects missing or outdated preflight state, verifies the actual Git checkouts, removes `app/.cxx`, `app/build`, `baselineprofile/build`, project `build` and `.gradle`, and checks that `sudo unshare --net` is available.
3. Inside a **network namespace without interfaces**, the script drops root privileges back to the checkout owner using `runuser`. It verifies pinned sources again and invokes `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks :app:assembleRelease :app:bundleRelease`. A missing Maven/Gradle/Android artifact must fail instead of reaching the network.
4. `report-offline-android.py` verifies the unsigned APK and AAB exist and are ZIP files, checks the actual Android application ID `com.nexora.git` and three native ABI directories, and records the APK/AAB hashes, dependency lock hashes and warm-up byte comparison. It explicitly reports `releaseQualified: false`, `independentBuildsCompared: false` and `fdroidserverQualified: false`.
5. A short-retention CI artifact contains the audit **JSON only**, not an installable APK. No release signer or GitHub account credential is supplied to this workflow; the OAuth URL uses a deliberately nonfunctional `.invalid` hostname.

This first diagnostic build can establish whether the current project can compile its complete Android release artifact under network isolation **once Maven/Gradle and native sources have been prefetched**. It does not prove a clean F-Droid build farm, fdroidserver `srclibs` recipe compatibility, two independent source cleanrooms, APK byte-for-byte reproducibility, or future signature/update continuity.

**Next qualification steps after the experimental CI passes:** audit all Maven coordinates and transitive toolchain inputs in the F-Droid-approved build environment; author/verify the real fdroidserver source-acquisition and prebuild recipe; run two separate build jobs with independent caches and compare unsigned/signing-stripped outputs and APK manifests; then request maintainer review. Keep `disable:` in F-Droid metadata until all those stages succeed.

## Outstanding release blockers

- [ ] Declare and verify nine pinned upstream sources through auditable `fdroidserver` source-library/prebuild configuration; review archive licensing.
- [ ] Resolve upstream CMake submodules and all transitive native source dependencies without build-time network access.
- [ ] Make the Gradle wrapper/plugin/Maven dependency cache fully reproducible within the fdroidserver-supported toolchain.
- [ ] Configure a non-confidential public GitHub App client ID, HTTPS Auth Broker and callback for the public F-Droid build, without embedding server secrets.
- [ ] Run two clean, isolated builds from the same source commit and record APK identity, size and binary/reproducibility analysis.
- [ ] Obtain F-Droid repository maintainer review of branding, privacy, licensing, anti-features and network-usage disclosures.
- [ ] Only then remove `disable:` from `metadata/com.nexora.git.yml` and enable distribution/update checks.

**Do not reuse a Play upload key as a promise of F-Droid signing identity.** F-Droid distribution may use its own app signature; Android installed updates require compatible signer lineage. Explain different signing identities to users.

See [Signing transparency](store/SIGNING_TRANSPARENCY.md), [F-Droid official metadata](../metadata/com.nexora.git.yml) and [Release process](RELEASE_PROCESS.md).
