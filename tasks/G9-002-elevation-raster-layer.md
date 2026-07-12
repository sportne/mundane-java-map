# G9-002 — Elevation raster layer

Status: Proposed
Depends on: G9-001
Gate: G9
Type: AFK

## Goal

Render a synthetic elevation grid as a useful map layer through configurable color ramps and optional
hillshading.

## Context

G9-001 supplies numeric terrain samples. Level 1 supplies raster-layer rendering, viewport requests,
cancellation, opacity, and caching; this slice must reuse those facilities rather than treating terrain
as an encoded image format.

## Scope

Toolkit-neutral elevation styling and request logic in API/core as needed, Java2D rendering in
`mundane-map-awt`, and a focused runnable or integration fixture. Support immutable ordered color
stops, deterministic no-data color/transparency, layer opacity, and optional hillshading with explicit
azimuth, altitude, and vertical-exaggeration settings.

## Out of scope

DTED or GeoTIFF reading, contour generation, 3D terrain, GPU rendering, automatic palette selection,
and label placement.

## Acceptance criteria

- A synthetic grid renders through the normal map-layer path with a caller-supplied immutable color
  ramp and explicit no-data treatment.
- Hillshading can be disabled or enabled with bounded, validated parameters; edges and no-data
  neighborhoods have documented deterministic behavior.
- Rendering honors viewport clipping, cancellation, opacity, and resource lifecycle inherited from
  the Level 1 raster path.
- Java2D types remain confined to `mundane-map-awt`; API/core values remain immutable and JDK-only.
- Rendering assertions use sampled colors, geometry, bounds, and tolerances rather than whole-image
  byte equality.

## Required tests

Unit tests for ramp interpolation, stop validation, no-data handling, and hillshade calculations;
AWT integration tests for layer rendering, opacity, cancellation, and non-pixel-perfect regression
checks.

## Validation

```bash
./gradlew :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the style usable by elevation grids from any source. Cache only derived data with explicit bounds
and invalidation; do not duplicate the underlying grid by default.

