# G19-030 — Z and M shape families

Status: Proposed
Depends on: G19-001, G19-011
Gate: G19
Type: AFK

## Goal

Read every Point, PolyLine, Polygon, and MultiPoint Z/M shape code without ordinate loss.

## Context

The current reader supports only the two-dimensional shape codes. Z/M layouts add required and
optional range/array sections, no-data measurement semantics, and dimensional API requirements that
should be completed independently from MultiPatch surface interpretation.

## Scope

- Decode shape codes 11, 13, 15, 18, 21, 23, 25, and 28 from sequential SHP and indexed SHX paths.
- Validate required Z and optional/required M ranges and arrays against exact record lengths.
- Preserve Z/M ordinates and the Shapefile M no-data sentinel through the approved neutral geometry
  representation without converting absence into an ordinary number.
- Apply file/record bounds, parts, coordinate, byte, allocation, and query accounting prospectively.
- Publish stable diagnostics for truncated, contradictory, non-finite, out-of-bounds, or unsupported
  dimensional data.

## Out of scope

- MultiPatch part/surface semantics, assigned to G19-031.
- Geometry export, assigned to G19-034.

## Acceptance criteria

- Every standard non-MultiPatch Z/M shape layout is decoded exactly or rejected for a named semantic
  invalidity rather than because dimensional data are unsupported.
- Sequential and SHX-indexed reads yield identical geometry and diagnostics.
- No-data M, optional M sections, dimension ranges, malformed offsets, and large counts cannot cause
  ordinate loss, partial records, overflow, or unbounded allocation.

## Required tests

- Authoritative and independently generated fixtures for every Z/M code, multipart arrangement,
  optional-M case, no-data sentinel, empty/null record, and dimensional boundary.
- Truncation at every range/array boundary, inconsistent ranges, record-length overflow, invalid part,
  cancellation, allocation, sequential/indexed parity, and stable-diagnostic tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved Shapefile corpus
lane, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
