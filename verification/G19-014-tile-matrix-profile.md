# G19-014 OGC TileMatrixSet algorithm profile

Reviewed: 2026-08-16

## Standards and evidence

The frozen model follows **OGC Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0**, OGC
17-083r4. Authoritative examples are the OGC schema repository's `WebMercatorQuad.json`,
`WorldCRS84Quad.json`, and variable-width examples. The implementation is encoding-independent and
does not parse those JSON/XML documents.

- Standard: <https://docs.ogc.org/is/17-083r4/17-083r4.html>
- Official examples: <https://schemas.opengis.net/tms/2.0/json/examples/tilematrixset/>

Tests pin published WebMercatorQuad levels 0–3 and WorldCRS84Quad levels 0–2, including identifiers,
scale denominators, cell sizes, point of origin, 256-cell tiles, and matrix dimensions. Synthetic
fixtures isolate YX ordered axes, bottom-left origin, non-square tile dimensions, and variable-width
row coalescence. HTTP XYZ and GeoPackage module fixtures prove the neutral model matches existing
adapter conventions without changing their production support claims.

## Frozen semantics

- Coordinates and returned envelopes use the library CRS x/y presentation. `pointOfOrigin` retains
  encoded ordinate order; `TileMatrixAxisOrder` performs the only explicit normalization.
- Tile rows and columns are zero-based. Normal coverage uses a half-open east/south selection at
  shared boundaries; a point exactly on the complete set maximum is assigned to the last tile.
- `TOP_LEFT` rows increase downward and `BOTTOM_LEFT` rows increase upward. Variable-width physical
  tiles span `coalesce` nominal columns, and each coalescence factor must divide the nominal width.
- Scale selection is explicit: nearest (finer on a tie), closest coarser-or-equal, or closest
  finer-or-equal. A one-sided request with no eligible matrix fails rather than clamping silently.
- Ordinary envelopes clip but never wrap. A seam-crossing request uses
  `coverageAcrossHorizontalSeam`, passes `west > east`, and returns at most two intersections with
  duplicate low-resolution tiles removed in stable first-part order.

## Limits and diagnostics

One set retains at most 64 matrices. A matrix accepts at most 4,294,967,296 nominal rows/columns,
65,536 cells per tile axis, and 1,024 variable-width row bands. Coalescence is capped at 1,048,576.
Coverage defaults to 100,000 materialized addresses and has a hard ceiling of 1,000,000. Enumeration
checks rows and columns prospectively and publishes only a complete immutable result.

Stable failures cover unknown matrices, invalid/unavailable scale selection, invalid index or world
coordinates, invalid seam ranges, and coverage limits. Context is bounded, immutable, and ordered.

## Compatibility

`CommonTileMatrixSets.webMercatorQuad` and `worldCrs84Quad` expose reviewed levels through 24.
`legacyXyz` remains capped at zoom 22. `xyzEnvelope` deliberately uses the existing XYZ arithmetic
order so its double-valued bounds remain exactly equal to `XyzTileRegion.bounds()` where profiles
coincide.
