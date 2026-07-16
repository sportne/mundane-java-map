# mundane-java-map

`mundane-java-map` is a small Java 21 map-component library with toolkit-neutral geometry, sources,
symbols, interaction contracts, and bounded format readers. Its Level 1 runtime is JDK-only;
Swing and Java2D integration is isolated in `mundane-map-awt`.

The project is pre-1.0. Compatibility changes in `0.x` are intentional, documented migrations rather
than unrestricted churn. The deprecated `FeatureStyle` snapshot contract remains supported for the
first Level 1 `0.x` release; role-specific marker, line, and fill symbols are its replacement, and
`FeatureStyle` is intended for removal before `1.0.0`.

## Requirements

- Java 21 for consumers and compilation. A Java 17 or newer runtime can launch Gradle, which selects
  a Java 21 compiler toolchain.
- GraalVM Java 21 with `native-image` only for the separate Native Image smoke lane.

## Published modules

| Artifact | Responsibility |
| --- | --- |
| `mundane-map-api` | Immutable geometry, feature, symbol, interaction, CRS, source, diagnostic, cancellation, and limit contracts. |
| `mundane-map-core` | JDK-only viewport/projection, source, hit-testing, measurement, indexing, clipping, simplification, and symbol algorithms. |
| `mundane-map-awt` | Swing `MapView`, Java2D renderers, explicit symbol/decoder registries, interaction routing, and measurement UI. |
| `mundane-map-io-shapefile` | Bounded read-only SHP/SHX/DBF/CPG/PRJ feature sources. |
| `mundane-map-io-image` | Bounded PNG/JPEG metadata, world-file placement, requests, lifecycle, and caches through an explicit decoder boundary. |

The format modules contain no AWT types and do not discover implementations. Applications explicitly
construct their CRS, symbol-renderer, and encoded-raster-decoder registries. Callers close opened
feature/raster sources; owned `MapLayerBinding` instances transfer that responsibility to `MapView`,
whose `close()` cancels current work and releases its owned bindings. Public values make defensive
copies of mutable inputs.

## Build and verification

The normal local gate is:

```bash
./gradlew qualityGate --console=plain
```

Specialized evidence remains independent so normal development does not silently require platform
raster evidence, a corpus, profiling, publication staging, or a native toolchain:

```bash
./gradlew offlineRepositoryVerification --console=plain
./gradlew renderRegression --console=plain
./gradlew shapefileCorpus --console=plain
./gradlew performanceQuick --console=plain
./gradlew performanceEvidence --console=plain
./gradlew nativeSmoke --console=plain
./gradlew publicationDryRun --console=plain
```

`renderRegression` uses bounds, topology, tolerant color regions, ordering, clipping, and
interpolation invariants rather than byte-identical whole images. `performanceQuick` is a
noncanonical iteration lane; only `performanceEvidence` produces canonical performance evidence.
The offline lane verifies the complete normal gate from one isolated Maven-layout repository.

Release verification targets GraalVM Java 21 on Ubuntu 24.04 Linux x86_64; Native Image
compatibility remains unverified until the G8 Linux Native Image release-lane checkpoint is
approved. Windows, macOS, Linux AArch64, other distributions, and cross-platform compatibility are
unverified.

## Small example

```java
var marker = BuiltInMarkers.filledScreen(
    BuiltInMarker.CIRCLE, Rgba.rgb(28, 108, 184), 10.0, 1.0);
var cities = new InMemoryLayer(
    "cities",
    "Cities",
    List.of(new Feature(
        "bos",
        "Boston",
        new PointGeometry(new Coordinate(-71.0589, 42.3601)),
        Map.of("kind", "city"),
        marker)));

var map = new MapView(new WebMercatorProjection());
map.setLayers(List.of(cities));
map.fitToData(32.0);
// Close the containing view/window when finished; close() also releases owned source bindings.
```

This example uses the supported `Layer`/`InMemoryLayer` small-snapshot path. For bounded or lazy data,
open a `FeatureSource` or `RasterSource` and install it with `MapLayerBinding.borrowed*` or
`MapLayerBinding.owned*` according to the desired lifecycle.

## Level 1 format and CRS profile

The shapefile reader supports bounded two-dimensional null, point, multipoint, polyline, and polygon
records, multipart lines/polygons and holes, sequential SHP access, validated SHX indexed access,
bounded DBF attributes, CPG encoding selection, and retained/recognized PRJ metadata. Z/M shape
profiles and heuristic CRS transformation are unsupported.

The image reader supports bounded PNG and JPEG, axis-aligned or six-coefficient world-file affine
placement, window requests, nearest/bilinear rendering controls, opacity, cancellation, lifecycle,
and bounded decode/resample caching. `ImageIO` and packed-pixel conversion remain in the explicit AWT
decoder implementation.

Level 1 recognizes only explicitly registered EPSG:4326 and EPSG:3857 definitions and operations.
Unknown definitions are retained when available but are not guessed or transformed. GeoJSON,
GeoTIFF, DTED, SVG import, GeoPackage, MBTiles, GPX/KML, remote tiles, additional projections,
editing/persistence, and optional JTS/PROJ/SQLite/GDAL adapters remain Level 2 work.

## Examples

Five independent examples consume the published APIs without copying parsers or renderers:

```bash
./gradlew :examples:basic-viewer:run
./gradlew :examples:symbol-gallery:run
./gradlew :examples:measurement-viewer:run
./gradlew :examples:shapefile-viewer:run --args='<path.shp> [EPSG:4326|EPSG:3857]'
./gradlew :examples:raster-viewer:run --args='<image.png-or-jpeg> [--world-file EPSG:4326|EPSG:3857]'
```

The basic, symbol, and measurement examples are deterministic no-argument demonstrations. The two
format viewers are real file consumers, apply the format modules' limits, present structured
diagnostics under fixed non-path source identities, and transfer source ownership to their views.

## Design and roadmap

- [DESIGN.md](DESIGN.md) indexes the compact architecture and approved decisions.
- [ROADMAP.md](ROADMAP.md) separates the Level 1 release gates from Level 2 work.
- [tasks/](tasks/) contains implementation-sized vertical slices and exact validation commands.
