# G19-130 — Complete RFC 7946 geometry and positions

Status: Proposed
Depends on: G19-001, G19-011
Gate: G19
Type: AFK

## Goal

Complete all RFC 7946 geometry types, empty forms, GeometryCollection nesting, and bounded position dimensions.

## Context

The released adapter supports six non-empty XY geometry families but rejects GeometryCollection, empty geometries,
third/further position elements, and null/heterogeneous structures needed for full document fidelity.

## Scope

- Add immutable GeometryCollection and preserve heterogeneous/nested membership through neutral geometry/domain APIs.
- Support exact RFC-compatible empty coordinate structures and null Feature geometry without inventing geometry.
- Parse/retain longitude/latitude, optional altitude Z, and bounded further numeric position elements as uninterpreted ordinates.
- Define consistent geometry dimension, position cardinality, WGS 84 axis/range, finite-number, altitude-unit, collection-depth,
  empty/degenerate, and neutral conversion behavior.
- Bound geometries/collections/nesting/positions/ordinates/number tokens/coordinates/owned bytes/work prospectively.

## Out of scope

- Assigning M/time semantics to fourth ordinates, CRS inference, coordinate repair, TopoJSON, and JSON-FG.

## Acceptance criteria

- Every RFC geometry/empty/null shape maps to immutable values and semantically round-trips through the writer.
- Heterogeneous collections and all retained ordinates survive supported neutral conversions without flattening.
- Invalid range/dimension/cardinality/nesting/non-finite/over-budget input fails atomically with stable paths.

## Required tests

- Seven geometry types, empty/null/nested collections, 2D/Z/further-ordinate, range/cardinality/dimension matrices.
- Deep/wide collections, huge/non-finite numbers, aggregate coordinate/ordinate/byte/work limits, and corpus fixtures.

## Validation

Run `./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain`, JSON corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
