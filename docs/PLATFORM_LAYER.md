# GitHub Platform Layer

## Purpose

Phase C provides the shared remote platform used by every GitHub-backed feature in Nexora Git.

It sits above authentication and below feature repositories.

```text
Issues / Pulls / Actions / Repositories / Search / Profile
                         ↓
                GitHubPlatformClient
                   ↙           ↘
          GitHubRestClient   GitHubGraphQlClient
                   ↓           ↓
             AuthSessionRepository
                         ↓
                       GitHub
```

## REST API

`GitHubRestClient` accepts `GitHubRestRequest` values.

Example:

```kotlin
val result = platformClient.rest.execute(
    GitHubRestRequest(
        pathOrUrl = "/user/repos",
        query = mapOf(
            "per_page" to GitHubApiConfig.MAX_REST_PAGE_SIZE.toString(),
        ),
    ),
)
```

The REST version is centralized in:

```text
GitHubApiConfig.REST_API_VERSION = 2026-03-10
```

All relative URLs resolve only against `https://api.github.com/`.

Absolute pagination URLs are rejected unless they use HTTPS and the exact `api.github.com` host.

## GraphQL API

GraphQL requests use:

```text
https://api.github.com/graphql
```

Example:

```kotlin
val result = platformClient.graphQl.execute(
    GitHubGraphQlRequest(
        query = """
            query ViewerLogin {
              viewer {
                login
              }
            }
        """.trimIndent(),
    ),
)
```

GraphQL responses expose:

- raw `data` JSON;
- normalized GraphQL errors;
- GitHub request ID;
- current rate-limit snapshot;
- cache provenance.

Feature modules may introduce typed parsing around the raw data payload without changing the transport boundary.

## Authentication integration

Both clients obtain the active account from `AuthSessionRepository`.

Before a request:

1. resolve active account;
2. obtain a valid access token;
3. refresh when near expiry;
4. execute the request.

If GitHub still returns `401`, the platform forces one refresh and retries once.

A second `401` is returned as an authentication failure. Infinite retries are prohibited.

## Pagination

### REST

GitHub REST pagination is parsed from the `Link` header.

The transport exposes:

- next;
- previous;
- first;
- last.

`GitHubRestPaginator.nextPage()` follows only previously validated `api.github.com` next URLs.

### GraphQL

`GitHubGraphQlPageInfo` models:

- `hasNextPage`;
- `hasPreviousPage`;
- `startCursor`;
- `endCursor`.

Cursor ownership remains with the feature query because GraphQL connection shape varies by feature.

## Rate limits

`GitHubRateLimitManager` reads response headers and tracks limits by resource.

Known fields include:

- resource;
- limit;
- remaining;
- used;
- reset time;
- Retry-After.

Primary exhaustion and secondary throttling are normalized into `AppError.RateLimited`.

The manager is exposed as a `StateFlow` so future UI can display remaining budget or temporarily disable expensive actions.

## Permissions

REST responses may include:

```text
X-Accepted-GitHub-Permissions
```

`GitHubPermissionResolver` parses single requirement sets and alternative requirement sets.

Example:

```text
pull_requests=read,contents=read; issues=read,contents=read
```

becomes two alternative permission sets.

This allows feature UI to explain which GitHub App permission is missing instead of reporting a generic HTTP 403.

GraphQL permissions are represented through normalized GraphQL error types because GitHub does not expose the same accepted-permissions header contract for GraphQL.

## Caching

The Phase C cache is:

- in-memory only;
- bounded;
- account scoped;
- never shared between GitHub identities.

REST supports ETag conditional requests.

Policies:

```text
NETWORK_ONLY
NETWORK_FIRST
CACHE_FIRST
NO_STORE
```

Behavior:

- `CACHE_FIRST`: fresh memory entry wins;
- `NETWORK_FIRST`: network is preferred, cache can be used after an I/O failure;
- `NETWORK_ONLY`: no cache read shortcut;
- `NO_STORE`: response is not cached.

Successful REST mutations clear the active account REST cache.

GraphQL mutations clear the active account GraphQL cache.

The cache is deliberately non-persistent so generic private repository payloads are not silently retained on disk.

## Error model

Platform failures map into `AppError`.

Important mappings:

```text
401 -> Authentication
403 + remaining=0 -> primary RateLimited
403 + Retry-After/secondary message -> secondary RateLimited
403 otherwise -> PermissionDenied
404 -> NotFound
409 -> Conflict
422 -> Validation
429 -> secondary RateLimited
5xx -> Server
I/O -> Network
invalid GraphQL payload -> Parsing
```

GraphQL `FORBIDDEN`, `NOT_FOUND` and validation-style errors are also classified.

## Logging

The shared OkHttp logging interceptor remains BASIC only in debug builds and disabled in release.

Sensitive headers are redacted:

- Authorization;
- Cookie;
- Set-Cookie.

Response bodies are not logged by the shared platform client.

## Security constraints

- never accept pagination redirects to non-GitHub hosts;
- never persist access tokens in requests or cache keys;
- never include tokens in URLs;
- never share cache entries between accounts;
- never retry authentication indefinitely;
- never reinterpret a permission failure as a rate-limit failure without evidence.
