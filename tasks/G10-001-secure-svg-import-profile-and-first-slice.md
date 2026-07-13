# G10-001 — Secure SVG import profile and first slice

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Approve a deliberately small static SVG marker profile and import it directly into the established
toolkit-neutral symbol model through secure parse, render, native, and consumer paths.

## Context

Level 1 supports vector paths and explicit symbol registration, but arbitrary SVG is too broad and
unsafe for this library. JDK XML parsing is sufficient for a bounded first profile if external
resolution and expansion are disabled.

## Scope

Complete **G10 secure SVG profile approval**, then create published AWT-free `mundane-map-io-svg` with
working bounded local byte/path import. Support the exact approved root, group/basic-shape/path,
transform, fill/stroke/opacity, and representability profile; return only ordinary immutable marker or
composite symbols. Reuse `SymbolRendererRegistry.builtIn()` with no SVG renderer. Extend architecture,
render regression, native literal-input smoke, publication inventory, and staged consumer evidence.

## Out of scope

Scripts, animation, CSS/style/class, text, filters, masks, clipping, gradients/patterns, arcs, external
or local references, network access, embedded raster/data URLs, DTDs/entities, DOM/XPath/Transformer,
arbitrary SVG conformance, map export, and silent fallback for unsupported content.

## Acceptance criteria

- **G10 secure SVG profile approval** records the exact element/attribute/path/transform/paint tables,
  circle approximation, representability restrictions, security settings, default limits,
  diagnostics, native scenario, and publication extension before module creation.
- Secure JDK StAX rejects forbidden XML features, external resolution, invalid UTF-8/XML, unsupported
  SVG, non-finite/unrepresentable geometry, and exact/one-over limits with stable path-free outcomes.
- A supported document produces one ordinary immutable `VectorMarkerSymbol` or ordered
  `CompositeSymbol`, returned as a verified `MARKER`-role `Symbol`, and renders through the existing
  explicitly constructed built-in registry; no XML, DOM, SVG scene graph, or external type leaks
  through public map contracts.
- The working published module has Javadocs, API/core/AWT/format architecture coverage, tolerant
  render regression, shared JVM/native parse/reject/render assertions, and staged offline consumer use.
- Unsupported SVG expansion remains a future backlog candidate and receives a separate task only after
  demonstrated demand; this first slice is not deferred for another decomposition.

## Required tests

Exact grammar/shape/path/transform/style/view-box tests; ownership and immutable-output tests; XXE,
entity/DTD/reference/UTF-8/XML/unsupported-profile, limit/overflow, cancellation, local HTTP/file
canary, deterministic mutation, and stable diagnostic tests; parse-to-render tolerant regression;
native JVM parity; publication/consumer and architecture boundary tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-svg:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew renderRegression --console=plain
./gradlew nativeSmoke --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 secure SVG profile approval**. General SVG support is not a goal. Because this
task adds a working post-Level-1 published/native-targeted module, its implementation must integrate
against the then-current inventories rather than hard-code a historical module/resource count. It is
dependency-parallel with G9-008 but not path-safe: whichever task lands second retains the first
task's append-only native scenario and reconciles the complete authoritative manifest.
