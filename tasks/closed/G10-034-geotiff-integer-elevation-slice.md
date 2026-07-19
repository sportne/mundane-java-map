# G10-034 — GeoTIFF integer elevation slice

Status: Complete
Depends on: G10-033, G9-002, G9-005
Gate: G10
Type: AFK

## Goal

Open, query, colorize, and render bounded signed-integer PixelIsPoint GeoTIFF terrain through the
format-neutral elevation model.

## Context

G10-033 completes raster placement; G9-002 and G9-005 provide elevation rendering and explicit query
policies. G10-003 keeps GeoTIFF separate from DTED and requires caller-selected units and sample-post
semantics.

## Scope

Implement both approved `GeoTiffFiles.openElevation(Path, ...)` and defensive-copy
`openElevation(byte[], ...)` entry points for one-band signed Int16 and Int32 samples, eager segment
decode into `PackedElevationGrid`, caller-declared elevation units, scale/tiepoint PixelIsPoint
placement, exact nearest/bilinear position queries, lifecycle/cancellation/accounting, color-ramp
rendering, hillshade-ready source behavior, and an elevation viewer mode.

## Out of scope

Float samples, GDAL no-data, rotated/sheared elevation, vertical CRS/unit inference, DTED parsing,
contours, or 3D terrain.

## Acceptance criteria

- Signed Int16/Int32 fixtures in all existing segment/compression variants open eagerly with exact
  post bounds, explicit units, and values, then query and render through G9 behavior.
- Path and byte-array opening produce equivalent elevation metadata/values, and mutation of the
  caller's byte array after opening cannot affect the published source.
- PixelIsArea/elevation and PixelIsPoint/raster route mismatches fail before sample allocation; no
  metadata or value heuristic chooses the route.
- Prospective temporary/final storage accounting, post-copy cancellation arbitration, and cleanup
  publish no partial elevation source.

## Required tests

Signed sample/byte-order tests, Path/byte-array parity and caller-array mutation isolation, route
mismatch, unit and sample-bound tests, nearest/bilinear queries, allocation and cancellation
checkpoints, color-ramp rendering, viewer smoke, and lifecycle cleanup.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Treat terrain as sampled numeric data, not an image. The returned elevation source must retain no
encoded snapshot or TIFF plan after successful eager decode.
