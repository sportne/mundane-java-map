# G16-003 — Periodic display and wrapped point-source slice

Status: Complete
Depends on: G16-002
Gate: G16
Type: AFK

## Goal

Render an explicitly repeating point `FeatureSource` through ordinary `MapView` queries on both
sides of the dateline while preserving the same logical feature identity.

## Context

G16-002 proves the approved periodic math in one dense example. G4 sources expose non-wrapping
envelopes and bounded sequential cursors; G3 interaction and G11 portrayal/labels rely on stable
feature IDs. G16 must add repetition above the source/CRS boundary rather than change those contracts.

## Scope

Add the approved immutable wrap configuration and binding opt-in with Javadocs; keep existing
constructors/defaults non-wrapped; implement canonical interval planning, full-world collapse,
aggregate accounting, cancellation, stable report merging, ID deduplication, checked copy
translation, and deterministic point marker/label rendering through in-memory and shapefile source
fixtures. Add a runnable or existing-viewer mode that demonstrates mixed repeating and local layers.

## Out of scope

Lines/polygons crossing the seam, wrapped hit/edit behavior, raster repetition, automatic CRS or
extent inference, source API changes, or tile formats.

## Acceptance criteria

- One explicit global point binding renders all intersecting copies across a seam and a multi-world
  viewport; a default or local binding renders once.
- A seam viewport issues the minimum unique canonical queries, deduplicates stable IDs, and paints in
  source-record then ascending-copy order.
- Aggregate source limits, cancellation, diagnostics, close, label budgets, and mixed-layer ordering
  remain deterministic and all-or-nothing.
- Existing non-wrapped rendering, source reports, fit, and public constructors remain compatible.

## Required tests

Canonicalization/copy-index arithmetic; one/two/full-world plans; seam endpoint deduplication;
ordering; query accounting; cancellation; point marker/label copies; mixed local/global bindings;
limits, diagnostics, lifecycle, API Javadocs, and architecture boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew :modules:mundane-map-architecture-tests:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Feature identity is logical; copy index is paint-scoped state. Do not put presentation copies into
attributes, workspaces, source records, registries, or format modules.

Completion record (2026-07-22): `MapView` now owns an optional checked horizontal profile and
`MapLayerBinding` owns an explicit pre-attachment `NONE`/`REPEAT_X` mode. The first ordinary source
slice opens one or two canonical queries (or one full-world query), deduplicates stable IDs, applies
aggregate limits, and renders point/multipoint marker and label copies with canonical geometry and
paint-scoped viewport offsets. The basic viewer's `--world-wrap` mode demonstrates mixed local and
repeating content; in-memory, hand-built shapefile, cancellation, hostile-limit, lifecycle, and
compatibility tests exercise the real stack.
