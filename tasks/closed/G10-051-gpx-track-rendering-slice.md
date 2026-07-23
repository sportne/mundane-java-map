# G10-051 — GPX track rendering slice

Status: Complete
Depends on: G10-050
Gate: G10
Type: AFK

## Goal

Read GPX track segments as useful line features, expose their approved fixed attributes, and render
them through a runnable viewer.

## Context

G10-050 establishes the secure snapshot/parser boundary and materialized GPX source. G10-005 defines
track/segment ordering, IDs, coordinate policy, fixed attributes, and warned loss of per-vertex data.

## Scope

Extend `mundane-map-io-gpx` with track and segment parsing, packed longitude/latitude coordinates,
LineString records, document-order candidate numbering, fixed track attributes, and bounded warnings
for skipped short segments and ignored per-track-point values. Add a runnable GPX viewer and tolerant
render-regression coverage for waypoint and track inputs.

## Out of scope

Routes, extension interpretation, preserving per-vertex elevation/time arrays, complete grammar and
limit hardening, Native Image, editing, and KML.

## Acceptance criteria

- Every segment with at least two valid track points becomes one ordered LineString with the approved
  generated ID, candidate number, attributes, and literal non-wrapping EPSG:4326 coordinates.
- Empty and one-point segments and omitted per-vertex elevation/time data produce bounded warnings
  without fabricating features or format-specific public geometry.
- A runnable local-file viewer renders waypoints and track segments without performing network I/O.
- Rendering regression checks geometry, placement, and tolerant color properties rather than
  cross-platform pixel identity.

## Required tests

Track/segment ordering, IDs, fixed attributes, skipped-segment and vertex-data warnings, dateline,
packed-coordinate, mixed waypoint/track query, viewer, and tolerant rendering-regression tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-gpx:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep source order equal to document order and use the approved one-based track/segment IDs. Do not
silently reinterpret routes as tracks or retain unbounded per-vertex metadata.

Completed with bounded GPX track and segment state machines that emit ordinary packed
`LineStringGeometry` records in source order, inherit the approved fixed track attributes, preserve
literal dateline coordinates, and retain stable warnings for short segments and discarded
per-vertex data. The new local-file GPX viewer owns the source through `MapView`; its offscreen tests
provide tolerant waypoint/line placement and color evidence through the existing
`renderRegression` lane. Exhaustive grammar/limit closure and independent fixtures remain G10-052.
