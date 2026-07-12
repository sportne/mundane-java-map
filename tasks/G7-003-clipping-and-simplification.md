# G7-003 — Clipping and simplification

Status: Proposed
Depends on: G7-002
Gate: G7
Type: AFK

## Goal

Reduce offscreen and subpixel vector work with viewport clipping and scale-aware simplification while
preserving visible line and polygon topology.

## Context

The spatial index limits candidate features. G7-001 evidence identifies remaining projection/path
cost, and unsimplified production geometry remains the fallback and correctness reference.

## Scope

- JDK-only clipping and simplification algorithms in `modules/mundane-map-core`
- Explicit render/query integration in `modules/mundane-map-awt`
- Correctness fixtures and performance scenarios

## Out of scope

- Destructive source-geometry mutation, topology repair, general overlay operations, and native/JTS
  acceleration
- Format-specific simplification or persistent precomputed tile pyramids

## Acceptance criteria

- Lines and polygon rings are clipped to a viewport expanded by the documented stroke/symbol margin;
  rejected features perform no path construction.
- Simplification tolerance derives explicitly from viewport scale/pixel tolerance and never modifies
  source coordinate storage.
- Line endpoints needed for continuity/endpoint symbols are retained, polygon rings remain closed,
  shells and holes remain associated, and winding behavior remains valid.
- Degenerate output, holes collapsed outside their shells, antimeridian/domain boundaries, and
  numerically unsafe geometry fall back to unsimplified rendering or a documented safe rejection.
- Results are deterministic for the same geometry, viewport, and tolerance.
- Render selection/hit-test semantics remain based on authoritative geometry unless a separately
  documented tolerance-safe path is proven equivalent.
- Evidence reports vertices projected/path-built and render time before/after across zoom levels,
  including the overhead for small inputs.
- No public mutable buffers or third-party production dependency is introduced.

## Required tests

- Exact clipping tests for horizontal/vertical/diagonal lines, boundary touches, multipart lines,
  shells, holes, and disjoint polygons.
- Simplification tests across zero, subpixel, and large tolerances with closure/topology assertions.
- Fixed-seed comparison tests that sample inside/outside regions and exercise safe fallback cases.
- Offscreen rendering regression tests using geometry/bounds/tolerant samples, plus performance
  scenarios at multiple zoom levels.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-performance-tests:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer compact established algorithms with explicit numeric guards. Correct topology and stable
fallback take priority over vertex-count reduction.
