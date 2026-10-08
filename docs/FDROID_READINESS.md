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
