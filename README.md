# mundane-java-map

`mundane-java-map` is a small Java 21 map-component library built around explicit APIs,
JDK-only runtime modules, Swing/Java2D rendering, and GraalVM Native Image compatibility.

The repository currently contains an initial vertical slice: in-memory point, line, and polygon
features can be projected, displayed, panned, zoomed, and inspected through map-coordinate pointer
events. Data-format adapters, symbols, measurements, and raster sources are planned but are not
represented by empty placeholder modules.

## Requirements

- A Java 17 or newer runtime to launch Gradle; the build resolves a Java 21 compiler toolchain.
- GraalVM with `native-image` only for the optional native smoke lane.

## Build

```bash
./gradlew qualityGate --console=plain
```

Useful focused commands:

```bash
./gradlew checkAll --console=plain
./gradlew :examples:basic-viewer:run
./gradlew nativeSmoke --console=plain
./gradlew publicationDryRun --console=plain
./gradlew printPublishedArtifacts --console=plain
```

`nativeSmoke` is intentionally separate from `qualityGate` because it requires a GraalVM
toolchain. The normal gate has no external services or native tools.

The library always compiles to the Java 21 API and class-file baseline. CI may select a newer test
launcher with `-Pmap.testJavaVersion=<version>` without changing published bytecode.

For a fully local build, provide one absolute normalized Maven-layout repository as the sole plugin
and dependency source for both the main build and `build-logic`:

```bash
./gradlew -Pmap.offlineRepo=/absolute/path/to/repository --offline qualityGate --console=plain
```

The repository must contain the required plugin markers and all transitive build/test artifacts.
There is no public or machine-local fallback when `map.offlineRepo` is set.

`publicationDryRun` recreates and verifies `build/release-dry-run/maven` with the POM, Gradle module
metadata, binary, sources, and Javadoc artifacts for `io.github.mundanej:mundane-map-api`,
`io.github.mundanej:mundane-map-core`, and `io.github.mundanej:mundane-map-awt` at the current project
version. It performs no remote publication.

## Modules

| Module | Responsibility |
| --- | --- |
| `mundane-map-api` | Public geometry, feature, layer, style, projection, and pointer-event contracts. |
| `mundane-map-core` | JDK-only viewport math, Web Mercator projection, and in-memory layers. |
| `mundane-map-awt` | Swing/Java2D map component and interaction wiring. |
| `mundane-map-architecture-tests` | Dependency-direction and Native Image architecture checks. |
| `mundane-map-native-tests` | Real offscreen-render Native Image smoke entrypoint. |
| `examples/basic-viewer` | Runnable point, line, and polygon demonstration. |

Future formats will be isolated adapters such as `mundane-map-io-shapefile`,
`mundane-map-io-image`, and `mundane-map-io-geotiff`. They should be added only with working
behavior and tests.

## Small example

```java
Layer cities = new InMemoryLayer(
    "cities",
    "Cities",
    List.of(new Feature(
        "bos",
        "Boston",
        new PointGeometry(new Coordinate(-71.0589, 42.3601)),
        Map.of("kind", "city"),
        FeatureStyle.point(Rgba.rgb(28, 108, 184), 10.0))));

MapView map = new MapView(new WebMercatorProjection());
map.setLayers(List.of(cities));
map.fitToData(32.0);
```

See `examples/basic-viewer` for complete usage.

## Design and roadmap

- [DESIGN.md](DESIGN.md) records the compact architecture and decision log.
- [ROADMAP.md](ROADMAP.md) defines capability gates rather than speculative modules.
- [tasks/](tasks/) contains implementation-sized vertical slices.

## Status

The project starts at `0.1.0-SNAPSHOT`. Public APIs may change freely before `1.0.0`.
