# G16-006 — Explicit global raster wrap

Status: Proposed
Depends on: G16-003, G6-004
Gate: G16
Type: AFK

## Goal

Render an explicitly declared, compatible global raster continuously across the dateline while
local and affine-incompatible rasters remain single, bounded layers.

## Context

G6 supplies bounded PNG/JPEG sources, world-file affine placement, windows, interpolation, and
caches. G16-003 supplies explicit display/layer wrap configuration and canonical interval planning.
Future HTTP XYZ and MBTiles tasks need the same canonical column arithmetic but must not be
preimplemented here.

## Scope

Validate the approved canonical-period raster extent/affine profile; plan canonical raster windows;
reuse equal detached reads across translated copies; render nearest/bilinear/opacity behavior through
one, seam, and multi-world viewports; preserve cache/lifecycle/cancellation diagnostics; and add
dependency-neutral canonical tile-column modulo tests/helpers only where consumed by this working
raster slice. Update the raster viewer with an explicit global-repeat option and local comparison.

## Out of scope

Repeating partial, rotated, sheared, or incompatible rasters; raster reprojection/warping; remote
networking; SQLite/MBTiles modules; credentials; vertical tile wrap; or automatic global detection.

## Acceptance criteria

- A supported global PNG/JPEG raster repeats without a seam gap through ordinary `RasterSource`
  rendering and obeys interpolation and opacity controls.
- Identical canonical requests share existing bounded detached results where safe; visual copies do
  not multiply decode ownership or leak resources.
- Local/default and affine-incompatible rasters do not repeat and receive the approved stable
  validation result when repetition is requested.
- Canonical tile-column math is bounded, overflow-safe, and ready for later G10 consumers without
  creating a format module or network path.

## Required tests

Global extent/tolerance validation; seam/multi-copy nearest and bilinear rendering; opacity;
canonical window reuse; copy limits; cancellation; corruption/close/cache invalidation; local and
rotated/sheared rejection; tile-column boundaries; viewer and tolerant rendering regression.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :examples:raster-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The layer opt-in is authoritative. Do not infer global repetition from EPSG:3857 alone or add an
empty HTTP, MBTiles, or GeoPackage module.
