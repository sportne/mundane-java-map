# Roadmap

The roadmap is organized as capability gates. Each gate must leave runnable, tested behavior.

## G0 — Scaffold and feasibility

- Gradle multi-module build and quality gate.
- Compact design, roadmap, and agent guidance.
- Dependency-direction architecture checks.
- Real AWT/offscreen Native Image smoke wiring.

## G1 — First map slice

- Immutable point, line, and polygon geometry.
- Features, layers, names, attributes, and basic styles.
- Web Mercator and viewport transforms.
- Swing rendering, pan, cursor-centered zoom, pointer-coordinate events, and fit-to-data.
- Runnable basic viewer and offscreen render test.

## G2 — Symbols and vector graphics

- Marker, line, fill, and composite symbol contracts.
- Toolkit-neutral vector paths and Java2D painters.
- Named symbol catalogs, raster icons, placement, rotation, and anchoring.
- Symbol gallery and native resource smoke tests.

## G3 — Interaction and measurement

- Hover, selection, click hit testing, and tool lifecycle.
- Pixel-tolerant point, line, and polygon hit testing.
- Planar and geographic distance strategies.
- Interactive distance measurement tool.

## G4 — Data adapters

- Format-neutral feature and raster source APIs.
- `mundane-map-io-shapefile`: read-only SHP, SHX, DBF, CPG, and PRJ profile.
- `mundane-map-io-image`: PNG/JPEG and world-file raster source.
- Bounded readers, stable diagnostics, malformed-input tests, and curated corpora.

## G5 — Raster and projection expansion

- Affine raster georeferencing and window requests.
- Additional projections selected by demonstrated use cases.
- Explicit unsupported-CRS diagnostics.
- Level 2 candidates: GeoJSON, GeoTIFF, and GeoPackage adapters.

## G6 — Performance and indexing

- Packed spatial index, viewport queries, clipping, simplification, and caches.
- Large-data fixtures, JFR workflow, and repeatable performance evidence.

## G7 — Native and release hardening

- Linux offscreen native CI; Windows/macOS evidence before cross-platform native claims.
- API and Javadoc review, publication dry run, downstream consumer smoke, checksums, and release
  readiness review.

## Level 2 backlog

- GeoJSON, GeoTIFF, GeoPackage, GPX, KML, MBTiles, and remote tile adapters.
- Basic editing, undo/redo, snapping, thematic styling, label placement, and workspace persistence.
- Optional JTS, PROJ, SQLite, or GDAL adapters isolated from the core API.

