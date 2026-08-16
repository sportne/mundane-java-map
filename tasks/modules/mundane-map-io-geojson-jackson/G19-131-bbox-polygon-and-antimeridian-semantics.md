# G19-131 — Bbox, polygon, and antimeridian semantics

Status: Proposed
Depends on: G19-011, G19-130
Gate: G19
Type: AFK

## Goal

Implement complete RFC 7946 bounding-box, polygon-ring, winding, antimeridian, pole, and validity behavior.

## Context

Current bbox values are validated but not retained comprehensively, dimensions/wrap are incomplete, and canonical
writer normalization needs an explicit reader-compatible polygon/antimeridian contract.

## Scope

- Add immutable 2D/N-dimensional bbox values at Geometry, Feature, FeatureCollection, and GeometryCollection scopes.
- Validate twice-dimension cardinality, finite values, longitude/latitude/Z/tail domains, coordinate-dimension consistency,
  containment/derivation policy, pole cases, and antimeridian-spanning west/east ordering.
- Implement polygon ring cardinality/closure/topology and RFC right-hand-rule behavior: compatible reading/reporting of
  opposite winding and deterministic canonical writer normalization without changing topology.
- Define dateline-crossing geometry, envelope/query/world-wrap, precision/negative-zero, and derived extent behavior.
- Bound ring/topology/orientation/bbox/derivation work and temporary storage; diagnose by stable object/ring indices.

## Out of scope

- Silently repairing invalid rings/coordinates, splitting RFC geometry on storage, and geodesic validity beyond the profile.

## Acceptance criteria

- Scoped bboxes and antimeridian/pole cases retain exact RFC semantics through reader/domain/source/writer.
- Accepted opposite winding writes canonical right-hand-rule output with an explicit normalization observation.
- Invalid topology/dimension/bbox and all work limits fail before source/output publication.

## Required tests

- Object-scope/2D/Z/tail bbox, antimeridian/pole, winding/hole/closure/topology, derived/declared extent matrices.
- Precision/negative-zero, invalid/mismatched boxes, huge rings/topology work, wrap/query, and independent fixtures.

## Validation

Run `./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain`, geometry/corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
