# G19-102 — Routes, tracks, segments, and order

Status: Proposed
Depends on: G19-101
Gate: G19
Type: AFK

## Goal

Complete GPX 1.1 route and track containers, ordered segments, identities, and point membership.

## Context

Routes are rejected outright. Tracks are flattened into geometry while much container and point metadata is
discarded, preventing complete interchange or reliable read-modify-write behavior.

## Scope

- Add immutable route values covering name/comment/description/source, repeated links, number, type,
  extensions, and ordered full route points.
- Complete track values covering the corresponding metadata, extensions, ordered segments, and ordered full
  track points; define schema-valid empty/degenerate segment/container behavior without invented geometry.
- Preserve top-level waypoint/route/track order required by GPX while retaining order within routes/segments.
- Assign deterministic bounded document-local identities to routes, tracks, segments, and points without using
  mutable names or source paths.
- Bound containers, segments, points, links, coordinates, text, owned memory, and aggregate parsing work before allocation.

## Out of scope

- Route planning, turn instructions, track simplification, activity metrics, map matching, and writing.

## Acceptance criteria

- All schema-valid route/track structures and fields are retained without warnings or silent flattening loss.
- Empty/one-point/multi-segment structures remain distinguishable in the domain model.
- Malformed order/cardinality and all aggregate limits fail atomically with stable diagnostics.

## Required tests

- Route/track/container-field/link/number/type/segment/point/order/empty/degenerate matrices.
- Large nested documents, aggregate point/segment/coordinate/byte thresholds, cancellation, and independent fixtures.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, XML corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
