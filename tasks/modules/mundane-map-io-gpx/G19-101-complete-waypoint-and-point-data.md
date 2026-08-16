# G19-101 — Complete waypoint and point data

Status: Proposed
Depends on: G19-100
Gate: G19
Type: AFK

## Goal

Implement the complete GPX 1.1 waypoint type once and reuse it consistently for waypoints, route points,
and track points.

## Context

The current parser retains only coordinates and a small display/time/elevation subset while validating and
discarding magnetic, geoid, quality, link, source, satellite, dilution, and DGPS fields.

## Scope

- Add immutable values for longitude/latitude, elevation, time, magnetic variation, geoid height, name,
  comment, description, source, repeated links, symbol, type, fix, satellites, HDOP/VDOP/PDOP, DGPS age,
  station ID, and the extension slot.
- Implement exact GPX numeric/enumeration/range/cardinality/order rules, including finite-value,
  non-negative, degree, DGPS-station, satellite-count, and date-time behavior.
- Preserve semantic decimal/date-time precision sufficient for deterministic canonical writing without
  retaining incidental source spelling.
- Share one validation/model path across root waypoints, route points, and track points; prevent scope-specific drift.
- Bound point counts, repeated links, text/URI values, numeric work, owned bytes, and diagnostics prospectively.

## Out of scope

- Route/track container behavior, vendor interpretation of extensions, and navigation-quality inference.

## Acceptance criteria

- Every standard `wptType` field is retained and validated identically in all three GPX point contexts.
- Invalid enumeration/range/order/cardinality input fails with stable field/scope diagnostics and no value leaks.
- Domain values can be serialized without dropping any supported standard point information.

## Required tests

- Complete field/context/optional/cardinality/order/enumeration/numeric/time/link matrix and schema-derived fixtures.
- Boundary decimals, time offsets, non-finite/huge values, repeated text/link limits, cancellation, and memory ceilings.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, XML corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
