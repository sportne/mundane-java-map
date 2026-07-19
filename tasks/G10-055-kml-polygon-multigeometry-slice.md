# G10-055 — KML polygon and MultiGeometry slice

Status: Proposed
Depends on: G10-054
Gate: G10
Type: AFK

## Goal

Complete the supported static KML geometry mappings with polygons, holes, and homogeneous
MultiGeometry records visible in a runnable viewer.

## Context

G10-054 establishes secure KML opening and Point/LineString behavior. G10-005 defines ring closure,
altitude omission, homogeneous MultiGeometry mapping, feature ordering, and unsupported mixed/nested
geometry outcomes.

## Scope

Add Polygon outer/inner rings, exact first/last x/y closure validation, coordinate negative-zero
canonicalization without normalizing ring order, orientation, or other coordinates, packed multipart
geometry, and homogeneous Point, LineString, or Polygon MultiGeometry mapping. Add a local-file KML viewer and
tolerant render-regression cases for holes, multipart geometry, container nesting, and mixed supported
geometry layers.

## Out of scope

Heterogeneous or nested MultiGeometry, altitude rendering, styles, labels, dynamic/network KML,
ExtendedData, KMZ, exhaustive hostile-input hardening, and Native Image.

## Acceptance criteria

- Supported Polygon placemarks retain closed outer rings and holes and render with correct fill
  topology.
- Homogeneous MultiGeometry values map to the approved ordinary multipart MundaneJ geometry without
  exposing KML-specific geometry types; mixed or nested forms fail predictably.
- The viewer renders all supported KML geometry families from local files without following external
  references.
- Rendering regression verifies geometry, bounds, hole behavior, and tolerant colors without
  requiring cross-platform pixel identity.

## Required tests

Polygon ring/closure/hole cases, homogeneous and rejected MultiGeometry cases, container/source
ordering, packed storage, viewer integration, and tolerant rendering-regression tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-kml:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use the existing ordinary Point/MultiPoint, LineString/MultiLineString, and Polygon/MultiPolygon
contracts. Do not introduce a generic geometry collection solely to mirror KML.
