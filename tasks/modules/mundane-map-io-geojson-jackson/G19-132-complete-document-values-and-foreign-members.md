# G19-132 — Complete document values and foreign members

Status: Proposed
Depends on: G19-002, G19-130, G19-131
Gate: G19
Type: AFK

## Goal

Represent complete GeoJSON objects, Feature IDs/properties, collections, and semantic foreign members using bounded
immutable toolkit-neutral JSON values.

## Context

Nested properties and foreign members are ignored/rejected or flattened to limited attributes, and the public API does
not retain the full GeoJSON document independently of its feature-source projection.

## Scope

- Add immutable JSON null/boolean/exact-number/string/array/object values without exposing Jackson node types.
- Model Geometry, GeometryCollection, Feature, and FeatureCollection standard members, optionality, nulls, IDs,
  collection order, bbox, and full structured properties/foreign members.
- Reject duplicate keys; define exact per-object standard/foreign collision tables and deterministic member semantics.
- Add immutable explicit object-kind/member codecs operating on safe JSON values with registry collision/version/error/cost rules.
- Expose complete documents beside stable feature-source projections and bound depth/members/keys/strings/numbers/nodes/
  arrays/properties/foreign values/codecs/owned bytes and aggregate work.

## Out of scope

- Guessing foreign-member meanings, public mutable/Jackson trees, JSON references/schemas, and duplicate-key preservation.

## Acceptance criteria

- Complete properties/IDs/nulls/foreign members round-trip semantically at every allowed object scope.
- Feature projection retains stable identity/order and exposes domain values that do not fit a flat attribute schema.
- Duplicate/colliding/hostile/over-budget values fail before partial document/source construction.

## Required tests

- Full JSON value/number/ID/null/property/foreign-scope/collision/order/codec and object-root matrix.
- Duplicate keys, deep/wide/long values, exact-number boundaries, codec failures/cost, source query/lifecycle, and fuzz tests.

## Validation

Run `./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain`, JSON corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
