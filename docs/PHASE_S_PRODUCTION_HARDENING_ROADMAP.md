# Phase S — Production Hardening & Product Polish

> **Status:** planned / not started  
> **Purpose:** close the gap between a feature-complete implementation and a production-ready public Android product.  
> **Scope:** repository governance, application security, Auth Broker hardening, OAuth callback integrity, native/JNI assurance, supply-chain security, UX, localization, settings, durable background work, performance, accessibility, website security/SEO, GitHub discoverability, distribution and release qualification.

Phase S exists because the post-Phase-R audit found several areas where the implementation is technically strong but launch readiness is not yet at the same standard. No Phase S item may be marked complete based only on documentation or an architectural stub. Where applicable, completion requires working user-facing behavior, automated validation, negative-path testing and release evidence.

## Severity and execution order

Work must be executed in this order unless a blocker requires a temporary dependency inversion:

1. **P0 — Security and repository governance**
2. **P1 — Reliability, supply chain and durable execution**
3. **P1 — Product UX, localization and accessibility**
4. **P2 — Performance, web/SEO, community and distribution**
5. **Release candidate qualification**

A P0 gate blocks a public stable release. A P1 gate blocks the Phase S closeout. P2 items may only be deferred when the deferral is explicitly documented with rationale, owner and follow-up issue.

---

## S.0 — Audit baseline and traceability

**Goal:** freeze an auditable starting point before changing production-hardening behavior.

- [ ] Record the audited baseline commit and branch.
- [ ] Record current Android, Native Git, Auth Broker, Web, Production and CodeQL workflow status.
- [ ] Create a Phase S implementation checklist with links to PRs and validation evidence.
- [ ] Map every Phase S item to one or more source files, tests, workflows or external account controls.
- [ ] Document accepted risks separately from unfinished work.
- [ ] Do not mark an item complete when only documentation exists but runtime behavior is absent.

### Acceptance gate

- [ ] A reviewer can identify the exact implementation and validation evidence for every checked Phase S item.

---

## S.1 — Repository governance and protected main

**Priority:** P0

**Goal:** make it impossible to bypass the security and production validation pipeline accidentally.

### S.1.1 Branch protection / ruleset

- [ ] Protect the default branch.
- [ ] Require pull requests before changes enter `main`.
- [ ] Require the latest branch state before merge when practical.
- [ ] Require successful Android CI.
- [ ] Require successful Native Git CI.
- [ ] Require successful Production CI.
- [ ] Require successful CodeQL.
- [ ] Require Auth Broker CI when broker code is affected.
- [ ] Require Web CI when website code is affected.
- [ ] Block force pushes to `main`.
- [ ] Block deletion of `main`.
- [ ] Require resolved review conversations where GitHub account capabilities allow it.
- [ ] Decide whether signed commits are mandatory; document the decision.
- [ ] Enable automatic source-branch deletion after merge unless a branch is intentionally retained.

### S.1.2 Merge policy

- [ ] Define the preferred merge method.
- [ ] Prevent direct production releases from unreviewed commits.
- [ ] Verify release tags point to commits contained in protected `main`.
- [ ] Document emergency/security hotfix procedure without creating a permanent bypass.

### Acceptance gate

- [ ] A direct push cannot bypass required validation.
- [ ] A force push cannot rewrite `main`.
- [ ] A release tag outside protected `main` cannot create an official release.

---

## S.2 — Auth Broker reverse-proxy and rate-limit hardening

**Priority:** P0

**Goal:** ensure public OAuth endpoints behave correctly behind Nginx and cannot be trivially abused.

### S.2.1 Correct client identity

- [ ] Audit how the broker determines client IP behind loopback Nginx.
- [ ] Do not treat every proxied request as `127.0.0.1`.
- [ ] Prefer Nginx-side public rate limiting for externally exposed OAuth routes.
- [ ] If forwarded client headers are consumed by Go, trust them only when the TCP peer is an explicitly trusted reverse proxy.
- [ ] Never trust arbitrary public `X-Forwarded-For` values.

### S.2.2 Nginx abuse controls

- [ ] Add `limit_req_zone` with a sensible per-IP policy.
- [ ] Apply stricter limits to token exchange, refresh and revoke routes than to static/public routes.
- [ ] Preserve a distinct health-check path.
- [ ] Return standards-compliant `429` behavior.
- [ ] Retain bounded request bodies.
- [ ] Keep Auth Broker bound to loopback only.
- [ ] Validate Nginx syntax in CI.

### S.2.3 Broker robustness

- [ ] Propagate request cancellation/deadlines to upstream GitHub requests.
- [ ] Review token-response error normalization to avoid leaking unnecessary upstream details.
- [ ] Add tests for malformed JSON, oversized bodies, unknown fields and invalid token shapes.
- [ ] Add rate-limit unit/integration tests.
- [ ] Add reverse-proxy integration tests proving two different clients are not collapsed into one quota bucket.
- [ ] Ensure sensitive OAuth responses remain `Cache-Control: no-store`.

### Acceptance gate

- [ ] Multiple public clients are rate-limited independently.
- [ ] Spoofed forwarding headers cannot bypass or poison the limiter.
- [ ] OAuth endpoint abuse controls pass automated integration tests.

---

## S.3 — OAuth callback integrity and Android App Links

**Priority:** P0

**Goal:** reduce callback interception and login denial risks inherent in an unverified custom URI scheme.

### S.3.1 Migration design

- [ ] Design an HTTPS Android App Link callback.
- [ ] Select a stable production callback path.
- [ ] Add `android:autoVerify="true"` intent handling.
- [ ] Publish a valid `/.well-known/assetlinks.json`.
- [ ] Bind the association to the production package name and signing-certificate fingerprint.
- [ ] Preserve strict state validation and PKCE.
- [ ] Preserve the broker-side fixed redirect destination.
- [ ] Decide whether the custom `nexoragit://` scheme remains as a temporary compatibility fallback.

### S.3.2 Callback tests

- [ ] Test valid callback delivery.
- [ ] Test state mismatch.
- [ ] Test expired authorization state.
- [ ] Test duplicate callbacks.
- [ ] Test malformed callback parameters.
- [ ] Test callback from an unverified host.
- [ ] Test process recreation between browser launch and callback.
- [ ] Test fallback behavior when no supported browser exists.

### Acceptance gate

- [ ] Production login uses a verified association between the HTTPS callback domain and the signed Android app, or a formal documented risk acceptance exists for keeping the custom scheme.

---

## S.4 — Android secret handling and runtime configuration assurance

**Priority:** P0

**Goal:** keep confidential material outside the APK while reducing trivial runtime-configuration discovery.

- [ ] Preserve the invariant that the GitHub App client secret never ships in APK/AAB/native libraries.
- [ ] Keep public runtime identifiers/configuration behind the neutral native library boundary where currently intended.
- [ ] Ensure release symbols do not expose unnecessary JNI/configuration names.
- [ ] Keep `RegisterNatives` rather than name-based exported JNI symbols for the protected runtime bridge.
- [ ] Validate release APK/AAB do not contain forbidden secret names or literal secret values.
- [ ] Add artifact scanning for common credential patterns.
- [ ] Add regression tests that intentionally inject a fake forbidden secret and verify CI fails.
- [ ] Review generated native symbol visibility and linker stripping.
- [ ] Document clearly that obfuscation is defense-in-depth, not secret storage.

### Acceptance gate

- [ ] A release artifact scan verifies no confidential credentials are present.
- [ ] Runtime configuration obfuscation does not become a dependency for protecting any server-side secret.

---

## S.5 — Static analysis expansion and native memory-safety validation

**Priority:** P0/P1

**Goal:** apply security analysis to every security-relevant implementation language.

### S.5.1 CodeQL expansion

- [ ] Retain Java/Kotlin CodeQL.
- [ ] Add C/C++ analysis for JNI/native Git code where supported.
- [ ] Add Go analysis for the Auth Broker.
- [ ] Add JavaScript/TypeScript analysis for the Next.js website.
- [ ] Scope workflow path filters so each language analysis runs when relevant.
- [ ] Keep third-party CodeQL actions pinned to immutable revisions.

### S.5.2 Native hardening CI

- [ ] Add host-side AddressSanitizer coverage for project-owned native code.
- [ ] Add UndefinedBehaviorSanitizer coverage.
- [ ] Add `clang-tidy` with an explicit, reviewed rule set.
- [ ] Add fuzz targets for parsers, JNI boundary decoding and other attacker-controlled inputs where practical.
- [ ] Keep fuzzing outside production APK builds.
- [ ] Add regression corpus for previously fixed crashes.
- [ ] Fail CI on sanitizer findings.

### Acceptance gate

- [ ] Kotlin/Java, C/C++, Go and TypeScript security-sensitive code all have automated static analysis.
- [ ] Native tests execute cleanly under ASan/UBSan.

---

## S.6 — Dependency and software-supply-chain hardening

**Priority:** P1

### S.6.1 Dependency monitoring

- [ ] Extend Dependabot to Gradle.
- [ ] Extend Dependabot to npm.
- [ ] Extend Dependabot to Go modules.
- [ ] Keep GitHub Actions updates enabled.
- [ ] Define review policy for native pinned commit updates.
- [ ] Add automated vulnerability review for pinned native dependencies.

### S.6.2 Dependency integrity

- [ ] Evaluate Gradle dependency verification metadata.
- [ ] Pin or lock JavaScript dependencies with a committed lockfile and reproducible install policy.
- [ ] Preserve immutable native dependency revisions.
- [ ] Verify Docker base-image update strategy.
- [ ] Document the policy for transitive dependency changes.

### S.6.3 Release provenance

- [ ] Generate an SBOM for official releases in CycloneDX or SPDX format.
- [ ] Attach the SBOM to GitHub Releases.
- [ ] Add GitHub artifact attestations/build provenance where supported.
- [ ] Record source commit, workflow identity and release artifact digest.
- [ ] Keep `SHA256SUMS.txt`.
- [ ] Consider signing checksum/provenance metadata separately from APK signing.

### Acceptance gate

- [ ] An official release can be traced from source commit to build workflow, package manifest/SBOM and final digest.

---

## S.7 — Global Settings information architecture

**Priority:** P1

**Goal:** expose product configuration as a coherent user-facing surface rather than scattered implementation settings.

Create a top-level Settings destination with sections for:

- [ ] Appearance.
- [ ] Language.
- [ ] Editor.
- [ ] Git defaults.
- [ ] Network and transfer behavior.
- [ ] Storage/workspaces.
- [ ] Accounts.
- [ ] Security.
- [ ] Updates/releases.
- [ ] Privacy.
- [ ] Diagnostics.
- [ ] About/licenses.

### S.7.1 Required behavior

- [ ] Persist user settings with DataStore where appropriate.
- [ ] Do not store credentials in general Settings DataStore.
- [ ] Provide reset-to-default where safe.
- [ ] Explain settings that change destructive Git behavior.
- [ ] Keep advanced settings discoverable but visually separated from common preferences.
- [ ] Add navigation and Compose tests.

### Acceptance gate

- [ ] Every persistent end-user preference has an explicit user-facing control or is intentionally documented as internal-only.

---

## S.8 — Appearance and theme completion

**Priority:** P1

**Goal:** turn existing theme infrastructure into a real supported feature.

- [ ] Connect `SYSTEM`, `LIGHT`, `DARK` and `AMOLED` to persisted settings.
- [ ] Apply theme changes without requiring an app restart where feasible.
- [ ] Decide whether dynamic color is exposed; if exposed, persist it.
- [ ] Verify contrast in every supported theme.
- [ ] Verify code editor and diff colors independently from general Material colors.
- [ ] Test navigation/system-bar icon appearance in every mode.
- [ ] Add theme UI tests and screenshot/golden coverage where maintainable.

### Acceptance gate

- [ ] Users can select and persist all themes advertised by product documentation.

---

## S.9 — Internationalization and localization

**Priority:** P1

**Goal:** eliminate hardcoded user-facing English strings.

### S.9.1 Resource migration

- [ ] Move all user-visible Android strings to resources.
- [ ] Replace hardcoded Compose text with `stringResource` or equivalent localized resources.
- [ ] Externalize content descriptions.
- [ ] Externalize error messages that are intended for users.
- [ ] Keep protocol/error identifiers internal and locale-independent.

### S.9.2 Initial locales

- [ ] Complete English resources.
- [ ] Complete Spanish resources.
- [ ] Review Mexican Spanish store metadata for consistency with in-app terminology.
- [ ] Add plural resources where required.
- [ ] Validate RTL-safe layouts even before adding an RTL locale.

### S.9.3 Quality gates

- [ ] Add a lint/check preventing new hardcoded user-facing strings.
- [ ] Test large translations and truncation.
- [ ] Test locale change behavior.
- [ ] Verify repository names, paths and code remain unmodified by localization.

### Acceptance gate

- [ ] The full primary workflow is usable in English and Spanish without mixed-language UI caused by hardcoded strings.

---

## S.10 — Onboarding and first-run experience

**Priority:** P1

**Goal:** make first use understandable for developers with different Git skill levels.

- [ ] Connect the existing onboarding state to a real first-run flow.
- [ ] Explain local Git versus GitHub briefly.
- [ ] Explain why authorization opens GitHub.
- [ ] Explain where repositories live on device.
- [ ] Offer three clear first actions: clone, import, create.
- [ ] Offer skip/finish behavior without trapping experienced users.
- [ ] Persist completion state.
- [ ] Allow replaying onboarding/help from Settings.
- [ ] Add contextual empty states after onboarding.
- [ ] Add instrumentation tests for first launch, skip, completion and return launch.

### Acceptance gate

- [ ] A new user can authenticate and reach a usable repository workflow without needing README documentation.

---

## S.11 — Home/dashboard product polish

**Priority:** P1

**Goal:** make Home a continuation surface rather than a static menu.

Add, where data exists:

- [ ] Recent local repositories.
- [ ] Recent GitHub repositories.
- [ ] Continue editing / recently opened workspace.
- [ ] Current branch and uncommitted-change summary.
- [ ] Pending local commits/ahead state.
- [ ] Pull requests requiring attention where API permissions permit.
- [ ] Failed/running Actions where relevant.
- [ ] Quick clone.
- [ ] Quick import.
- [ ] Quick create project/repository.
- [ ] Useful offline state.
- [ ] Clear account identity and account-switch access.

### Acceptance gate

- [ ] Returning users can resume common work from Home with fewer taps than the current static-card flow.

---

## S.12 — Durable long-running operations

**Priority:** P1

**Goal:** prevent process death, background restrictions or network changes from corrupting long operations.

Review at minimum:

- [ ] Clone.
- [ ] Fetch/pull.
- [ ] Push.
- [ ] Large project import/sync.
- [ ] Git LFS transfers.
- [ ] Release asset upload/download.
- [ ] GitHub Actions artifact download.

### S.12.1 Execution architecture

- [ ] Identify which operations must survive process death.
- [ ] Use WorkManager for durable deferrable work where appropriate.
- [ ] Use foreground execution/notification where Android requires a user-visible long transfer.
- [ ] Support cancellation.
- [ ] Surface progress.
- [ ] Define retry semantics.
- [ ] Define network constraints.
- [ ] Make partial-file writes atomic/recoverable.
- [ ] Reconcile operation state after process recreation.

### Acceptance gate

- [ ] Killing and restoring the app during a selected long-running test operation does not silently corrupt repository or transfer state.

---

## S.13 — Performance engineering and benchmark gates

**Priority:** P1/P2

### S.13.1 Benchmarks

- [ ] Add Android Macrobenchmark module.
- [ ] Measure cold startup.
- [ ] Measure warm startup.
- [ ] Measure critical Compose navigation.
- [ ] Measure large repository list rendering.
- [ ] Measure editor open latency.
- [ ] Measure project-search latency.
- [ ] Measure representative Git status/diff operations.

### S.13.2 Baseline Profile

- [ ] Generate a Baseline Profile for critical startup/navigation paths.
- [ ] Integrate profile generation/verification into release workflow where practical.

### S.13.3 Device matrix

Define representative test classes:

- [ ] low-end physical/emulated target;
- [ ] mid-range target;
- [ ] high-end target.

Set budgets for:

- [ ] startup time;
- [ ] frame jank;
- [ ] editor interaction;
- [ ] memory under large project;
- [ ] repository scan/search.

### S.13.4 Artifact budgets

- [ ] Replace only-catastrophic APK/AAB caps with target and hard-limit budgets.
- [ ] Track size deltas between releases.
- [ ] Alert on unexpected native payload growth.

### Acceptance gate

- [ ] Performance claims are backed by repeatable benchmark results, not only workload caps.

---

## S.14 — Accessibility release qualification

**Priority:** P1

### S.14.1 Automated coverage

- [ ] Preserve semantic labels for icon-only controls.
- [ ] Verify decorative icons remain excluded from duplicate announcements.
- [ ] Add tests for key accessibility labels/roles/states.
- [ ] Verify destructive confirmations expose meaningful accessibility text.
- [ ] Verify error and loading states are announced appropriately where practical.

### S.14.2 Physical-device/manual matrix

Execute and record evidence for:

- [ ] TalkBack.
- [ ] maximum/large font scaling.
- [ ] increased display size.
- [ ] gesture navigation.
- [ ] three-button navigation.
- [ ] portrait.
- [ ] landscape where supported.
- [ ] hardware keyboard/focus navigation.
- [ ] light theme.
- [ ] dark theme.
- [ ] AMOLED theme.

### Acceptance gate

- [ ] Accessibility is not marked complete until the signed release candidate passes the manual matrix on a physical device.

---

## S.15 — Website security, privacy and technical SEO

**Priority:** P1/P2

### S.15.1 Production configuration safety

- [ ] Remove the silent production fallback to `https://nexora-git.invalid`.
- [ ] Require a valid HTTPS `NEXT_PUBLIC_SITE_URL` for production deployment/build.
- [ ] Add CI tests for canonical, sitemap and robots domain correctness.

### S.15.2 Security headers

- [ ] Add a reviewed Content-Security-Policy.
- [ ] Keep HSTS.
- [ ] Keep `X-Content-Type-Options: nosniff`.
- [ ] Keep clickjacking protection.
- [ ] Keep/refine Referrer-Policy.
- [ ] Keep restrictive Permissions-Policy.
- [ ] Disable unnecessary server disclosure.
- [ ] Add header regression tests.

### S.15.3 Privacy

- [ ] Add a public `/privacy` page.
- [ ] Keep privacy wording synchronized with `docs/PRIVACY.md`.
- [ ] Link Privacy from footer.
- [ ] Link Privacy from app Settings/About.
- [ ] Use the permanent HTTPS Privacy URL for store submissions.

### S.15.4 SEO

- [ ] Keep canonical metadata.
- [ ] Keep Open Graph metadata.
- [ ] Keep Twitter/X card metadata.
- [ ] Keep robots and sitemap.
- [ ] Add `SoftwareApplication` / `MobileApplication` JSON-LD.
- [ ] Include version/download/license/source information in structured data where valid.
- [ ] Evaluate replacing full `force-dynamic` rendering with static/ISR plus controlled release-data revalidation.
- [ ] Validate metadata in production, not only source.

### Acceptance gate

- [ ] Production pages emit the correct canonical domain, security headers, Privacy URL and valid application structured data.

---

## S.16 — GitHub discoverability, community and repository presentation

**Priority:** P2  
**Development milestone:** implemented and ready for CI/review, subject to PR merge.  
**External deployment exceptions:** homepage [#46](https://github.com/Gh0stDeveloper/Nexora-Git/issues/46); final screenshots [#47](https://github.com/Gh0stDeveloper/Nexora-Git/issues/47). Both remain **open work**, not completed deliverables.

### S.16.1 Repository metadata

- [x] Review repository description for search clarity — verified via repository API.
- [x] Add all nine focused GitHub topics — verified via repository API.
- [x] Enable Discussions for community support — verified via repository API.
- [x] Enable automatic merged-branch deletion — verified via repository API.
- [x] Protect main via active ruleset and required CI checks — verified via ruleset API.
- [ ] Set homepage to the **real deployed** HTTPS product website; deliberately deferred to issue #46.

### S.16.2 README presentation and captures

- [x] Clear project purpose, primary feature set, architecture and support links in README.
- [x] Explain the verified-release download process without claiming unreleased builds exist.
- [x] Provide contribution, support and security guidance, Issue templates and PR template.
- [x] Track future owner-taken screenshots without showing broken images or fabricated placeholders.
- [x] Make Community Readiness pass for the **documented development deferral**, while rejecting incomplete screenshot sets.
- [ ] Capture eight real final Android screenshots manually **after working app and own VPS/Auth Broker qualification** (issue #47).
- [ ] Publish Home (Light, Dark, AMOLED), repository detail, editor, Git workbench, PR and Actions screenshots.
- [ ] Add and review the final README image gallery from approved screenshots.
- [ ] Run the explicit `--require-screenshots` release presentation check.

The user will capture the screenshots on an Android device once the application is verified with the production VPS. The optional `Marketing Screenshots` workflow performs **manual on-demand validation only**. It does not generate, commit or overwrite images. This prevents a broken emulated screenshot job from interrupting ongoing development; it does **not** assert screenshots exist.

### Acceptance gates

- [x] **Development presentation gate:** project scope, contribution routes, support and trusted download instructions are understandable without broken image references. Deferred launch-only dependencies are documented and owned.
- [ ] **Public launch presentation gate:** real product screenshots and verified live homepage are available and reviewed before they are advertised. Track via issues #46 and #47.

**Audit rule:** keep the external dependencies open until independently verified. The subphase may be merged as development-ready only when its required GitHub CI checks pass; do not equate that merge with public-launch readiness.

---

## S.17 — Store/ASO and distribution completeness

**Priority:** P2

### S.17.1 Play Store assets

- [ ] Final app title.
- [ ] Short description.
- [ ] Full description.
- [ ] English listing.
- [ ] Spanish listing.
- [ ] Phone screenshots.
- [ ] Feature graphic.
- [ ] High-resolution icon.
- [ ] Privacy URL.
- [ ] Support/source URL.
- [ ] Release notes/changelog process.
- [ ] Data Safety answers reviewed against actual runtime behavior.

### S.17.2 Signing transparency

- [ ] Publish the production signing-certificate SHA-256 fingerprint.
- [ ] Show it on the official website.
- [ ] Show it in release documentation.
- [ ] Preserve APK SHA-256 display/download.
- [ ] Document independent verification commands.

### S.17.3 F-Droid

- [ ] Resolve build-time network dependency acquisition.
- [ ] Make native source acquisition compatible with reproducible/offline F-Droid builds.
- [ ] Re-enable F-Droid build metadata only after successful reproducibility validation.
- [ ] Keep `NonFreeNet` disclosure for GitHub-dependent features where applicable.

### Acceptance gate

- [ ] Store metadata and direct-download metadata describe the same product behavior and signing identity.

---

## S.18 — Release-candidate program

**Priority:** P0 release gate

Do not jump from implementation-complete directly to a stable `1.0.0`.

### S.18.1 RC preparation

- [ ] Choose `v1.0.0-beta.1` or `v1.0.0-rc.1` as the first public qualification release.
- [ ] Build from protected `main`.
- [ ] Run every required workflow.
- [ ] Publish signed APK.
- [ ] Publish AAB where appropriate.
- [ ] Publish checksums.
- [ ] Publish SBOM/provenance after S.6 is complete.
- [ ] Publish signing fingerprint.
- [ ] Publish release notes with known limitations.

### S.18.2 Real-device qualification

Test at minimum:

- [ ] clean install;
- [ ] login/logout;
- [ ] account switching;
- [ ] clone public repository;
- [ ] clone private repository;
- [ ] import local project;
- [ ] edit/save;
- [ ] stage/commit;
- [ ] fetch/pull/push;
- [ ] conflict workflow;
- [ ] Issues;
- [ ] Pull Requests;
- [ ] Actions;
- [ ] Releases;
- [ ] offline reopening;
- [ ] process death during representative work;
- [ ] low-storage behavior;
- [ ] network interruption;
- [ ] theme switching;
- [ ] English/Spanish;
- [ ] TalkBack.

### S.18.3 Stable promotion gate

Stable `v1.0.0` may be published only when:

- [ ] all P0 Phase S items are complete;
- [ ] all P1 Phase S items are complete;
- [ ] no known Critical or High security issue remains;
- [ ] no reproducible data-loss bug remains;
- [ ] no release-signing inconsistency remains;
- [ ] RC telemetry/feedback, if collected, contains no blocking regression;
- [ ] manual accessibility qualification is recorded;
- [ ] production website and Privacy URL are live;
- [ ] official signing identity is published;
- [ ] release artifacts are reproducibly traceable to the source commit.

---

## Phase S Definition of Done

Phase S is complete only when all of the following are true:

- [ ] repository governance prevents bypassing required production checks;
- [ ] Auth Broker reverse-proxy/rate-limit behavior is correct under real deployment topology;
- [ ] OAuth callback integrity has been strengthened or formally risk-accepted;
- [ ] confidential credentials remain absent from APK/AAB and native payloads;
- [ ] security analysis covers Android, native C/C++, Go and web code;
- [ ] dependency monitoring and release provenance are implemented;
- [ ] Settings, appearance and onboarding are actual user-facing features;
- [ ] Android UI is localized at least into English and Spanish;
- [ ] critical long-running operations have explicit process-death behavior;
- [ ] benchmarks and release performance budgets exist;
- [ ] signed RC accessibility QA has been executed;
- [ ] website Privacy, security headers and production SEO configuration are complete;
- [ ] GitHub metadata and visual presentation are launch quality;
- [ ] official signing identity is publicly verifiable;
- [ ] at least one public beta/RC has completed qualification;
- [ ] there are no unresolved P0 or P1 findings.

## Non-goals

Phase S must not expand into unrelated feature development. New GitHub APIs, new editor languages, new Git commands or major new product modules belong in later feature phases unless they are strictly required to close a Phase S security, reliability or UX finding.

## Completion evidence

Every completed subphase must record:

1. implementation PR;
2. exact tests/workflows executed;
3. relevant manual QA evidence;
4. documentation updated;
5. residual risk, if any;
6. merge commit in protected `main`.

Only then may its checkbox be changed from `[ ]` to `[x]`.
