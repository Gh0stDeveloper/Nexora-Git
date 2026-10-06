# Performance Audit

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Scope

The audit covers repository browsing, editing, syntax analysis, project search, Git operations and release artifact size.

## Existing bounded workloads

- code-browser text preview: 2 MiB cap;
- mobile editor safety limit: 512 KiB;
- project search: 5,000 files, 500 matches and 1 MiB per file;
- Tree-sitter native traversal: bounded node/span/symbol/diagnostic counts;
- GitHub Actions log preview and binary downloads use explicit limits;
- release asset and LFS paths use bounded/streaming transfers.

## Threading

Disk and network work uses coroutine IO dispatchers. Tree-sitter/formatter analysis uses background dispatchers. Compose state updates remain on ViewModel scopes rather than performing blocking filesystem traversal inside composables.

## Release optimization

Release builds enable:

- R8 code shrinking and optimization;
- resource shrinking;
- non-debuggable release variant;
- separate native symbol-table output.

## Artifact budgets

Production CI fails when:

- AAB exceeds 200 MiB;
- universal APK exceeds 250 MiB.

These budgets are guardrails, not performance targets. They prevent accidental native/runtime asset growth from silently shipping.

## Follow-up metrics

After real users exist, measure cold start, editor frame time and repository-search latency on representative low/mid/high Android hardware. No telemetry SDK is added solely for performance measurement.

---

[← Documentation hub](README.md)
