# G19-164 — MVT 2.1 writer and MBTiles metadata

Status: Proposed
Depends on: G19-163
Gate: G19
Type: AFK

## Goal

Encode deterministic MVT 2.1 payloads and their required MBTiles vector metadata without semantic loss.

## Context

Transactional MBTiles vector creation needs a standards-complete encoder, not merely storage of opaque
caller blobs. Encoding must make clipping, quantization, dictionary, and metadata decisions explicit.

## Scope

- Build immutable bounded vector tiles/layers/features and encode canonical MVT 2.1 protobuf and gzip.
- Define caller-controlled tile clipping/buffering/quantization/simplification policy; validate geometry,
  winding, IDs, attributes and precision before emitting bytes and reject unapproved loss.
- Canonically order layers/features/dictionaries/fields/commands, encode every scalar value, and produce
  deterministic gzip headers/trailers and bytes.
- Generate/reconcile complete `vector_layers`, field types, descriptions/zoom ranges, optional tilestats,
  exact unknown metadata preservation, limits, cancellation, and atomic output.

## Out of scope

- Automatic cartographic generalization, hidden topology repair, vector-tile serving, protobuf runtime,
  and claiming byte identity with other valid non-canonical encoders.

## Acceptance criteria

- Every representable MVT 2.1 domain value writes, reopens, and preserves approved semantics deterministically.
- Geometry/value/metadata that cannot satisfy the selected policy fails before output replacement.
- Independent MVT consumers accept the generated tiles and metadata.

## Required tests

- Read/write/reopen goldens for every layer/value/geometry/buffer/extent/quantization/dictionary/metadata case.
- Determinism, independent consumers, precision/loss refusal, huge output, cancellation, sink failure and cleanup tests.

## Validation

Run MBTiles MVT writer and independent consumer lanes, then qualityGate and `git diff --check`.

## Notes

None.
