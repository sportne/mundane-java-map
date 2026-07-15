# Raster-viewer G6-002 fixture copy

These repository-authored test resources are distributed under the project's BSD-3-Clause license.
They are an exact test-local copy of the G6-002 in-domain geographic PNG pair documented by
`modules/mundane-map-awt/src/test/resources/io/github/mundanej/map/awt/image-fixtures/g6-002/PROVENANCE.md`.

| Resource | Decoded/physical bytes | SHA-256 |
| --- | ---: | --- |
| `geographic-rgba.png.b64` | 79 | `b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe` |
| `geographic-rgba.pgw` | 22 | `2f04215b9625536b036b768d8bffc03c2045debd66cb34dc0361c5398ffdbbd5` |

The viewer test decodes the Base64 transport, verifies both byte lengths and SHA-256 values, copies
the bytes to one temporary sibling pair, and loads that pair through the production viewer path.
