# G4-003 — Feature Source Query Rendering Slice

Status: Complete
Depends on: G2-005, G3-003, G3-004, G4-001, G4-002
Gate: G4
Type: AFK

## Goal

Render viewport-bounded queries from an in-memory `FeatureSource` through a closeable cursor, while
adding the packed multipoint and multipart geometry needed by real vector formats.

## Context

`InMemoryLayer` currently exposes a complete feature list and `MapView` iterates it directly.
G4-001 defines source/query/cursor/diagnostic ownership, G4-002 defines CRS boundaries, G2-005 owns
explicit symbol rendering, G3-003 owns hit/hover/selection transactions, and G3-004 fixes the final
measurement-overlay order. The public geometry model has point, one-part line, and one polygon
exterior with holes, but shapefiles also require multipoint, multipart polyline, and multipart polygon
values.

## Scope

- Approved common and feature source contracts plus immutable multipoint/multipart geometry in
  `mundane-map-api`.
- Shared checked query accounting and an in-memory feature-source implementation in
  `mundane-map-core`.
- Explicit mixed legacy/source bindings, viewport querying, cancellation, rendering, interaction,
  report, fit, and close integration in `mundane-map-awt`.
- API/core/AWT/architecture tests, compatibility adaptation for existing layers, and public Javadocs.

## Out of scope

- Shapefile parsing, spatial indexing, simplification, asynchronous prefetch, editing, or mutable
  streaming updates.
- Arbitrary CRS transformation beyond the explicit G4-002 registry.
- Executors, background I/O, retained viewport-record caches, a public layer SPI, or a format module.

## Acceptance criteria

- Multipoint and multipart line/polygon values use packed primitive coordinates and part/ring
  offsets where appropriate, defensively copy inputs, preserve stable order, and validate cardinality,
  closure, holes, offsets, and finite values.
- The in-memory source exposes immutable metadata and applies envelope and approved attribute/query
  limits with deterministic source order.
- A map view derives a source-CRS query envelope from the visible viewport only when explicit direct
  transforms exist, opens at most one bounded cursor per source per operation, stages and CRS-preflights
  the complete result, and closes it before rendering or reporting on every path.
- Cursor iteration stops at cancellation and configured result/allocation limits, reporting the
  stable G4-001 diagnostic rather than retaining partial hidden state.
- Point, multipoint, single/multipart line, and polygon/multipolygon-with-holes all render through the
  real source-backed path.
- Existing `Layer`/`InMemoryLayer` consumers follow the G4-001 compatibility decision without an
  undocumented behavior change.
- Source replacement and view disposal close owned resources exactly once; caller-owned sources are
  not closed implicitly.
- Source-backed records participate in mixed paint/hit order, hover, selection, overlays, and
  fit-to-data without synthetic `Feature` values; bounded-query absence does not clear source
  selection as though it proved deletion.
- A binding exposes only a narrow cross-thread signal for its current synchronous source operation;
  cancellation never affects future operations or introduces a worker/executor.

## Required tests

- API tests for every multipart shape, offsets, defensive copies, invalid rings/parts, and envelopes.
- Core source tests for filtering, stable order, limits, cancellation, early close, diagnostics, and
  ownership.
- AWT integration tests that assert mixed bindings, query bounds, cursor closure, current-operation
  cancellation, all-or-nothing CRS failure, reports, fit, ownership, and source interaction state.
- Offscreen-image tests for every singular/multipart family, holes, ordering, endpoint markers,
  overlays, and one-record hit identity.
- Regression tests for existing in-memory layer rendering.
- Architecture tests for toolkit/format boundaries and absence of discovery, executors, prefetch, or
  retained query caches.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test :modules:mundane-map-architecture-tests:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

A linear in-memory scan is sufficient here; G7 supplies the packed index. Do not add a format module
until it reads working records in its own vertical slice. This task is intentionally at the upper end
of the target size because it delivers the already-approved feature contracts through one complete
render/hit/lifecycle slice rather than splitting the contract into non-runnable class cards.
