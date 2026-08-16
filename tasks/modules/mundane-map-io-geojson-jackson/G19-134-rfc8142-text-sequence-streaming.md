# G19-134 — RFC 8142 text-sequence streaming

Status: Proposed
Depends on: G19-132
Gate: G19
Type: AFK

## Goal

Add bounded incremental RFC 8142 GeoJSON Text Sequence reading and writing without retaining an entire stream.

## Context

The adapter currently accepts one complete RFC 7946 JSON document. GeoJSON Text Sequences use an explicit
record-separator framing protocol with different error isolation, recovery, ownership, and partial-output semantics.

## Scope

- Add pull/cursor APIs that recognize ASCII RS (`0x1E`), exactly one UTF-8 RFC 7946 object per record, and the
  recommended trailing LF while preserving stable record indices.
- Define strict default behavior plus explicit bounded recovery to the next RS after malformed or truncated records;
  never splice JSON texts or treat NDJSON as RFC 8142.
- Enforce per-record and aggregate byte/object/value/geometry/work limits prospectively, including bounded scan work.
- Specify input ownership, cancellation, cursor close, stream failure, cleanup aggregation, and record-local diagnostics.
- Add a streaming writer that preflights and commits one complete frame at a time, plus transactional atomic file output.
- Publish and verify `application/geo+json-seq` media-type integration without ambient content negotiation.

## Out of scope

- JSON Lines/NDJSON, arbitrary concatenated JSON, unframed recovery, parallel record reordering, and unbounded replay.

## Acceptance criteria

- Large sequences are processed with bounded retained state and stable record order/index diagnostics.
- Strict and recovery modes handle malformed/truncated framing exactly as documented without corrupting later records.
- Writer failures expose exact committed-record semantics; transactional file failure preserves the prior target.

## Required tests

- Empty/single/many records, chunk boundaries around RS/UTF-8/LF, missing/extra delimiters, and trailing truncation.
- Strict/recovery scan ceilings, per-record/aggregate limits, cancellation, cursor/sink failures, and cleanup aggregation.
- Independent RFC 8142 producer/consumer fixtures and sequence writer/read-back interoperability.

## Validation

Run `./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain`, sequence corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
