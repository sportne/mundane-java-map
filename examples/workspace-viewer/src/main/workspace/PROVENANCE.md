# Workspace viewer fixture provenance

The build materializes a fixed local example under `build/workspace-fixture`.

- `data/map.*` is the BSD-3-Clause generated polygon/hole EPSG:3857 corpus fixture already tracked
  by `mundane-map-io-shapefile` as `generated-polygon-hole-windows1252-3857`.
- `data/image.png` is the 93-byte generated PNG fixture already tracked by the native raster smoke
  inventory. The workspace viewer supplies its own six-coefficient `image.pgw` so its represented
  bounds are `[0,0]-[80,40]`, matching the shapefile fixture.
- `example.mmap.xml` and `data/image.pgw` are project-authored BSD-3-Clause test/example data.

No fixture is downloaded, generated from ambient data, or selected by network or credentials.
