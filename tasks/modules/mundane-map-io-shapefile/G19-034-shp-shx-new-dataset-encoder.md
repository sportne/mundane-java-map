# G19-034 — SHP/SHX new-dataset encoder

Status: Proposed
Depends on: G19-031
Gate: G19
Type: AFK

## Goal

Encode one homogeneous bounded feature sequence into deterministic new SHP and SHX components.

## Context

Most Shapefile export utility comes from creating a fresh interoperable dataset. Geometry encoding,
offset accounting, and index construction should be correct before filesystem publication and DBF
schema concerns are added.

## Scope

- Add immutable public geometry export options and limits with Javadocs.
- Preflight one homogeneous non-null shape family plus permitted Null Shape records, dimensionality,
  MultiPatch part semantics, finite coordinates, ranges, parts, record counts, and output size.
- Encode deterministic file/record headers, bounds, content lengths, 16-bit-word offsets, shape
  payloads, Z/M ranges/arrays, and SHX entries for every approved export shape code.
- Enforce the declared compatibility byte ceiling before writing and use checked arithmetic for
  every record, offset, and aggregate calculation.
- Support cancellation and cleanup of unpublished component streams without assuming seekable input.

## Out of scope

- DBF/CPG/PRJ encoding, final multi-file commit, in-place updates, heterogeneous splitting, and
  implicit dimensional coercion.

## Acceptance criteria

- Golden SHP/SHX bytes are deterministic and reopen identically through this module and at least two
  independent Shapefile readers.
- Any unrepresentable geometry, dimension, part, record, offset, or size fails during preflight with
  no published component.
- Writer limits and diagnostics are closed, value-safe, and aligned with reader accounting.

## Required tests

- Golden and round-trip fixtures for every approved shape code, null records, multipart/ring cases,
  Z/M/no-data, MultiPatch parts, bounds, and deterministic ordering.
- Homogeneity, dimensional mismatch, non-finite, offset/word/size boundary, cancellation, short-write,
  disk-failure, cleanup, and independent-reader tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved writer corpus
lane, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
