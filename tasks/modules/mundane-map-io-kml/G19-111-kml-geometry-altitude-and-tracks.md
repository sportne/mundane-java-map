# G19-111 — KML geometry, altitude, and tracks

Status: Proposed
Depends on: G19-010, G19-011, G19-110
Gate: G19
Type: HITL

## Goal

Implement complete KML 2.3 geometry, coordinate, altitude, Track, and MultiTrack semantics and dimensional mapping.

## Context

The current subset is primarily XY and homogeneous; it loses altitude and cannot represent heterogeneous
MultiGeometry, Track/MultiTrack, angles, interpolation, or 2.3 altitude behavior.

## Scope

- Add Point, LineString, LinearRing, Polygon, heterogeneous MultiGeometry, Track, and MultiTrack object values.
- Implement coordinate/angle/time tuple cardinality, Z and track-time retention, interpolation, model angles,
  empty/degenerate structures, ring validity, and deterministic object-local identity.
- Implement altitude/sea-floor modes, altitude offset, extrude, tessellate, WGS 84/vertical reference,
  terrain/bathymetry availability, and documented 2D projection/fallback behavior.
- Map to dimensional neutral geometry without flattening heterogeneous membership or track time/angles.
- Bound coordinates/rings/components/tracks/samples/topology/conversion/terrain work and owned bytes prospectively.

## Out of scope

- A terrain engine, bathymetry service, route interpolation beyond KML, and general 3D rendering.

## Acceptance criteria

- Supported geometries round-trip through the domain/writer and retain membership, Z, time, angles, and altitude mode.
- 2D feature projection is deterministic and reports unavailable vertical operations rather than guessing.
- Invalid/over-budget geometry fails atomically with stable diagnostics.

## Required tests

- Geometry/coordinate/Z/time/angle/altitude/extrude/tessellate/track/multitrack matrix and OGC fixtures.
- Dateline/pole/ring/degenerate/precision/terrain-unavailable/geometry-bomb and cross-renderer tests.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, OGC/geometry corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves altitude/2D projection policy, tolerances, and external evidence.
