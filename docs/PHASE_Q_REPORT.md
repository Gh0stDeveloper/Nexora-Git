# Phase Q Report — Advanced Mobile Development

## Scope

Phase Q upgrades mobile development workflows around the existing code browser and editor while preserving offline operation and bounded resource use.

## Q.1 Tree-sitter
- [x] native runtime embedded
- [x] exact revision pins
- [x] Kotlin
- [x] Java
- [x] JavaScript
- [x] TypeScript
- [x] TSX
- [x] Python
- [x] JSON
- [x] JNI bridge
- [x] UTF-16-safe editor ranges
- [x] native smoke coverage
- [x] Native Git CI

## Q.2 Language intelligence
- [x] semantic syntax spans
- [x] symbols
- [x] diagnostics
- [x] code outline
- [x] symbol selection
- [x] debounced background parsing
- [x] fallback highlighting

## Q.3 Optional LSP
- [x] client/provider contract
- [x] capability/status model
- [x] provider registry
- [x] empty provider multibinding
- [x] JSON-RPC 2.0 codec
- [x] protocol unit coverage
- [x] no automatic executable download

## Q.4 Formatting
- [x] JSON formatter
- [x] structured brace formatter
- [x] protected syntax spans
- [x] undo/redo integration
- [x] explicit save semantics
- [x] formatter unit coverage

## Q.5 Project templates
- [x] Kotlin CLI
- [x] Android Compose
- [x] Node + TypeScript
- [x] Python CLI
- [x] generated workspace lifecycle
- [x] safe rendered paths
- [x] bounded generated output
- [x] template unit coverage

## Q.6 Project search
- [x] literal and regex search
- [x] case / whole-word modes
- [x] include / exclude globs
- [x] binary/cache exclusions
- [x] canonical confinement
- [x] result caps
- [x] browser UI
- [x] unit and Compose coverage

## Validation

- [x] Native Git CI after Tree-sitter integration
- [x] JVM tests added for parser, LSP codec, formatter, templates and search
- [x] Compose tests extended for language intelligence, formatting, templates and project search
- [x] documentation
- [ ] final Android CI
- [ ] final Foundation CI
- [ ] pull-request integration validation

## Merge gate

Phase Q remains on `phase-q-advanced-mobile-development` until Android and Foundation CI pass on the final implementation and the pull request is validated against `main`.
