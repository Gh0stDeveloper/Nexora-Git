# F-Droid Readiness

F-Droid support is optional for Nexora Git.

## Prepared

- open-source repository and Apache-2.0 licensing;
- stable application ID/version scheme;
- F-Droid metadata skeleton at `metadata/com.nexora.git.yml`;
- GitHub network-service dependency disclosed as `NonFreeNet`;
- reproducible native dependency revisions are documented/pinned.

## Current build-system blocker

The native CMake build currently uses `FetchContent` to acquire libgit2, Mbed TLS, Tree-sitter and language grammars. F-Droid build infrastructure expects source dependencies to be acquired reproducibly by fdroidserver rather than through arbitrary build-time network access.

For that reason the initial F-Droid build entry is intentionally disabled. Do not remove the `disable` gate until native dependencies are vendored or mapped to F-Droid-compatible source-library acquisition.

## Authentication consideration

The F-Droid flavor must also receive the public production GitHub App client ID and HTTPS broker/callback configuration without embedding any confidential client secret.

No separate closed-source runtime is required.
