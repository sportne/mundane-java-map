# G10-031 — GeoTIFF raster layout and color completion

Status: Complete
Depends on: G10-030
Gate: G10
Type: AFK

## Goal

Complete the approved uncompressed GeoTIFF raster layouts, byte orders, color profiles, and Level 1
CRS paths through real windowed rendering.

## Context

G10-030 supplies the module, facade, parser, stripped grayscale source, publication path, and viewer.
G10-003 fixes the remaining exact uncompressed raster matrix.

## Scope

Add big-endian header/value/sample decoding, validated row-major uncompressed tiles, WhiteIsZero,
RGB, and unassociated-alpha profiles, EPSG:3857 GeoKeys, exact edge-strip/edge-tile planning, and
segment-selective strict-window reads. Extend the viewer and tolerant render integration across the
new combinations.

## Out of scope

PackBits/Deflate, ModelTransformation, elevation, unsupported photometric/sample profiles, persistent
caches, and general TIFF metadata.

## Acceptance criteria

- Both TIFF byte orders and every approved unsigned eight-bit gray/RGB/alpha profile decode to exact
  immutable RGBA values from strips and tiles.
- Window reads decode each intersecting segment at most once in natural order, handle edge segments
  exactly, and retain deterministic failure/reuse behavior.
- EPSG:4326 and EPSG:3857 fixtures query and render with their approved placement and no implicit
  reprojection.

## Required tests

Byte-order and inline/out-of-line value tests, strip/tile count and edge tests, every approved color
mapping, segment-selection/window tests, CRS/placement integration, malformed segment declarations,
and tolerant render tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep one chunky decoder path and the closed sample matrix. Do not introduce a codec registry or TIFF
metadata model.
