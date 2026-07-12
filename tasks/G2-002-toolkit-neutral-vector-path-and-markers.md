# G2-002 — Toolkit-Neutral Vector Paths and Markers

Status: Proposed
Depends on: G2-001
Gate: G2
Type: AFK

## Goal

Render the first custom vector marker through the real map stack using an immutable toolkit-neutral
path model, then supply the complete Level 1 built-in marker set on that same path.

## Context

`CoordinateSequence` demonstrates packed primitive storage in `mundane-map-api`, while `MapView`
currently constructs Java2D shapes internally and always draws point features as circles. The
G2-001 profile defines how symbols replace or adapt `FeatureStyle`; this task implements that
decision without introducing SVG parsing or Java2D types outside `mundane-map-awt`.

## Scope

- Public vector-path, marker-symbol, and built-in-marker values in `mundane-map-api`.
- JDK-only built-in path construction in `mundane-map-core` where it is not a public value concern.
- Explicit vector-path conversion and marker rendering in `mundane-map-awt`.
- API, core, and offscreen AWT tests plus affected public Javadocs.

## Out of scope

- Raster icons, catalogs, composite placement, line endpoints, hatch fills, and SVG import.
- General path boolean operations, stroking algorithms, or text glyph conversion.

## Acceptance criteria

- The path model represents move, line, quadratic, cubic, and close commands with packed primitive
  storage, defensive copies, stable equality, and no toolkit dependencies.
- Construction rejects non-finite coordinates, missing initial moves, incomplete command operands,
  and invalid close/subpath sequences with stable diagnostics or documented exceptions.
- AWT conversion preserves subpaths, quadratic/cubic control points, close commands, and the
  approved fill rule without exposing `Path2D` through public API contracts.
- Circle, square, triangle, diamond, cross, X, star, and arrow built-ins use normalized paths and
  render through the same explicit vector-marker renderer.
- At least one point feature using each marker is rendered through `MapView`; the legacy circle
  appearance remains available through the G2-001 migration path.
- Public additions have complete Javadocs, immutable values, and tested defensive array handling.

## Required tests

- API unit tests for every command, multi-subpath construction, equality, copies, and invalid input.
- AWT conversion tests that inspect path segments and bounds for line, quadratic, cubic, and close.
- Offscreen map tests for all eight built-ins using non-background region and approximate-bounds
  assertions.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Store command kinds and ordinates in compact primitive arrays where practical. Conversion may create
Java2D objects per render until performance evidence justifies caching; do not add a speculative
native path library.

