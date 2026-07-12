# G4-003 — Feature Source Query Rendering Slice

Status: Proposed
Depends on: G4-001, G4-002
Gate: G4
Type: AFK

## Goal

Render viewport-bounded queries from an in-memory `FeatureSource` through a closeable cursor, while
adding the packed multipoint and multipart geometry needed by real vector formats.

## Context

`InMemoryLayer` currently exposes a complete feature list and `MapView` iterates it directly.
G4-001 defines source/query/cursor/diagnostic ownership, and G4-002 defines CRS boundaries. The public
geometry model has point, one-part line, and one polygon exterior with holes, but shapefiles also
require multipoint, multipart polyline, and multipart polygon values.

## Scope

- Approved source contracts plus immutable multipoint/multipart geometry in
  `mundane-map-api`.
- An in-memory feature-source implementation and viewport query logic in `mundane-map-core`.
- Source-backed layer rendering and cursor lifecycle integration in `mundane-map-awt`.
- API/core/AWT tests, compatibility adaptation for existing layers, and public Javadocs.

## Out of scope

- Shapefile parsing, spatial indexing, simplification, asynchronous prefetch, editing, or mutable
  streaming updates.
- Arbitrary CRS transformation beyond the explicit G4-002 registry.

## Acceptance criteria

- Multipoint and multipart line/polygon values use packed primitive coordinates and part/ring
  offsets where appropriate, defensively copy inputs, preserve stable order, and validate cardinality,
  closure, holes, offsets, and finite values.
- The in-memory source exposes immutable metadata and applies envelope and approved attribute/query
  limits with deterministic source order.
- A map view derives a source-CRS query envelope from the visible viewport only when an explicit
  recognized transform exists, opens one bounded cursor, renders its results, and closes it on normal,
  empty, cancelled, and exceptional paths.
- Cursor iteration stops at cancellation and configured result/allocation limits, reporting the
  stable G4-001 diagnostic rather than retaining partial hidden state.
- Point, multipoint, single/multipart line, and polygon/multipolygon-with-holes all render through the
  real source-backed path.
- Existing `Layer`/`InMemoryLayer` consumers follow the G4-001 compatibility decision without an
  undocumented behavior change.
- Source replacement and view disposal close owned resources exactly once; caller-owned sources are
  not closed implicitly.

## Required tests

- API tests for every multipart shape, offsets, defensive copies, invalid rings/parts, and envelopes.
- Core source tests for filtering, stable order, limits, cancellation, early close, diagnostics, and
  ownership.
- AWT integration tests that assert query bounds, cursor closure, CRS failures, and offscreen output
  for each geometry family.
- Regression tests for existing in-memory layer rendering.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

A linear in-memory scan is sufficient here; G7 supplies the packed index. Do not add a format module
until it reads working records in its own vertical slice.

