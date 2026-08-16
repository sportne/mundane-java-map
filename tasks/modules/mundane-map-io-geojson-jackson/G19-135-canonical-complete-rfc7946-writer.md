# G19-135 — Canonical complete RFC 7946 writer

Status: Proposed
Depends on: G19-131, G19-132, G19-133, G19-134
Gate: G19
Type: AFK

## Goal

Extend deterministic GeoJSON output to the complete approved RFC 7946 document model and strict RFC 8142 frames.

## Context

The current writer emits a bounded deterministic FeatureCollection for six non-empty XY geometry families, but it
cannot write complete object roots, collections, dimensions, bboxes, structured values, or retained foreign members.

## Scope

- Write every approved Geometry, Feature, FeatureCollection, empty/null form, position dimension, bbox, ID, property,
  and foreign-member value from the complete immutable model.
- Normalize polygon rings to the RFC right-hand rule and preserve antimeridian, altitude, and uninterpreted tail ordinates.
- Emit frozen standard-member order followed by foreign members in deterministic Unicode code-point order, stable UTF-8,
  escaping and finite-number formatting, with no insignificant whitespace.
- Reject legacy `crs`, non-WGS-84/untransformed values, collisions, invalid geometry, and non-representable domain values
  before committing output; expose explicit strict-transform entry points for legacy input results.
- Share canonical object serialization with the RFC 8142 writer while retaining its record-commit semantics.
- Enforce exact byte/value/geometry/codec/work ceilings, atomic filesystem replacement, cancellation, and cleanup aggregation.

## Out of scope

- RFC 8785 conformance, source-lexical preservation, legacy CRS output, in-place file editing, TopoJSON, and JSON-FG.

## Acceptance criteria

- Identical semantic values and options produce byte-identical complete documents and sequence records.
- All approved data survives semantic write/read round trips without losing structured or foreign-member content.
- Any validation, limit, cancellation, sink, or cleanup failure obeys the documented atomic/committed-output boundary.

## Required tests

- Complete object/geometry/empty/dimension/bbox/value/foreign-member/member-order/number/escaping matrices.
- Winding normalization, antimeridian, legacy-transform output, collision/representability and exact-byte limits.
- Byte reproducibility, atomic replacement, failing/short sinks, cancellation, sequence framing, and independent consumers.

## Validation

Run `./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain`, writer interoperability tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

The project profile is deterministic but deliberately does not claim the JSON Canonicalization Scheme.
