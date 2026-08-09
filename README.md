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
| `mundane-map-vaadin` | Optional Flow component with a bundled local Canvas renderer, browser-local navigation and label measurement, server-resolved portrayals, expiring catalog-icon resources, serialized common feature-source/CRS bindings, and acknowledged detached vector capture. |
| `mundane-map-io-shapefile` | Bounded read-only SHP/SHX/DBF/CPG/PRJ feature sources. |
| `mundane-map-io-image` | Bounded PNG/JPEG metadata, world-file placement, requests, lifecycle, and caches through an explicit decoder boundary. |
| `mundane-map-io-http-tiles` | Bounded JVM-only fixed-host HTTP XYZ acquisition into detached PNG/JPEG raster snapshots. |
| `mundane-map-io-dted` | Bounded Level 2 DTED elevation sources. |
| `mundane-map-io-geotiff` | Bounded JDK-only Classic GeoTIFF raster and elevation sources. |
| `mundane-map-io-svg` | Secure Level 2 static SVG-symbol subset import. |
| `mundane-map-io-se` | Secure Level 2 OGC SE 1.1 feature-style subset import. |
| `mundane-map-io-gpx` | Bounded Level 2 GPX 1.1 waypoint and track feature sources. |
| `mundane-map-io-kml` | Bounded Level 2 KML 2.2 point, line, polygon, and homogeneous multipart feature sources. |
| `mundane-map-symbology-milstd2525` | Bounded Level 2 MIL-STD-2525E Change 1 icon-based point symbology. |
| `mundane-map-io-geojson-jackson` | Optional bounded Level 2 RFC 7946 feature-source reader/writer using Jackson Core. |
| `mundane-map-io-maplibre-style-jackson` | Optional bounded Level 2 MapLibre v8 vector-style reader using Jackson Core. |
| `mundane-map-io-geopackage-xerial` | Optional Linux JVM-only bounded Level 2 GeoPackage 1.4.0 feature and PNG/JPEG tile reader using pinned Xerial SQLite JDBC classifiers. |
| `mundane-map-io-mbtiles-xerial` | Optional Linux JVM-only bounded Level 2 MBTiles 1.3 PNG/JPEG tile reader using pinned Xerial SQLite JDBC classifiers. |
| `mundane-map-workspace` | Immutable workspace values plus bounded secure read, canonical atomic write, explicit local openers, and owning sessions for `.mmap.xml` version 1. |

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

For documentation-only feedback, `./gradlew javadocAll --console=plain` generates strict Java 21
Javadocs for every module, example, and hand-authored build-support source set. The normal gate also
runs exhaustive public/protected declaration checks and the same Javadoc aggregate.

Every Java project with executable production instructions enforces at least 80% JaCoCo
instruction coverage both for its aggregate bundle and for every hand-authored production source
file. Its normal `test` task produces XML at
`PROJECT/build/reports/jacoco/test/jacocoTestReport.xml` and browsable HTML at
`PROJECT/build/reports/jacoco/test/html/index.html`. Deterministic per-source CSV and Markdown
summaries are written beside them as `source-coverage.csv` and `source-coverage.md`;
files without executable instructions remain visible separately. `check` and `qualityGate` run
both matching verification tasks. The
[pre-change coverage baseline](verification/G17-004-coverage-baseline.tsv) records the complete
governed inventory and the behavior risks used to select the added tests.

Specialized evidence remains independent so normal development does not silently require platform
raster evidence, a corpus, profiling, publication staging, or a native toolchain:

```bash
./gradlew offlineRepositoryVerification --console=plain
./gradlew renderRegression --console=plain
./gradlew shapefileCorpus --console=plain
./gradlew dtedCorpus --console=plain
./gradlew performanceQuick --console=plain
./gradlew performanceEvidence --console=plain
./gradlew liveTrackSmoke --console=plain
./gradlew nativeSmoke --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
```

`renderRegression` uses bounds, topology, tolerant color regions, ordering, clipping, and
interpolation invariants rather than byte-identical whole images. `performanceQuick` is a
noncanonical iteration lane; only `performanceEvidence` produces canonical performance evidence.
The opt-in `liveTrackEvidence` task runs one explicitly selected 10k, 100k, or 1m profile and is not
part of the normal gate. The offline lane verifies the complete normal gate from one isolated
Maven-layout repository. GitHub Actions separately runs the Java 21 quality and Java 25 test jobs, rendering,
Shapefile/DTED corpus and performance jobs, a Linux x86-64 Native Image job, isolated offline
repository verification, and the exact glibc/musl SQLite-adapter platform matrix. These specialized
lanes remain separate because they require cold homes, corpus data, external tools, containers,
platform-specific behavior, or deliberately expensive evidence.

The Java 21 CI job owns the complete `qualityGate`, including formatting, Checkstyle, SpotBugs,
coverage reporting and thresholds, architecture rules, and Javadocs. The Java 25 job runs
`supportedJdkTests`, which compiles against Java 21 and executes every normal JUnit suite—including
the architecture-test suite—on the additional supported test JDK. It does not repeat task-based
formatting, Checkstyle, SpotBugs, coverage, or Javadocs. The checked
[verification manifest](verification/G17-002-verification-manifest.tsv)
maps every normal, specialized, platform, and opt-in lane to its owning workflow or command.

## Level 1 support statement

Published artifacts require Java 21. The Level 1 production runtime is JDK-only, with Swing and
Java2D confined to `mundane-map-awt`; applications explicitly register renderers, raster decoders,
and recognized CRS operations. The supported Level 1 surface comprises the bounded symbol/vector,
interaction/measurement, source/CRS, read-only shapefile, PNG/JPEG/world-file, and evidence-backed
performance profiles described below and in the [changelog](CHANGELOG.md).

Release verification targets GraalVM Java 21 on Ubuntu 24.04 Linux x86_64. That Native Image claim
becomes valid only for a release candidate whose exact build-and-run CI evidence is recorded at the
G8 release checkpoint. Windows, macOS, Linux AArch64, other distributions, and cross-platform Native
Image compatibility are not claimed without separate evidence.

Binary parsers and image sources enforce explicit limits and return stable structured diagnostics;
they do not promise recovery of arbitrary malformed input. The performance evidence is tied to its
recorded scenarios and environment and is not a portable latency or throughput guarantee. General
CRS transformation, raster reprojection, editing, export, arbitrary SVG, and the additional formats
and adapters listed under Level 2 are outside the Level 1 support statement.

## Optional horizontal world-wrap profile

Web Mercator views can opt into bounded continuous east/west display repetition. Repetition is off
by default and must be enabled both on the view and on each compatible global feature, editable, or
raster binding. Canonical source coordinates and logical feature identifiers do not acquire a copy
index; local and non-wrapped layers retain their ordinary behavior.

The profile supports repeated projected points, geographic dateline-split lines and polygons,
wrapped interaction/editing/measurement, detached vector export, and full-period PNG/JPEG rasters
whose CRS and axis-aligned affine placement satisfy the documented compatibility checks. It does
not infer global layers, repair arbitrary topology, wrap vertically, widen CRS domains, or promise
globe/polar behavior. Copy, precision, query, geometry, raster, and allocation work remains bounded
and produces stable diagnostics.

```java
var binding = MapLayerBinding.ownedFeature("world", "World", source, portrayal);
binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X); // before attachment
map.setHorizontalWrap(HorizontalWrap.webMercator());
map.setLayerBindings(List.of(binding));
```

Linux Java 21 Native Image and staged Java 21 consumer verification exercise this explicit profile.
The paired `world-wrap-plan-disabled` and `world-wrap-plan-wrapped` performance rows are descriptive
evidence only; they establish no portable wall-clock threshold.

## OGC Symbology Encoding support profile

`mundane-map-io-se` reads a named, bounded subset of OGC Symbology Encoding 1.1
`FeatureTypeStyle`; it does not claim an SE conformance class. Input is one local regular UTF-8 file
or caller-owned byte snapshot. The adapter directly constructs hardened JDK StAX, resolves no
schemas, DTDs, entities, XInclude, URLs, files, or classpath resources, and publishes no XML parser
type through its public API.

The supported portrayal surface is:

- declaration-ordered ordinary and `ElseFilter` rules with inclusive minimum and exclusive maximum
  scale denominators;
- exact-property comparison, between, explicit-null, `And`, `Or`, and `Not` filters over bounded
  literals and canonical feature attributes;
- solid point marks named `square`, `circle`, `triangle`, `star`, `cross`, or `x`, with literal
  pixel size, rotation, displacement, anchor, color, and opacity values from the approved profile;
- solid line stroke and polygon fill/outline symbolizers in literal screen-pixel units; and
- one marker-role `ExternalGraphic` resolved only as an exact key in a caller-supplied immutable
  symbol catalog with media type `application/vnd.mundane-map.symbol`.

SLD/WMS documents, coverage symbolizers, text, raster, graphic strokes/fills, dash/cap/join effects,
CSS, functions and arithmetic expressions, geometry expressions, dynamic parameters, remote or
embedded resources, schema validation, and arbitrary SE/XML extensions are excluded. Public read
limits bound bytes, XML structure and text, rules, predicates, symbolizers, catalog references, and
owned output allocation. Unsupported or hostile input fails atomically with a stable structured
`SeReadProblem`.

## MapLibre Style support profile

`mundane-map-io-maplibre-style-jackson` reads the named
“mundane-java-map MapLibre v8 vector-style profile”; it does not claim general MapLibre or Mapbox
compatibility. The optional AWT-free adapter directly constructs its pinned Jackson Core parser and
publishes no Jackson type. It accepts bounded local byte snapshots only and never dereferences a
style source or fetches a URL.

The supported profile maps declaration-ordered circle, line, fill, and point-symbol layers onto the
same immutable symbols, predicates, portrayal resolver, and point-label path used by project-native
styles and the OGC SE adapter. Applications explicitly provide exact-key feature-source and named
symbol catalogs. Literal values, typed filters, zoom ranges, and the closed documented
`match`/`case`/`step`/linear-interpolation subset are supported; unsupported members and expressions
fail atomically with stable structured diagnostics.

Remote or inline source loading, vector/raster tiles, sprites, glyph services, arbitrary fonts,
network access, 3D/terrain/heatmap layers, general MapLibre expressions, transitions, and extension
syntax are excluded. The supported matrix and limits are authoritative in the
[G14 design](design/G14-maplibre-style.md). The staged Java 21 consumer covers direct parse,
expression, bind, icon and label rendering, and vector label capture. Ubuntu 24.04 Linux x86-64
GraalVM CE 21 covers the same parser/binder, label-profile resolution, icon rendering, and stable
diagnostic paths without claiming native Java2D glyph execution. Windows/macOS Native Image
behavior is not claimed.

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
Unknown definitions are retained when available but are not guessed or transformed. Additional
projections and optional JTS/PROJ/GDAL adapters remain deferred. DTED, GeoTIFF, fixed-host HTTP XYZ,
the static SVG subset, and the optional Jackson Core GeoJSON and Xerial GeoPackage/MBTiles profiles
are implemented Level 2 capabilities and do not broaden Level 1. The HTTP adapter is a JVM-only
explicit acquisition client and has no Native Image support claim. GeoPackage provides catalog, all
six approved two-dimensional feature geometry families, typed attribute projection,
retained/recognized CRS metadata, and explicit-zoom sparse PNG/JPEG tile-matrix raster sources.
MBTiles provides strict
single-tileset metadata, explicit-zoom TMS-to-XYZ conversion, and sparse PNG/JPEG raster windows.
Both tile adapters offer transactional optional decoded caching and runnable viewers. Their exact
supported deployment is Java 21 on Linux x86-64 with glibc 2.35 or newer; pinned Ubuntu 22.04 and
24.04 fresh-JVM lanes record open/query/read/render evidence, while Alpine/musl is explicitly
rejected. Both adapters are Native Image `not-targeted`.

## MIL-STD-2525 point-symbol profile

`mundane-map-symbology-milstd2525` implements the MundaneJ supported MIL-STD-2525E Change 1
icon-based point-symbol profile for a finite Land Unit, Land Equipment, and Activities inventory.
It is not a complete MIL-STD-2525 implementation or conformance claim.

The module accepts canonical 30-position SIDCs for identities 0–6, present/planned status, fifteen
project-authored entity paths, and seven graphical sector modifiers from the approved Appendix-A
tables. It exposes explicit strict and degraded resolution into ordinary toolkit-neutral symbols,
two fixed palettes, and an exact finite SIDC-attribute portrayal. It does not implement tactical
graphics, text amplifiers, dynamic catalogs, legacy SIDCs, APP-6 translation, arbitrary symbol
sets, classpath discovery, or a military-specific AWT renderer.

The runnable gallery includes the full bounded entity, identity/status, modifier/fallback, and
light/dark palette matrix:

```bash
./gradlew :examples:symbol-gallery:run
```

The staged Java 21 consumer and the shared Linux x86-64 GraalVM Java 21 executable verify parse,
resolve, portray, render, degraded, malformed, and unsupported paths. No Windows or macOS Native
Image claim is made.

## Local workspace profile

`mundane-map-workspace` is a Level 2 JDK-only convenience for reopening a local map composition. Its
strict `.mmap.xml` version 1 grammar stores viewport state, ordered guarded relative source
references, exact external catalog-symbol names, and raster interpolation/opacity. Applications
explicitly register trusted source openers, finite path/sidecar profiles, recognized CRS definitions,
and immutable symbol catalogs; the module performs no classpath discovery and never embeds data,
credentials, runtime limits, caches, selection, tools, or edit history.

Reads reject symbolic links, traversal, malformed UTF-8/XML, DTDs/entities, unknown grammar, and
configured limit excesses with bounded structured diagnostics. Writes are canonical and require a
forced same-directory temporary file plus atomic replacement, with no non-atomic fallback. Opening is
all-or-nothing: the returned `WorkspaceSession` owns every source and closes them in reverse order.
Views borrow those sources and must close or detach their bindings before closing the session. The
runnable `:examples:workspace-viewer` demonstrates explicit shapefile and world-file image policies.

The supported Native Image statement is limited to the shared Linux Java 21 smoke lane. Workspace
files are local configuration, not a sandbox: registered openers are trusted application code, and a
concurrent filesystem replacement after the final guarded identity check remains an OS boundary.

The implemented Level 2 GeoJSON adapter supports the bounded six-family profile documented in the
design. Its directly constructed Jackson Core reader/writer, source query, and renderer path are also
verified by the Linux x86-64 Native Image smoke; this is not a Windows or macOS claim. Run its review
viewer with the bundled fixture, or pass one local file:

```bash
./gradlew :examples:geojson-viewer:run
./gradlew :examples:geojson-viewer:run --args=/absolute/path/data.geojson
```

## Examples

Twenty independent examples consume the published APIs without copying parsers or renderers:

```bash
./gradlew :examples:basic-viewer:run
./gradlew :examples:symbol-gallery:run
./gradlew :examples:measurement-viewer:run
./gradlew :examples:shapefile-viewer:run --args='<path.shp> [EPSG:4326|EPSG:3857]'
./gradlew :examples:raster-viewer:run --args='<image.png-or-jpeg> [--world-file EPSG:4326|EPSG:3857]'
./gradlew :examples:elevation-viewer:run
./gradlew :examples:geojson-viewer:run --args='<optional-path.geojson>'
./gradlew :examples:gpx-viewer:run --args='<path.gpx>'
./gradlew :examples:kml-viewer:run --args='<path.kml>'
./gradlew :examples:geotiff-viewer:run
./gradlew :examples:geopackage-viewer:run --args='/absolute/path/data.gpkg feature_table'
./gradlew :examples:geopackage-viewer:run --args='/absolute/path/data.gpkg tile_table 4'
./gradlew :examples:mbtiles-viewer:run --args='/absolute/path/data.mbtiles 4'
./gradlew :examples:http-tile-viewer:run
./gradlew :examples:point-edit-viewer:run
./gradlew :examples:styling-label-viewer:run
./gradlew :examples:workspace-viewer:run
./gradlew :examples:vector-export:run
./gradlew :examples:se-viewer:run
./gradlew :examples:maplibre-style-viewer:run
./gradlew :examples:live-track-stress:run --args='--population=1000000'
```

The basic, symbol, measurement, elevation, editing, styling, and workspace examples are deterministic
no-argument demonstrations. The format viewers are real file consumers, apply their modules' limits,
present structured diagnostics under fixed non-path source identities, and transfer or retain source
ownership according to their documented view/session lifecycle. The
[SE viewer](examples/se-viewer) is a project-authored vector-profile gallery with scale controls,
ordered rules, an explicit catalog marker, lines, polygon fills/outlines, and a visible hole. The
[live-track stress example](examples/live-track-stress/README.md) is a JVM-only packed simulation,
estimation, rendering, and evidence workload rather than a public tracking API.

## Licenses and fixture provenance

The project code is licensed under [Apache License 2.0](LICENSE). Optional adapters retain their
dependency notices in their published resources:
[GeoJSON/Jackson](modules/mundane-map-io-geojson-jackson/src/main/resources/META-INF/NOTICE),
[MapLibre/Jackson](modules/mundane-map-io-maplibre-style-jackson/src/main/resources/META-INF/NOTICE),
[GeoPackage/Xerial](modules/mundane-map-io-geopackage-xerial/src/main/resources/META-INF/NOTICE), and
[MBTiles/Xerial](modules/mundane-map-io-mbtiles-xerial/src/main/resources/META-INF/NOTICE).

Independently sourced or tool-generated fixtures retain exact provenance beside the data. The main
entry points are the [Natural Earth chart](examples/live-track-stress/NATURAL_EARTH_PROVENANCE.md),
[GeoTIFF corpus](modules/mundane-map-io-geotiff/src/test/resources/geotiff-corpus/PROVENANCE.md),
[MapLibre fixtures](modules/mundane-map-io-maplibre-style-jackson/src/test/resources/io/github/mundanej/map/io/maplibre/style/fixtures/PROVENANCE.md),
[SE fixtures](modules/mundane-map-io-se/src/test/resources/se-fixtures/PROVENANCE.md), and
[native raster fixtures](modules/mundane-map-native-tests/src/test/resources/io/github/mundanej/map/nativeimage/raster/PROVENANCE.md).
Those notices define fixture rights and evidence scope; they do not widen the runtime support
statements above.

## Design and roadmap

- [DESIGN.md](DESIGN.md) indexes the compact architecture and approved decisions.
- [CHANGELOG.md](CHANGELOG.md) records release capabilities, migrations, limits, and non-claims.
- [ROADMAP.md](ROADMAP.md) separates the Level 1 release gates from Level 2 work.
- [tasks/](tasks/) contains implementation-sized vertical slices and exact validation commands.
- [Project-hardening design](design/G17-project-hardening.md) records the current documentation,
  build-efficiency, Javadoc, and coverage evidence.
