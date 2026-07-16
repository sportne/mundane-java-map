# Changelog

All notable user-visible changes are recorded here. The project is pre-1.0; compatibility changes
in `0.x` releases are documented with a migration path.

## [0.1.0] — Unreleased

First useful Level 1 release candidate.

### Added

- Immutable toolkit-neutral geometry, features, layers, symbols, vector paths, CRS metadata, source
  queries, diagnostics, cancellation, and parser-limit contracts.
- Swing and Java2D map rendering with explicit renderer and raster-decoder registration, viewport
  navigation, tools, symbol-aware hit testing, hover/selection overlays, and planar/geographic
  measurement.
- Read-only bounded shapefile sources for two-dimensional null, point, multipoint, polyline, and
  polygon records, including multipart geometry, holes, SHX, DBF, CPG, and retained/recognized PRJ.
- Bounded PNG/JPEG raster sources with world-file affine placement, window requests, nearest and
  bilinear rendering, opacity, cancellation, lifecycle, and decode/resample caching.
- Packed spatial indexing, viewport clipping, scale-aware simplification, and an evidence-retained
  private vector-template cache.
- Runnable basic, symbol-gallery, measurement, shapefile, and raster examples, plus independent
  corpus, rendering-regression, performance, Native Image, publication, and downstream-consumer
  verification lanes.

### Migration and compatibility

- `FeatureStyle` remains supported but is deprecated. New code should use role-specific immutable
  marker, line, and fill symbols; `FeatureStyle` is intended for removal before `1.0.0`.
- All published artifacts require Java 21. Production runtime modules remain JDK-only, and Swing/
  Java2D types are confined to `mundane-map-awt`.
- Callers explicitly construct registries and close owned feature/raster sources. There is no
  reflection-based provider discovery or automatic resource scanning.

### Limits and non-claims

- The Level 1 CRS profile recognizes only explicitly registered EPSG:4326 and EPSG:3857 operations.
  Unknown definitions may be retained but are not guessed or transformed; raster reprojection and
  general coordinate-operation discovery are unsupported.
- Shapefile support is two-dimensional and read-only. Z/M profiles, editing, repair, and unrestricted
  allocation are unsupported. Binary and image inputs are governed by explicit caller-visible
  limits and stable structured diagnostics.
- PNG/JPEG decoding uses the explicitly registered JDK `ImageIO` boundary in `mundane-map-awt`.
  GeoJSON, GeoTIFF, DTED, SVG import, GeoPackage, MBTiles, GPX/KML, remote tiles, editing,
  persistence, vector export, and optional JTS/PROJ/SQLite/GDAL adapters remain Level 2 work.
- Performance evidence is scenario- and environment-specific. It establishes correctness and the
  retained Level 1 optimization decisions, not portable latency or throughput guarantees.
- The intended Native Image claim is limited to the separately recorded Ubuntu 24.04 Linux x86_64
  GraalVM Java 21 build-and-run lane. This unreleased candidate does not claim that verification
  until exact-candidate CI evidence is recorded; Windows, macOS, Linux AArch64, and other GraalVM
  distributions remain unverified.

[0.1.0]: https://github.com/sportne/mundane-java-map/releases/tag/v0.1.0
