# G19-065 — Transactional DTED cell encoder

Status: Proposed
Depends on: G19-064
Gate: G19
Type: AFK

## Goal

Encode the approved builder plan into a canonical, bounded, transactionally published Level 0/1/2
DTED cell.

## Context

The test suite has an independent fixture writer, but production has no encoder, destination policy,
or failure-safe publication contract.

## Scope

- Encode canonical UHL, DSI, ACC, standard data records, sequence/count fields, signed-magnitude
  samples, void words, fixed padding/reserved bytes, and unsigned checksums.
- Compute exact output/work/allocation limits prospectively and checkpoint cancellation while
  traversing profiles and samples.
- Write only to a private sibling staging file, flush, reopen with the production reader, compare the
  complete declared metadata/grid, and commit under an explicit create/replace policy.
- Preserve an existing target on all failures and remove staging artifacts with failure aggregation.
- Define filesystem capability, symlink, path-race, permission, I/O, and atomic-move diagnostics.

## Out of scope

- In-place header/sample editing, multi-cell volume/disc directory products, and automatic source
  terrain transformation.

## Acceptance criteria

- Emitted bytes are deterministic for one builder plan and reopen to the exact declared metadata,
  samples, and no-data mask.
- Any preflight, encode, flush, verification, commit, cancellation, or cleanup failure leaves the
  previous destination intact and no owned staging file behind.
- Writer resource use remains bounded for the largest supported Level 2 latitude-zone cell.

## Required tests

- Golden UHL/DSI/ACC/profile bytes and checksums for every level/latitude zone, positive/negative/zero/
  void extremes, deterministic output, reopen comparison, create/replace, symlink/path race, short/
  failed writes, flush/move/cleanup failure, cancellation at every phase, and output/work limits.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, its focused writer evidence, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
