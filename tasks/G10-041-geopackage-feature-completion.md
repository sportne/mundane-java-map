# G10-041 — GeoPackage feature completion

Status: Proposed
Depends on: G10-040
Gate: G10
Type: AFK

## Goal

Read, query, and render the complete approved GeoPackage geometry and attribute profile with explicit
CRS behavior and hostile-feature validation.

## Context

G10-040 supplies the optional module, strict session/catalog boundary, point geometry, publication,
and consumer path. The proposed G10-004 design closes the remaining feature matrix.

## Scope

Add LineString/MultiLineString/Polygon/MultiPolygon GeoPackage binary parsing, multipart/hole
semantics, approved typed attributes and projection, retained-unknown versus explicitly recognized
CRS metadata, geometry-envelope query rejection, decoded-envelope intersection, stable empty-geometry
warnings, complete feature limits/diagnostics, and a feature viewer across all supported geometries.

## Out of scope

Z/M, GeometryCollection, curves/surfaces, topology repair, automatic spatial indexes, reprojection,
tiles, writing, schema extensions, or alternate SQLite drivers.

## Acceptance criteria

- All six approved 2D geometry families and approved scalar/byte/date attribute values query in
  primary-key order and render with correct multipart/hole semantics.
- Geometry headers, WKB, envelopes, CRS/table/header agreement, attribute declarations/storage, and
  projection obey the exact closed profile and stable record/schema diagnostics.
- Empty geometries are skipped with bounded warnings; malformed/hostile feature rows publish no
  partial record and leak no database identifiers or values.

## Required tests

Both geometry byte orders, every geometry family, multipart/hole/envelope tests, every approved
attribute type and projection, recognized/unknown CRS cases, empty warnings, query bounds, malformed
geometry/schema/record, cancellation, and tolerant viewer rendering.

## Validation

```bash
./gradlew :modules:mundane-map-io-geopackage-xerial:check :modules:mundane-map-awt:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Retain external types privately and use packed immutable G4 geometries; do not introduce a
GeoPackage-specific public geometry model.
