# G9-002 — Elevation raster layer

Status: Complete
Depends on: G9-001
Gate: G9
Type: AFK

## Goal

Render a synthetic elevation grid as a useful map layer through configurable color ramps and optional
hillshading.

## Context

G9-001 supplies numeric terrain samples. Level 1 supplies raster-layer rendering, viewport requests,
cancellation, opacity, and caching; this slice must reuse those facilities rather than treating terrain
as an encoded image format. Elevation bounds locate sample posts, whereas Level 1 raster bounds locate
pixel edges, so no public `RasterSource` adaptation is valid.

## Scope

`mundane-map-api` immutable color stops/ramps, no-data style, and bounded hillshade settings;
`mundane-map-core` stateless post-support planning and elevation-to-RGBA rasterization;
`mundane-map-awt` borrowed/owned elevation bindings using existing render options, request limits,
conversion, reports, cancellation, and ownership; `examples/elevation-viewer`; focused API/core/AWT/
example/architecture tests and rendering-regression scenarios. Support exact color interpolation,
sample-domain clipping, nearest/bilinear rendered-color resampling, layer opacity, and deterministic
first-order hillshading for recognized geographic or projected Level 1 CRS data.

## Out of scope

DTED or GeoTIFF reading, contour generation, 3D terrain, GPU rendering, automatic palette selection,
statistics/normalization, label placement, public position interpolation, reprojection/warping,
slope/aspect products, cast shadows, new elevation source/window contracts, and retained derived
caches. Do not make elevation a `RasterSource`, add a terrain renderer SPI, or create a format module.

## Acceptance criteria

- A synthetic grid renders through the normal map-layer path with a caller-supplied immutable color
  ramp of 2–256 finite strictly ordered stops, exact unit matching, deterministic clamp/interpolation,
  and explicit no-data treatment.
- A stateless core operation selects bounded post-support windows, clips output to exact sample-center
  bounds, reuses raster requests/resampling/accounting/pixels, and returns no partial result on limit
  failure or cancellation without adapting the source contract. Its plan is factory-only, unique
  post-window accounting retains G4 semantics, and tap counts remain checked derived work.
- Hillshading can be disabled or enabled with bounded azimuth, altitude, and exaggeration; projected-
  metre and geographic-degree spacing, grid edges, poles, and no-data neighborhoods have exact
  deterministic behavior.
- Borrowed and owned elevation bindings honor same-CRS validation, fit without sampling, viewport
  clipping, nearest/bilinear rendered-color resampling, opacity-zero short circuit, cancellation,
  reports, atomic publication, layer order, and G4 lifecycle/close semantics.
- The runnable synthetic viewer exposes ramp, hillshade, interpolation, and opacity behavior while
  clearly making no DTED-input claim.
- Java2D types remain confined to `mundane-map-awt`; API/core values remain immutable and JDK-only.
- No elevation-derived cache, background worker, discovery mechanism, format type, external
  dependency, or new diagnostic family is introduced.
- Rendering assertions use sampled colors, geometry, bounds, and tolerances rather than whole-image
  byte equality.

## Required tests

API tests for stop count/null colors/ordering/finite spans and lookup/defensive copies, unit matching,
clamp and channel rounding, no-data defaults, hillshade bounds, and immutable style updates. Core
tests for post-support
window selection, exact/partial/touching bounds, orientation, bilinear halo, density caps,
nearest/bilinear RGBA behavior, projected/geographic hillshade math, edges/poles/no-data, every
unique-window/output/byte limit boundary and derived-tap overflow, fixed central/one-sided extreme
slope operations, factory-only plan invariants, and cancellation stages. AWT tests for CRS and unit
attachment, complete eight-intermediate/four-published byte preflight boundaries, fit, opacity,
clipping, layer order,
non-interactivity, style/options replacement,
borrowed/owned cleanup, report recovery, and atomic cancellation. Headless viewer tests, architecture
tests, and tolerant rendering-regression regions/bounds/directions are required.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check :examples:elevation-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The approved design adds no cache: G9-007 must first show that direct bounded access is insufficient.
Rendered-color bilinear filtering is not the public numeric position-query policy owned by G9-005.
See `design/G9-elevation-and-dted.md` for the authoritative contracts and algorithms.

Implemented as the four immutable API style values, one stateless core planner/rasterizer, one direct
AWT elevation binding variant, and the synthetic viewer. The slice reuses Level 1 raster requests,
pixels, resampling, limits, conversion, reports, cancellation, and presentation without introducing a
`RasterSource` adapter, retained derived cache, format module, or DTED claim.
