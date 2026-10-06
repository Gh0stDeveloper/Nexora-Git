# Advanced Mobile Development

Phase Q turns Nexora Git's mobile editor and project browser into a stronger local development environment without changing the Android-first security model.

## Embedded Tree-sitter

Tree-sitter is compiled into the existing NDK library. No parser runtime or grammar is downloaded at runtime.

Bundled grammars:

- Kotlin
- Java
- JavaScript
- TypeScript
- TSX
- Python
- JSON

The runtime and every grammar are pinned to an exact Git revision in `native/git/CMakeLists.txt`. The native parser returns semantic spans, symbols and syntax diagnostics as a compact JSON contract through JNI.

### Offset correctness

Tree-sitter reports byte offsets in UTF-8 source. Compose edits Kotlin `String` values using UTF-16 indexes. The native syntax engine converts every exposed range to UTF-16 before it crosses JNI, including code that contains emoji or supplementary Unicode characters.

### Mobile limits

AST traversal is bounded to protect memory and frame time:

- 12,000 visited nodes
- 5,000 semantic spans
- 1,000 symbols
- 200 diagnostics

Editor parsing is debounced and runs away from the UI thread. Unsupported languages retain the existing local regex highlighter.

## Local language intelligence

The editor exposes:

- AST-backed syntax highlighting
- function/type/property symbols
- syntax errors and missing-node diagnostics
- symbol navigation inside the current document
- parser/root status in the editor
- a transparent regex fallback when no native grammar exists

This is structural language intelligence, not a claim of compiler-level semantic analysis.

## Optional LSP architecture

Phase Q adds a provider-neutral LSP contract and JSON-RPC 2.0 codec. The registry supports multiple future providers selected by language.

No language-server executable is bundled, downloaded, or started automatically in Phase Q. This is intentional: Nexora Git does not execute arbitrary binaries merely because a repository asks it to. A future language-server provider must explicitly define its lifecycle, trust boundary and installation policy.

The architecture includes document open/change/close operations and formatting capability contracts so a later trusted provider can be integrated without rewriting the editor.

## Formatters

Formatting always edits the in-memory buffer first. The user still decides when to save.

Current formatters:

- JSON: parse + deterministic pretty print
- Kotlin / Java / JavaScript / TypeScript: structural brace indentation

For structured languages, Tree-sitter string/comment ranges are protected so braces inside those spans do not change indentation depth. Formatting participates in editor undo/redo.

Python is deliberately not handled by the brace formatter because indentation is semantically significant.

## Project templates

Nexora Git can create local generated workspaces for:

- Kotlin CLI
- Android Compose
- Node + TypeScript
- Python CLI

Generated workspaces are separate from SAF imports and remote clones. Template creation validates names, confines every rendered file to the workspace root, caps generated files and total bytes, and deletes partial output on failure.

Git is not silently initialized. The generated project appears in **On this device** and can be initialized through the existing **Init Git** workflow.

## Advanced project search

Project search supports:

- literal text
- regular expressions
- match case
- whole word
- include glob
- exclude glob
- path/line/column result metadata
- direct result opening

Default generated/cache directories such as `.git`, `.gradle`, `.idea`, `build`, `node_modules` and `dist` are skipped.

Safety/performance limits:

- query length: 256 characters
- files scanned: 5,000
- results: 500
- searchable file size: 1 MiB
- binary sample: 4 KiB
- result preview: 180 characters

Canonical-path checks and symbolic-link rejection keep search inside the selected workspace.

## Security boundary

Phase Q remains local-first. Tree-sitter parsing, formatting, templates and search operate on device. The optional LSP layer has no active external provider by default and does not weaken repository or authentication boundaries.
