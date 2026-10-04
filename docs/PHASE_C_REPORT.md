# Phase C — GitHub Platform Layer Report

Date: 2026-10-04

## Status

**Implementation complete.**

Phase C creates the reusable remote transport foundation for future repository, issue, pull-request, Actions, releases, search and social features.

## Delivered

### REST

- authenticated REST execution;
- centralized official API base URL;
- centralized REST API version;
- support for GET/POST/PUT/PATCH/DELETE;
- custom headers/query parameters;
- official-host URL validation;
- ETag revalidation;
- 304 cache reuse;
- REST Link pagination.

### GraphQL

- authenticated GraphQL endpoint;
- variables;
- normalized data/error envelope;
- GraphQL error classification;
- cursor page-info model/parser;
- query cache;
- mutation cache invalidation.

### Authentication bridge

- active account resolution;
- valid-token resolution;
- proactive refresh inherited from Phase B;
- one forced refresh + retry on 401;
- no infinite authentication retry loop.

### Rate limits

- response header parsing;
- per-resource snapshots;
- primary exhaustion tracking;
- secondary throttling detection;
- Retry-After handling;
- StateFlow exposure for future UI.

### Permission diagnostics

- `X-Accepted-GitHub-Permissions` parser;
- read/write/admin levels;
- alternative permission sets;
- permission details included in domain errors.

### Cache

- bounded LRU memory cache;
- account-scoped keys;
- REST and GraphQL separation;
- configurable TTL;
- network-first/cache-first/network-only/no-store policies;
- mutation invalidation;
- no generic private GitHub payload persistence to disk.

### Standardized errors

`AppError` now carries actionable structured information for:

- network;
- authentication;
- permission;
- rate limit;
- not found;
- conflict;
- validation;
- server;
- parsing;
- local storage/Git errors.

## Tests

Phase C adds unit coverage for:

- REST Link parsing;
- external pagination-host rejection;
- GitHub App accepted-permission parsing;
- primary and secondary rate-limit mapping;
- rate-limit timing;
- official API URL resolution;
- malicious/external absolute URL rejection;
- GraphQL error classification;
- structured AppResult errors.

## CI validation

Android CI:

```text
Run: 37216844850
Head SHA: 11e55d92fd7dd6302ee2ec44bcbacadf5faa2b66
Conclusion: success
```

Validated:

- debug APK build;
- Android instrumentation-test APK compilation;
- JVM unit tests;
- Android lint;
- Gradle Wrapper validation;
- debug APK artifact upload.

Artifact:

```text
Name: NexoraGit-debug
Artifact ID: 11308772599
Size: 20,488,538 bytes
SHA-256: 8eb5fb6de4e924705b6b1438ae6462a15fce756c68db367b1d51445e27c95447
```

Foundation CI also passed for the final code SHA.

## External dependencies

Phase C does not introduce a new server component.

Live authenticated calls still depend on the Phase B production prerequisites:

- registered GitHub App;
- deployed Auth Broker;
- configured Android public auth values.

## Next phase

**Phase D — Real Git Engine**

Phase D moves from GitHub platform operations to genuine local Git through libgit2, Android NDK/C++ and JNI.
