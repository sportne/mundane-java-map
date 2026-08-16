# G19-012 — Raster reprojection and warping

Status: Proposed
Depends on: G19-010
Gate: G19
Type: AFK

## Goal

Add a bounded JDK-only raster warp engine so georeferenced images and elevation grids can be rendered
and queried across supported coordinate systems.

## Context

Vector geometries can be projected, but raster layers generally require CRS identity or narrow
affine placement. Expert GeoTIFF, DTED, GeoPackage, and tile workflows need deterministic resampling.

## Scope

- Freeze nearest, bilinear, and one higher-quality resampling profile for imagery and elevation.
- Implement inverse-mapped tiled/windowed warping with nodata, alpha, and mask semantics.
- Define error tolerance, antimeridian/domain handling, memory/work limits, and cancellation.
- Integrate with raster query/render abstractions without AWT dependencies.
- Expose stable diagnostics for non-invertible or unavailable coordinate operations.

## Out of scope

- GPU acceleration, JNI codecs, or implicit network grid acquisition.

## Acceptance criteria

- Results match independent reference rasters within documented per-resampler tolerances.
- Large inputs are processed in bounded windows and cancellation releases resources.
- Nodata/mask/elevation interpolation behavior is explicit at edges and discontinuities.
- Failure is atomic and cannot publish a partially warped layer.

## Required tests

- Affine and nonlinear control rasters for every resampler and sample type.
- Domain seam, nodata, cancellation, overflow, and resource-limit tests.
- Rendering and elevation-query integration fixtures.

## Validation

Run `./gradlew :modules:mundane-map-core:check --console=plain`, rendering/performance lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
