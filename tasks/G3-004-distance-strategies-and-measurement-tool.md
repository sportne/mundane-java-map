# G3-004 — Distance Strategies and Measurement Tool

Status: Proposed
Depends on: G3-003, G4-002
Gate: G3
Type: AFK

## Goal

Measure multi-segment paths interactively using explicit planar or recognized-geographic distance
semantics, and demonstrate the behavior in a runnable example.

## Context

G3-001 provides tool lifecycle/routing, G3-003 fixes hover/selection paint ordering, and G4-002
provides exact map/display CRS definitions plus optional coordinate conversion. `Coordinate` carries
no independent unit. Distance selection must therefore be explicit: a strategy is bound to the
view's exact map CRS, projected axes declare metre units through `CrsUnit`, and EPSG:4326 uses a
documented great-circle strategy rather than treating degrees as length.

## Scope

- Immutable metre-distance result, CRS-bound strategy, measurement-state, and bounded semantic
  tool-command contracts in `mundane-map-api`.
- Planar/geographic algorithms and command routing in `mundane-map-core`.
- Measurement tool input, focused Backspace/Escape routing, and the concrete final overlay pass in
  `mundane-map-awt`.
- A working `examples/measurement-viewer`, tests, and affected public Javadocs.

## Out of scope

- Ellipsoidal geodesics, area measurement, elevation-aware distance, snapping, editing, persistence,
  or arbitrary CRS transformation.

## Acceptance criteria

- Planar distance uses Euclidean coordinate differences from a recognized projected CRS whose axes
  explicitly declare metres through G4; it never guesses units from numeric ranges or adds a second
  unit model.
- Geographic distance is available only for the recognized geographic CRS and uses a documented
  haversine/great-circle earth radius, antimeridian-safe longitude differences, and meter results.
- A strategy/view CRS mismatch and out-of-domain strategy input use the stable bounded G4 CRS
  problem shape; missing/unknown source metadata remains owned by source binding rather than being
  duplicated in measurement.
- Primary clicks add vertices, pointer movement previews the next segment, double-click completes the
  path, Backspace removes the latest vertex, and Escape cancels through the G3 lifecycle.
- The overlay shows vertices, segments, the current segment in every applicable phase, and a live
  total that includes preview distance, with documented unit formatting and no mutation of map
  features.
- Zero-length segments, antipodal/near-antipodal points, antimeridian crossing, cancellation, tool
  replacement, and viewport navigation are deterministic and finite.
- Missing optional map coordinates never add data or emit warnings, qualifying moves/clicks remain
  consumed, and the click-based tool never captures; primary drag and wheel navigation still pass.
- Source, hover, selection, and measurement paint in that order; measurement state has one tool owner
  and does not create synthetic features or participate in hit testing.
- The measurement viewer can switch between a declared planar example and recognized EPSG:4326 data
  and is constructible in a headless test.

## Required tests

- Core numeric tests for projected-metre validation, known great-circle distances, antimeridian,
  poles, identical points, and accumulated paths with stated tolerances.
- Router/tool-state tests for semantic Backspace, lifecycle Escape with no tool and empty/non-empty
  measurement, add, preview, undo, complete, cancel, missing coordinates, single-view tool ownership,
  proven absence of capture, and navigation coexistence.
- Offscreen fixed-order overlay/current-segment/live-total tests and a headless two-CRS
  measurement-viewer construction test.
- Diagnostic tests for exact strategy/view mismatch and coordinate-domain failure.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test :examples:measurement-viewer:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the algorithms JDK-only and document numeric tolerances. Do not add PROJ, JTS, or a custom
native geodesic library for this Level 1 capability.
