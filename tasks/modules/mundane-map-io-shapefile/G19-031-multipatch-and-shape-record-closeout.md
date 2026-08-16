# G19-031 — MultiPatch and shape-record closeout

Status: Proposed
Depends on: G19-030
Gate: G19
Type: HITL

## Goal

Complete the ESRI Shapefile shape-code reader with explicit MultiPatch semantics and cross-family
record validation.

## Context

MultiPatch combines Z/M arrays with TriangleStrip, TriangleFan, OuterRing, InnerRing, FirstRing, and
Ring part types. Treating it as an ordinary polygon would invent topology or discard the format's
surface intent.

## Scope

- Decode shape code 31 and every standard MultiPatch part type into an approved lossless neutral
  representation.
- Validate part ordering, minimum vertices, ring grouping, Z/M layouts, ranges, record/file bounds,
  and no-data measurement semantics.
- Close null-record and homogeneous file/record type rules across every standard shape code.
- Define behavior for degenerate rings, duplicate vertices, empty parts, ring orientation, and
  semantically ambiguous FirstRing/Ring groups without fabricating surfaces.
- Update the public geometry capability matrix and stable invalid/unsupported diagnostics.

## Out of scope

- Repairing invalid topology or inferring a watertight solid not encoded by the part metadata.
- Geometry export, assigned to G19-034.

## Acceptance criteria

- Every shape code in the July 1998 technical description has a tested read path or a precise
  semantic-invalid result.
- MultiPatch part identity and ordinates survive the neutral representation without polygon coercion.
- Cross-family mismatch, malformed parts, offsets, ranges, and hostile counts fail atomically before
  large allocation.

## Required tests

- Independent fixtures for all six MultiPatch part types, mixed valid groups, Z/M/no-data values,
  null records, and each standard shape code in sequential and indexed modes.
- Invalid ordering/minimums, ambiguous groups, type mismatch, truncation, overflow, cancellation,
  limit, and corpus-interoperability tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved shape corpus
lane, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the neutral MultiPatch representation, ambiguity policy, and
independent corpus evidence before completion.
