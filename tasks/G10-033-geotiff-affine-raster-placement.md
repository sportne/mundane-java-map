# G10-033 — GeoTIFF affine raster placement

Status: Proposed
Depends on: G10-032
Gate: G10
Type: AFK

## Goal

Render approved rotated and sheared PixelIsArea GeoTIFF rasters with exact affine placement and
tolerant regression evidence.

## Context

G10-032 supplies the complete compressed raster path. G10-003 permits one finite invertible 2D
`ModelTransformation` form for rasters and defines the cell-corner to cell-center conversion.

## Scope

Parse and validate the 16-DOUBLE ModelTransformation alternative, reject conflicts/perspective/Z
coupling/singular placement, construct the existing center-based affine raster placement, preserve
outer-corner metadata bounds, and extend the viewer and `renderRegression` across rotated/sheared
EPSG:4326 and EPSG:3857 cases.

## Out of scope

Ground-control points, nonlinear warping, arbitrary CRS, elevation affine transforms, coordinate
repair, raster reprojection, or new rendering comparison infrastructure.

## Acceptance criteria

- Valid finite affine fixtures render at the approved center/corner positions with correct outer
  bounds in both recognized CRSs.
- Conflicting, non-finite, perspective, Z-coupled, singular, and collapsed transforms produce the
  exact stable georeference/profile diagnostics.
- Regression assertions prove placement, geometry bounds, and tolerant color behavior without
  cross-platform pixel identity.

## Required tests

Affine parsing and matrix-shape tests, area center/corner conversion, metadata bounds, invalid
transform diagnostics, CRS mismatch, viewer smoke, and tolerant rendering regression tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-awt:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Reuse the G6 affine rendering contract; do not add a GeoTIFF-specific transform to public map APIs.
