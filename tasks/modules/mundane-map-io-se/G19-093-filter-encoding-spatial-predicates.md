# G19-093 — Filter Encoding 1.1 spatial predicates

Status: Proposed
Depends on: G19-010, G19-011, G19-092
Gate: G19
Type: HITL

## Goal

Implement the bounded FE 1.1 spatial-predicate profile with explicit geometry, CRS, distance-unit,
precision, and topology semantics.

## Context

All spatial predicates and geometry literals are currently unsupported. A correct implementation
depends on the common CRS and topology foundations rather than adapter-local approximations.

## Scope

- Pin the GML 3.1.1 geometry-literal subset used by FE 1.1 and map it to neutral geometry values.
- Add BBOX, Equals, Disjoint, Intersects, Touches, Crosses, Within, Contains, Overlaps, DWithin, and
  Beyond over approved geometry families and property/geometry expressions.
- Define axis order, CRS resolution/transformation, envelope, empty/invalid geometry, boundary,
  dimensional projection, tolerance, distance, and unit behavior.
- Bound geometry literals, transformations, topology operations, indexes, distance work, and temporary
  storage prospectively; reject unsupported CRS/units before evaluation.
- Provide stable operation/geometry/CRS/unit/limit diagnostics without retaining source coordinates.

## Out of scope

- General GML documents, geodesic buffering beyond the approved core profile, FES 2.0 temporal
  predicates, and datastore-specific spatial SQL.

## Acceptance criteria

- Each spatial operator agrees with the pinned FE/GML/core-topology contract across supported CRS pairs.
- Results are deterministic at declared numeric/topology tolerances and consistent in AWT/Vaadin rules.
- Invalid or over-budget geometry/CRS/unit input fails without partial portrayal.

## Required tests

- Operator/geometry/empty/boundary/CRS/axis/unit/distance matrix and OGC-derived fixtures.
- Dateline/pole/precision cases, invalid topology, transformation failure, geometry bombs, and work limits.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, OGC/topology/corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the GML subset, CRS/distance profile, tolerances, and external evidence.
