# G10-035 — GeoTIFF floating elevation and no-data

Status: Proposed
Depends on: G10-034
Gate: G10
Type: AFK

## Goal

Complete the approved GeoTIFF elevation sample matrix with floating-point values, explicit no-data,
hillshading, and tiled/compressed parity.

## Context

G10-034 delivers eager signed-integer terrain. G10-003 additionally permits finite Float32/Float64
samples and the bounded GDAL no-data compatibility tag.

## Scope

Add Float32/Float64 decoding, finite exact-type and lowercase `nan` no-data policies, masked-sample
behavior, non-finite validation, positive-zero normalization, all approved strip/tile and compression
combinations, color ramps, optional bounded hillshading, and query/render parity.

## Out of scope

Unsigned/complex/multiband elevation, scale/offset, vertical CRS, arbitrary GDAL metadata, raster
no-data, interpolation beyond G9, or new terrain formats.

## Acceptance criteria

- Valid Float32/Float64 fixtures preserve finite values, mask only under the declared finite/`nan`
  policy, normalize negative zero, and query/render consistently with integer terrain.
- Malformed no-data grammar, Infinity, unmasked NaN, and wrong-route no-data fail with exact tag or
  sample diagnostics and no partial source.
- None/PackBits/Deflate and strip/tile elevation variants produce equivalent color and hillshade
  behavior within the existing tolerant rendering policy.

## Required tests

Floating sample/byte-order tests, finite and `nan` no-data matrices, non-finite diagnostics,
strip/tile/compression parity, no-data query boundaries, color-ramp and hillshade rendering tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-awt:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Tag 42113 is the only accepted compatibility extension and remains private format metadata.
