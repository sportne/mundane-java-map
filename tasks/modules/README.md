# Module completeness tasks

This directory groups the G19 feature-completeness backlog by the Gradle module that owns the work.
The review and standards baseline are in
[`design/G19-module-feature-completeness-review.md`](../../design/G19-module-feature-completeness-review.md).

Cards expand the intentionally bounded G0–G18 profiles. Until a card is complete, existing README
support statements remain authoritative; a proposed card does not retroactively weaken or broaden a
released profile.

| Module | Proposed cards | Principal gap |
| --- | ---: | --- |
| `mundane-map-api` | 0 | Complete: [dimensional geometry](../closed/G19-001-ordinate-aware-geometry-and-heterogeneous-collections.md) and [advanced portrayal](../closed/G19-002-advanced-portrayal-and-structured-attributes.md) |
| [`mundane-map-core`](mundane-map-core/) | 3 | Reprojection, labels, and tile matrices; [common CRS/WKT2](../closed/G19-010-common-crs-catalog-and-wkt2-operations.md) and [bounded dimensional topology](../closed/G19-011-dimensional-geometry-validity-and-topology.md) are complete |
| [`mundane-map-awt`](mundane-map-awt/) | 2 | Advanced rendering plus accessible/printable Swing behavior |
| [`mundane-map-io-shapefile`](mundane-map-io-shapefile/) | 7 | Complete reading and transactional new-dataset export |
| [`mundane-map-io-image`](mundane-map-io-image/) | 5 | Static PNG 3 and common JPEG decode interoperability |
| [planned `mundane-map-awt-image-webp-twelvemonkeys`](mundane-map-awt-image-webp-twelvemonkeys/) | 3 | Optional static WebP decode through pinned TwelveMonkeys/AWT integration |
| [`mundane-map-io-http-tiles`](mundane-map-io-http-tiles/) | 3 | HTTP policy/cache and direct raster matrices |
| [planned `mundane-map-io-tilejson-jackson`](mundane-map-io-tilejson-jackson/) | 2 | Read-only TileJSON 2.x/3.0 parsing and raster discovery |
| [planned `mundane-map-io-wmts`](mundane-map-io-wmts/) | 4 | Read-only WMTS 1.0.0 KVP/REST tiles and FeatureInfo |
| [planned `mundane-map-io-ogc-api-tiles-jackson`](mundane-map-io-ogc-api-tiles-jackson/) | 5 | Guarded OGC API Tiles discovery and generic tile retrieval |
| [`mundane-map-io-dted`](mundane-map-io-dted/) | 7 | Metadata, windows, catalogs/mosaics, and builder-driven cell writing |
| [`mundane-map-io-geotiff`](mundane-map-io-geotiff/) | 10 | TIFF/BigTIFF, samples/CRS/windows, and GeoTIFF/COG writing |
| [`mundane-map-io-svg`](mundane-map-io-svg/) | 10 | Restricted static SVG import, filters/resources, and accessible export |
| [`mundane-map-io-se`](mundane-map-io-se/) | 10 | SE/SLD documents, Filter 1.1, all symbolizers, canonical writing, and conformance |
| [`mundane-map-io-gpx`](mundane-map-io-gpx/) | 7 | Complete GPX 1.1 domain, extensions, feature mapping, writing, and interoperability |
| [`mundane-map-io-kml`](mundane-map-io-kml/) | 10 | KML 2.3/KMZ model, 2D presentation, links, updates, tours, writing, and conformance |
| [planned `mundane-map-io-kml-html-jsoup`](mundane-map-io-kml-html-jsoup/) | 2 | Sanitized static description/balloon HTML and renderer integration |
| [`mundane-map-symbology-milstd2525`](mundane-map-symbology-milstd2525/) | 10 | Current U.S./NATO catalogs, point/tactical rendering, legacy translation, and conformance |
| [`mundane-map-io-geojson-jackson`](mundane-map-io-geojson-jackson/) | 7 | Complete RFC 7946, RFC 8142 streaming, controlled legacy CRS input, deterministic writing, and interoperability |
| [`mundane-map-io-maplibre-style-jackson`](mundane-map-io-maplibre-style-jackson/) | 10 | Pinned complete style interchange, complete 2D layers/resources, deterministic writing, and conformance |
| [`mundane-map-io-geopackage-xerial`](mundane-map-io-geopackage-xerial/) | 10 | Complete GeoPackage 1.4 direct use, official extensions, separate community profiles, and conformance |
| [`mundane-map-io-mbtiles-xerial`](mundane-map-io-mbtiles-xerial/) | 10 | MBTiles 1.3, recognized schemas, MVT/UTFGrid, builders, CRUD/rewrite, and conformance |
| [`mundane-map-workspace`](mundane-map-workspace/) | 10 | Versioned complete state, portable packaging, integrity, recovery, and lifecycle |
| [`mundane-map-vaadin`](mundane-map-vaadin/) | 10 | Browser/accessibility/mobile parity, scalable transport/cache, Canvas/worker/WebGPU, and closeout |
| [`mundane-map-architecture-tests`](mundane-map-architecture-tests/) | 2 | JPMS identities/consumers and release governance; [released API/SemVer governance is complete](../closed/G19-190-released-api-baselines-and-semver-governance.md) |
| [`mundane-map-native-tests`](mundane-map-native-tests/) | 5 | Host/toolchain matrix, closed-world closure, corpus parity, platform services, and release evidence |
| [`mundane-map-performance-tests`](mundane-map-performance-tests/) | 5 | JMH methodology, correctness, workload coverage, integration evidence, and evidence governance |

[G19-999](G19-999-external-expert-feature-completeness-closeout.md), stored directly under
`tasks/modules`, is the final external-expert closeout card. There are 174 module-owned cards and one
project-wide closeout card.
