# G6-002 — World-file affine georeferencing

Status: Proposed
Depends on: G6-001
Gate: G6
Type: AFK

## Goal

Place PNG/JPEG rasters in geographic or projected map coordinates using a validated six-coefficient
world-file affine transform.

## Context

G6-001 supplies bounded image sources and decoding. G4-002 supplies explicit CRS metadata and
transform boundaries. World-file coordinates locate pixel centers and must not be treated as
corner coordinates.

## Scope

- World-file discovery/parsing and affine metadata in `modules/mundane-map-io-image`
- Toolkit-neutral affine placement in raster requests/rendering
- PNG/JPEG world-file fixtures and raster-viewer georeferencing

## Out of scope

- PRJ/WKT recognition beyond registered G4 CRS behavior
- GeoTIFF tags, ground-control points, nonlinear warping, and resampling-quality work from G6-003

## Acceptance criteria

- The supported deterministic filename search covers the approved long/short PNG and JPEG variants
  (`.pngw`/`.pgw`, `.jpgw`/`.jgw`, `.jpegw`) plus `.wld`, including a documented case policy.
- Exactly six finite, locale-independent decimal values are read in world-file order
  `A, D, B, E, C, F`; extra/missing text and byte/line limits produce stable diagnostics.
- Coefficients represent pixel-center-to-map coordinates, and raster corner bounds include the
  correct half-pixel expansion.
- North-up, negative pixel-height, rotated, and sheared transforms produce correct envelopes.
- Singular or numerically unusable transforms, non-finite coefficients, overflow, and contradictory
  bounds/CRS metadata fail predictably.
- Geographic/projected CRS metadata is explicit; missing or unknown CRS remains unknown and is not
  guessed from coefficient magnitudes.
- The viewer fits and places georeferenced fixtures through the normal raster-source path.

## Required tests

- Parser tests for all supported filename variants, decimal formats, whitespace, line counts, case
  policy, oversized text, and malformed/non-finite values.
- Affine tests for identity-like, north-up, rotated, sheared, and singular transforms with pixel
  center/corner assertions.
- Integration tests for geographic and projected fixtures, unknown CRS, fit-to-data, and rendering.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :examples:raster-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Parse with explicit charset and locale-independent numbers. Preserve the original transform as an
immutable value; do not reduce rotated/sheared input to an axis-aligned approximation.
