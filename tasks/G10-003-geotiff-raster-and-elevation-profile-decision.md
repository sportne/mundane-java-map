# G10-003 — GeoTIFF raster and elevation profile decision

Status: Proposed
Depends on: G8-004, G9-001
Gate: G10
Type: HITL

## Goal

Approve one bounded Classic GeoTIFF profile and a pure-Java implementation strategy with explicit
raster and elevation entry points.

## Context

Level 1 provides bounded raster windows, affine placement, resampling, and CRS diagnostics; G9 provides
positioned eager terrain data, queries, and rendering. GeoTIFF must preserve the cell-area versus
sample-post distinction and remain separate from DTED even when both produce `ElevationSource`.

## Scope

Complete **G10 GeoTIFF profile and routing approval** for Classic TIFF byte orders and one IFD;
strips/tiles; None, PackBits, and Deflate; exact gray/RGB/alpha and signed/float elevation profiles;
GeoKeys, EPSG:4326/EPSG:3857, affine/cell-center rules, explicit caller elevation units and no-data;
snapshot ownership, limits, diagnostics, cancellation, and source lifecycle. Approve explicit
`openRaster`/`openElevation` selection in a future JDK-only `mundane-map-io-geotiff` module, and record
G10-030 through G10-038.

## Out of scope

Production code or module creation; BigTIFF, multiple IFDs/overviews, LZW/JPEG/predictors, palette/
YCbCr/CMYK, arbitrary CRS, writing, cloud-optimized range access, persistent caches, JNI/native codecs,
and TIFF or external-library types in public contracts.

## Acceptance criteria

- **G10 GeoTIFF profile and routing approval** records the closed container/tag/layout/compression,
  raster/elevation sample, GeoKey/CRS/georeference, no-data, limit, diagnostic, and precedence matrix.
- Caller-selected raster versus elevation openers must agree with `PixelIsArea` versus `PixelIsPoint`;
  no sample or metadata heuristic routes data, and DTED remains a separate reader.
- The approved module is a published Level 2 JDK-only runtime with a bounded byte snapshot and private
  decoders; the review rejects opaque ImageIO metadata/allocation behavior and defers GDAL/JNI without
  a demonstrated missing capability. Native Image remains unclaimed until executable evidence.
- G10-030 through G10-038 are ordered working slices for the first stripped raster, tiled/color
  completion, compression, affine placement, integer then floating elevation, hostile hardening,
  corpus/performance, and Native Image; no empty module is created.

## Required tests

No production tests. Review the hand-built valid/negative/limit/cancellation matrix, independent-writer
corpus plan, parser/source compile sketches, and the follow-up graph's render, query, publication/
consumer, performance, and Native Image evidence.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoTIFF profile and routing approval**. Approval chooses the strict pure-Java
profile, explicit semantic openers, and nine-card implementation graph. Rejection creates no module;
GeoTIFF remains Level 2.
