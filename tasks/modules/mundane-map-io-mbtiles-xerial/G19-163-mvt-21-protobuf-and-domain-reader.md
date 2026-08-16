# G19-163 — MVT 2.1 protobuf and domain reader

Status: Proposed
Depends on: G19-001, G19-002, G19-011, G19-161
Gate: G19
Type: HITL

## Goal

Implement a complete bounded Mapbox Vector Tile 2.1 reader and neutral feature projection for
`format=pbf` MBTiles.

## Context

MBTiles 1.3 standardizes gzip-wrapped MVT storage and required layer metadata, but the module rejects
PBF and has no vector tile model.

## Scope

- Implement the exact MVT 2.1 protobuf wire schema directly without a protobuf runtime or generated/
  discovered classes: layers, versions, names, extents, dictionaries, scalar values, features and fields.
- Decode every point/line/polygon command sequence, zigzag/cursor/count rule, winding/ring assignment,
  tile buffer, ID/tag/value relation, unknown field, overflow, and malformed-state rule.
- Expose immutable tile-local records plus bounded projection/clipping into neutral map geometry and
  feature sources with deterministic IDs/order and explicit quantization/validity policy.
- Parse/reconcile required MBTiles `json.vector_layers`, zooms/fields/descriptions and semantic
  tilestats/unknown values using pinned Jackson; bound gzip/protobuf/geometry/JSON and aggregate work.

## Out of scope

- General protobuf APIs, MVT geometry collections, server behavior, automatic style selection, and
  silently repairing malformed or topologically invalid commands.

## Acceptance criteria

- Official and independent MVT 2.1 tiles decode all standard value/geometry/layer cases exactly.
- Metadata/payload strict and inspection modes report precise deterministic mismatches.
- Hostile gzip/protobuf/command/geometry/metadata input fails prospectively without partial features.

## Required tests

- Full wire type, packed field, layer/version/name/extent/dictionary/value/id/tag/unknown-field and
  point/multi/line/polygon/hole/buffer/winding/quantization/projection matrices.
- Truncated/overflow/invalid varint/wire/command/count/cursor/ring/tag/index/gzip/JSON bombs, limits,
  fuzz seeds, source query/lifecycle, official fixtures and independent producer corpus.

## Validation

Run MBTiles vector checks and MVT corpus/fuzz lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve the exact MVT 2.1 requirement map, topology/quantization policy, corpus, and
direct protobuf implementation before closing the card.
