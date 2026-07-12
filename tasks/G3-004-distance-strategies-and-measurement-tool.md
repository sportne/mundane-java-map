# G3-004 — Distance Strategies and Measurement Tool

Status: Proposed
Depends on: G3-001, G4-002
Gate: G3
Type: AFK

## Goal

Measure multi-segment paths interactively using explicit planar or recognized-geographic distance
semantics, and demonstrate the behavior in a runnable example.

## Context

G3-001 provides tool capture/lifecycle and G4-002 provides recognized CRS metadata and hardened
projection boundaries. `Coordinate` currently carries no units. Distance selection must therefore be
explicit: planar coordinates require a declared linear unit, while EPSG:4326 uses a documented
great-circle strategy rather than treating degrees as length.

## Scope

- Immutable distance result, linear-unit, strategy, and measurement-state contracts in
  `mundane-map-api`.
- Planar and geographic algorithms in `mundane-map-core`.
- Measurement tool input/overlay integration in `mundane-map-awt`.
- A working `examples/measurement-viewer`, tests, and affected public Javadocs.

## Out of scope

- Ellipsoidal geodesics, area measurement, elevation-aware distance, snapping, editing, persistence,
  or arbitrary CRS transformation.

## Acceptance criteria

- Planar distance uses Euclidean coordinate differences and an explicitly declared supported linear
  unit; it never guesses units from numeric ranges.
- Geographic distance is available only for the recognized geographic CRS and uses a documented
  haversine/great-circle earth radius, antimeridian-safe longitude differences, and meter results.
- Unsupported, missing, or mismatched CRS/unit combinations produce the stable G4 diagnostic shape
  rather than silently selecting a strategy.
- Primary clicks add vertices, pointer movement previews the next segment, double-click completes the
  path, Backspace removes the latest vertex, and Escape cancels through the G3 lifecycle.
- The overlay shows vertices, segments, current segment distance, and total distance with documented
  unit formatting and no mutation of map features.
- Zero-length segments, antipodal/near-antipodal points, antimeridian crossing, cancellation, tool
  replacement, and viewport navigation are deterministic and finite.
- The measurement viewer can switch between a declared planar example and recognized EPSG:4326 data
  and is constructible in a headless test.

## Required tests

- Core numeric tests for planar units, known great-circle distances, antimeridian, poles, identical
  points, and accumulated paths with stated tolerances.
- Tool-state tests for add, preview, undo, complete, cancel, capture, and navigation coexistence.
- Offscreen overlay tests and a headless measurement-viewer construction test.
- Diagnostic tests for missing, unknown, and mismatched CRS/unit metadata.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test :examples:measurement-viewer:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the algorithms JDK-only and document numeric tolerances. Do not add PROJ, JTS, or a custom
native geodesic library for this Level 1 capability.
