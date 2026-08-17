# G19-014 — OGC tile-matrix-set algorithms

Status: Complete
Depends on: G19-010
Gate: G19
Type: AFK

## Goal

Replace Web-Mercator/256-pixel assumptions in reusable core algorithms with a bounded OGC
TileMatrixSet-compatible model while preserving XYZ convenience APIs.

## Context

WMTS, OGC API Tiles, GeoPackage, and non-Web-Mercator pyramids require explicit matrix dimensions,
origins, axes, scale denominators, tile sizes, and variable-width rows.

## Scope

- Freeze the supported OGC TileMatrixSet 2.0 concepts and JSON/XML-independent value model.
- Implement tile/world conversion, coverage enumeration, clipping, and scale selection.
- Support top-left/bottom-left conventions, non-square tiles, and variable matrix widths.
- Prospectively bound row/column ranges and tile enumeration.
- Retain exact XYZ/Web-Mercator adapters and compatibility tests.

## Out of scope

- Network service discovery or format-specific metadata parsing.

## Acceptance criteria

- Published OGC well-known scale-set examples reproduce expected indices/envelopes.
- Numeric/domain errors produce stable diagnostics without wraparound or excessive enumeration.
- Existing XYZ behavior is byte-for-byte stable where the models coincide.

## Required tests

- Multiple CRS/origin/tile-size/variable-width matrices from authoritative examples.
- Boundary, antimeridian, overflow, and maximum-coverage tests.
- Cross-module GeoPackage and HTTP-tile integration fixtures.

## Validation

Run `./gradlew :modules:mundane-map-core:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.

Completed 2026-08-16 with a bounded encoding-independent OGC TileMatrixSet 2.0 value model,
coordinate/envelope/scale/coverage algorithms, explicit seam traversal, variable-width rows,
WebMercatorQuad and WorldCRS84Quad constructors, exact legacy XYZ bounds, stable diagnostics, and
HTTP XYZ plus GeoPackage interoperability fixtures. The frozen evidence and limits are recorded in
`verification/G19-014-tile-matrix-profile.md`.
