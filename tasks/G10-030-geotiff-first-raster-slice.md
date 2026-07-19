# G10-030 — GeoTIFF first raster slice

Status: Proposed
Depends on: G10-003
Gate: G10
Type: AFK

## Goal

Publish a working JDK-only GeoTIFF adapter that reads, queries, and renders one bounded little-endian
uncompressed stripped grayscale raster.

## Context

G10-003 approves the closed Classic TIFF/GeoTIFF profile, explicit raster/elevation routing, stable
diagnostics, and nine serial implementation slices. G4 and G6 provide source, raster-window,
placement, resampling, and rendering contracts.

## Scope

Create `modules/mundane-map-io-geotiff` with both approved `GeoTiffFiles.openRaster(Path, ...)` and
defensive-copy `openRaster(byte[], ...)` entry points, immutable options and limits, a Classic
version-42 header/one-IFD parser, minimal GeoKey parsing, little-endian
uncompressed strips, unsigned eight-bit BlackIsZero, EPSG:4326 PixelIsArea scale/tiepoint placement,
strict window reads, lifecycle/cancellation, a minimal viewer, architecture inventory, publication
staging, and an offline consumer.

## Out of scope

Big-endian input, tiles, other photometric profiles, compression, ModelTransformation, elevation,
hostile-input closure, corpus/performance claims, and Native Image evidence.

## Acceptance criteria

- A hand-built little-endian stripped BlackIsZero fixture opens through `GeoTiffFiles.openRaster`,
  exposes canonical EPSG:4326 area placement, serves strict windows, and renders in the viewer.
- Path and byte-array opening produce equivalent raster metadata/pixels, and mutation of the caller's
  byte array after opening cannot affect the published source.
- The initial parser rejects unsupported routes/layouts with the stable G10-003 vocabulary and
  enforces the applicable snapshot, IFD, GeoKey, segment, pixel, working-byte, cancellation, and
  lifecycle boundaries before publication.
- The new module is AWT-free, JDK-only, directly registered, documented, published, and consumable
  from the staged repository without leaking TIFF implementation types.

## Required tests

Header/IFD/GeoKey unit tests, Path/byte-array parity and caller-array mutation isolation, exact and
one-over opening limits, strict-window and placement tests, source lifecycle/cancellation tests,
source-to-AWT render integration, architecture tests, viewer smoke, publication metadata, and
offline-consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The module must deliver this complete vertical slice when it is registered; do not create empty
future-layout or elevation scaffolding. Keep all decoding private and use packed primitive plans.
