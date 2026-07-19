# G11-012 — Same-CRS snap resolver slice

Status: Proposed
Depends on: G11-010
Gate: G11
Type: AFK

## Goal

Resolve a pointer position to a bounded deterministic vertex or segment target from explicitly
captured same-CRS reference snapshots and display transforms.

## Context

G11-001 fixes immutable snap references, inclusive logical-pixel tolerance, exact forward/reverse CRS
operations, geometry indexes, cancellation, limits, exclusions, and tie precedence.

## Scope

Add toolkit-neutral snap values to `mundane-map-api`, implement the linear bounded resolver in
`mundane-map-core`, integrate immutable reference capture and visible preview data with the point-edit
viewer, and cover every Level 1 geometry family.

## Out of scope

Retained spatial indexes, cross-CRS reference sets, live source cursors, grids, intersections,
midpoints, angular snapping, topology, or committing a point edit from pointer events.

## Acceptance criteria

- Vertices and non-zero segments are visited in declared layer/feature/geometry order without
  bridging parts or duplicating a closed-ring endpoint.
- Inclusive distance, vertex-before-segment, reverse reference priority, and ascending semantic
  geometry-index ties reproduce the approved winner exactly.
- Limits, cancellation, non-representable coordinates, CRS mismatches, exclusions, and no-candidate
  results use the approved problem/result policy without returning partial winners.
- A viewer scenario displays snapped and unsnapped preview positions from one captured reference set.

## Required tests

All geometry candidate/index cases, holes/parts/repeated endpoints/zero-length segments, exact
tolerance and ties, transform failures, cancellation polling, limit boundaries, exclusions,
defensive copies, and preview integration.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check :examples:point-edit-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The first resolver is deliberately a bounded linear scan. A retained index requires separate measured
evidence and must not be introduced in this task.
