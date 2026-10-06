# System Map

> [Documentation hub](README.md) · [Architecture](ARCHITECTURE.md) · [Authentication](AUTH.md) · [VPS installer](VPS_INSTALLER.md)

This document is the visual map of Nexora Git. It shows the major runtime boundaries, data paths and deployment relationships without mixing them with implementation history.

## End-to-end runtime

```mermaid
flowchart TB
    DEV[Developer on Android]

    subgraph ANDROID["Nexora Git Android application"]
        UI[Compose UI]
        VM[ViewModels / Coroutines / Flow]
        PLATFORM[GitHub Platform Client]
        STORAGE[Room + DataStore + SAF]
        EDITOR[Editor + Search + Tree-sitter]
        GIT[GitEngine]
        SECRETS[Android Keystore-backed token encryption]
    end

    subgraph NATIVE["Native Git layer"]
        JNI[JNI bridge]
        CORE[C++17 nexoragit_core]
        LIBGIT[libgit2]
        TLS[Mbed TLS]
    end

    subgraph GITHUB["GitHub"]
        REST[REST API]
        GRAPHQL[GraphQL API]
        OAUTH[OAuth endpoints]
        GITREMOTE[Git HTTPS remotes / LFS]
    end

    subgraph VPS["Self-hosted Auth Broker"]
        NGINX[Nginx + TLS]
        BROKER[Go Auth Broker]
        DOCKER[Docker Compose]
    end

    LOCAL[(Local repositories / managed workspaces)]

    DEV --> UI
    UI --> VM
    VM --> PLATFORM
    VM --> STORAGE
    VM --> EDITOR
    VM --> GIT
    VM --> SECRETS

    PLATFORM --> REST
    PLATFORM --> GRAPHQL

    GIT --> JNI --> CORE --> LIBGIT
    LIBGIT --> TLS
    LIBGIT --> LOCAL
    LIBGIT --> GITREMOTE

    UI -->|GitHub authorization| OAUTH
    OAUTH -->|HTTPS callback| NGINX
    NGINX --> BROKER
    DOCKER --> BROKER
    BROKER -->|code exchange / refresh / revoke| OAUTH
    BROKER -->|fixed native callback| UI
```

## Authentication path

```mermaid
sequenceDiagram
    participant A as Android app
    participant G as GitHub
    participant N as Nginx
    participant B as Auth Broker
    participant K as Android Keystore

    A->>A: Generate state + PKCE verifier/challenge
    A->>G: Open official GitHub authorization
    G-->>N: HTTPS callback with code + state
    N->>B: Reverse proxy callback
    B-->>A: Fixed nexoragit:// callback
    A->>A: Validate state
    A->>B: Exchange code + PKCE verifier
    B->>G: Exchange with confidential client secret
    G-->>B: Access + refresh token bundle
    B-->>A: Token bundle
    A->>K: Encrypt persistent token material
```

The broker never becomes a general GitHub API proxy. Normal GitHub API requests originate from the Android client using the authenticated user session.

## Local Git path

```mermaid
flowchart LR
    ACTION[User Git action] --> DOMAIN[GitEngine contract]
    DOMAIN --> IMPL[Libgit2GitEngine]
    IMPL --> BRIDGE[NativeGitBridge]
    BRIDGE --> JNI[JNI]
    JNI --> CORE[C++17 core]
    CORE --> LIB[libgit2]
    LIB --> ODB[(objects / refs / index)]
    LIB --> WT[(working tree)]
    LIB --> REMOTE[GitHub HTTPS remote]
    CREDS[GitCredentialProvider] --> POLICY[GitHub host security policy]
    POLICY --> LIB
```

GitHub OAuth credentials are injected only at runtime and only for eligible `https://github.com` Git operations. Tokens are not persisted in remote URLs or `.git/config`.

## Android storage path

```mermaid
flowchart LR
    PICKER[Storage Access Framework] --> SOURCE[User-selected source folder]
    SOURCE --> SCAN[Project scanner]
    SCAN --> MODE{Workspace strategy}
    MODE -->|Direct filesystem eligible| DIRECT[Direct workspace]
    MODE -->|Provider / filesystem constraints| MANAGED[App-managed workspace]
    MANAGED --> SYNC[Non-destructive source sync]
    DIRECT --> GIT[GitEngine]
    SYNC --> GIT
    GIT --> DOTGIT[Protected .git metadata]
```

## VPS deployment map

```mermaid
flowchart TB
    INTERNET[Internet / GitHub callback]
    FIREWALL[Cloud firewall / security group]
    NGINX[Nginx shared reverse proxy]
    SITE1[Other VPS sites]
    VHOST[Nexora Git auth vhost]
    CERT[Let's Encrypt certificate]
    LOOP[127.0.0.1:18080-18180]
    BROKER[Auth Broker container]
    ENV[Root-readable .env]
    REPO[/opt/nexora-git]
    STATE[/etc/nexora-git-vps.conf]

    INTERNET --> FIREWALL --> NGINX
    NGINX --> SITE1
    NGINX --> VHOST
    CERT --> VHOST
    VHOST --> LOOP --> BROKER
    ENV --> BROKER
    REPO --> BROKER
    STATE --> REPO
```

Nginx can continue serving unrelated sites on the same VPS. The installer creates a dedicated `server_name` virtual host and exposes the broker only on loopback.

## Trust boundaries

| Boundary | Protected asset | Main controls |
| --- | --- | --- |
| GitHub ↔ Android | authorization response | PKCE, random state, strict callback parsing |
| Android ↔ Auth Broker | token exchange | HTTPS, fixed endpoints, bounded inputs |
| Android persistent storage | access/refresh tokens | Android Keystore-backed AES-GCM |
| Kotlin ↔ native Git | repository operations | typed contract, sanitized native errors |
| libgit2 ↔ remote | GitHub credential | HTTPS-only host policy, ephemeral credential callback |
| SAF source ↔ managed workspace | project data | canonical path checks, conflict detection, protected `.git` |
| Internet ↔ VPS broker | confidential OAuth service | Nginx TLS, loopback container port, rate limits, read-only container |
| CI ↔ release artifacts | signing/integrity | external secrets, pinned actions, signature verification, checksums |

---

[← Documentation hub](README.md)
