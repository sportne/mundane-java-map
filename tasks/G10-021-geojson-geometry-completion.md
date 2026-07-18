# G10-021 — GeoJSON geometry completion

Status: Proposed
Depends on: G10-020
Gate: G10
Type: AFK

## Goal

Read and render every approved 2D GeoJSON geometry family through one source-backed map slice.

## Context

G10-020 supplies the bounded source, parser boundary, Point/MultiPoint behavior, and publication path.

## Scope

Add LineString, MultiLineString, Polygon, and MultiPolygon coordinate parsing, packed immutable
geometry construction, holes and multipart ordering, viewport queries, and tolerant AWT rendering
tests.

## Out of scope

GeometryCollection, empty or Z/M geometry, topology repair, antimeridian splitting, writing, and
viewer/corpus work.

## Acceptance criteria

- All six supported geometry roots query and render through the real source/layer stack.
- Coordinate range, cardinality, closure, multipart ordering, and hole semantics match G10-002.
- Unsupported geometry shapes terminate with stable diagnostics and no partial record publication.

## Required tests

Geometry unit tests, feature/query integration tests, polygon-hole/multipart tests, malformed geometry
tests, and tolerant render assertions.

## Validation

```bash
./gradlew :modules:mundane-map-io-geojson-jackson:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Retain document order and packed primitive coordinate storage; do not add a GeoJSON geometry model.
