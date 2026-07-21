# Natural Earth land-chart provenance

The `live-track-stress` example bundles the five unmodified sidecars needed to open Natural Earth
`ne_110m_land`, version `4.1.0`, through the project's shapefile reader.

- Official dataset page: <https://www.naturalearthdata.com/downloads/110m-physical-vectors/>
- Archive: <https://naciscdn.org/naturalearth/110m/physical/ne_110m_land.zip>
- Retrieved (UTC): `2026-07-21`
- Upstream version: `4.1.0`
- Archive SHA-256: `1926c621afd6ac67c3f36639bb1236134a48d82226dc675d3e3df53d02d2a3de`
- Terms checked (UTC): `2026-07-21`
- Terms: <https://www.naturalearthdata.com/about/terms-of-use/>
- Redistribution basis: Natural Earth identifies its raster and vector map data as public domain.

The retained files are byte-for-byte archive members. No geometry, attribute, encoding, or CRS
sidecar is rewritten. At runtime the example clips decoded geographic geometry to the explicit Web
Mercator latitude domain before projection; this does not alter the bundled data. The upstream ESRI
WKT is retained verbatim in the PRJ sidecar. Because that spelling is outside the reader's deliberately
small recognized-WKT grammar, the example explicitly declares EPSG:4326 when opening this exact,
hash-verified dataset rather than guessing from untrusted text.

| Retained archive member | Bytes | SHA-256 |
| --- | ---: | --- |
| `ne_110m_land.shp` | 89,504 | `8689e6932b8e370e2ca4587cf3ba21e460b1235db37b6ed3c172c35b4a6088de` |
| `ne_110m_land.shx` | 1,116 | `2719254764a70262a34333581d582d503b8af5d6626e6da4eb2b5f86e7316faa` |
| `ne_110m_land.dbf` | 3,431 | `db7cf6d2de2811df09bd7fcc6f243ab78a715b83571a0cb7b36b4e2af3297caa` |
| `ne_110m_land.prj` | 147 | `3259f0e55290a82b1350646f604e8a7ee1e2136c0320a40fad838ab40819fff8` |
| `ne_110m_land.cpg` | 5 | `3ad3031f5503a4404af825262ee8232cc04d4ea6683d42c5dd0a2f2a27ac9824` |

To reproduce the retained resource set, download the archive, verify its SHA-256, and extract only
the five members listed above into
`src/main/resources/io/github/mundanej/map/example/livetrack/naturalearth/`. Verify each extracted
member against this table. The archive's `VERSION.txt` was `4.1.0`; its documentation files are not
runtime resources and are represented by this provenance record and the official links above.
