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

- `RasterAffineTransform`, tagged raster-grid placement, and compatible raster metadata evolution in
  `mundane-map-api`
- Exact affine window/envelope planning in `mundane-map-core`
- Finite sidecar snapshot, strict parser, limits, placement mode, and diagnostics in
  `modules/mundane-map-io-image`
- True-parallelogram AWT placement, PNG/JPEG fixtures, viewer mode, and architecture tests

## Out of scope

- PRJ/WKT recognition beyond registered G4 CRS behavior
- GeoTIFF tags, ground-control points, nonlinear warping, and resampling-quality work from G6-003
- Automatic world-file fallback, directory scanning, `.prj`/CRS guessing, and sidecar priority rules

## Acceptance criteria

- World-file placement is explicitly requested. Direct sibling candidates are exactly lower/upper
  `.pngw`/`.pgw`, `.jpgw`/`.jgw`, `.jpegw`/`.jgw`, then `.wld`; mixed case is unsupported, same-file
  aliases collapse, and multiple distinct candidates fail without priority fallback.
- Files are bounded to 4,096 bytes and 256 bytes per line. Strict US-ASCII grammar accepts exactly six
  locale-independent decimals in physical order `A,D,B,E,C,F`, with one optional final line ending;
  controls, BOM, comments, blank/extra lines, and non-finite values fail stably. File/line limit
  excess uses `SOURCE_LIMIT_EXCEEDED`, not a duplicate parser diagnostic.
- The G6-001 six-value `ImageSourceLimits` constructor remains compatible with the new defaults; one
  full constructor, accessors, withers, equality, and defaults preserve all eight fields.
- Coefficients represent pixel-center-to-map coordinates, and raster corner bounds include the
  correct half-pixel expansion.
- North-up, negative pixel-height, rotated, and sheared transforms produce correct envelopes.
- A scaled finite determinant/inverse rejects singular or unrepresentable transforms without an
  arbitrary epsilon. Placement exposes its immutable kind payload, and a metadata factory derives
  a finite positive represented footprint/envelope from placement/dimensions so callers cannot create
  contradictory or translation-collapsed state.
- Geographic/projected CRS metadata is explicit; missing or unknown CRS remains unknown and is not
  guessed; MapView still requires the exact display CRS and never performs raster reprojection.
- Affine viewport planning first intersects the stored envelope, then power-of-two normalizes,
  inverse-clips a fixed four-corner polygon, returns a conservative strict source window, and leaves
  direct requests unmodified. Arithmetic failure is not treated as empty. Axis-aligned G4 edge/ULP
  behavior is unchanged.
- AWT draws the selected window as its true transformed parallelogram rather than the axis-aligned
  envelope. Fit, alpha, diagnostics, cancellation, and ownership stay on the existing raster path.
- `raster-viewer <image> --world-file EPSG:4326|EPSG:3857` explicitly loads and fits the sidecar;
  the existing normalized mode remains visibly non-georeferenced.
- New public placement/transform/metadata APIs have complete Javadocs, immutable value equality, and
  no mutable array/collection exposure.

## Required tests

- Sidecar seam tests for every candidate/case, same-file aliases, ambiguity, snapshot races, missing/
  malformed files, limits, cancellation, close, and primary/suppressed cleanup. Races prove pre-open
  replacement bytes are authoritative, no fallback occurs, and same-size mid-read mutation is not
  falsely claimed detectable.
- Parser tests for exact ASCII decimal/whitespace/line-ending grammar and byte/line minus/equal/plus-
  one limits, counting indentation/trailing SP/TAB but excluding LF/CRLF; affine tests for scaled
  inverse, centers, corners, positive represented footprint/envelope, translation collapse, overflow,
  and singularity.
- API compatibility/value tests cover old/new image-limit constructors, old metadata construction,
  placement-derived metadata, kind payload access, signed-zero canonicalization, equality, and
  defensive ownership.
- Core tests preserving every axis-aligned grid oracle plus affine full/partial/touching/clipped window
  cases, one-fringe bound, rotation/shear, huge dimensions, numeric normalization, thin positive
  areas, and explicit arithmetic failure.
- Integration tests for geographic/projected/unknown/mismatched CRS, true affine PNG/JPEG placement,
  fit-to-data, tolerant sampled regions, envelope exterior background, and cleanup.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check :examples:raster-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Parse with explicit ASCII and locale-independent numbers. Preserve the transform as an immutable
pixel-center value; do not reduce rotated/sheared input to an axis-aligned approximation. Do not run
publication, native, rendering-regression, corpus, or performance lanes in this task.
