# G19-062 — Explicit DTED cell catalog and selection

Status: Proposed
Depends on: G19-061
Gate: G19
Type: AFK

## Goal

Index an explicitly supplied bounded set of DTED cells and deterministically select cells and levels
for point and regional terrain requests.

## Context

Opening one cell at a time does not provide expert regional workflows. Catalog identity, overlap,
duplicate, missing-cell, and level policy must be settled before mosaicing.

## Scope

- Register caller-supplied paths/handles or an explicit manifest; inspect no ambient directories.
- Index standard cell origin, extent, level, edition, and immutable metadata with prospective entry,
  path, metadata-byte, descriptor, and duplicate limits.
- Freeze duplicate/overlap precedence, requested-versus-best-available level selection, partial-cell
  availability, antimeridian, pole-zone, and shared-edge ownership policies.
- Expose bounded point/cell/range discovery and stable missing/ambiguous/incompatible diagnostics.
- Coordinate lazy cell opening, cancellation, cache ownership, replacement, and exact close.

## Out of scope

- Recursive filesystem/network discovery and assembling one output elevation grid.

## Acceptance criteria

- Catalog results are independent of registration/hash iteration order and never silently choose
  between conflicting equal-precedence cells.
- Antimeridian, latitude-zone, multi-level, duplicate, missing, and partial-cell cases follow the
  documented policy.
- Rejected registration and close leave no retained file or cache ownership.

## Required tests

- Adjacent/overlapping/missing L0/L1/L2 manifests, duplicate editions, boundary/pole/dateline cells,
  explicit-handle ownership, cancellation, concurrency policy, and every aggregate limit.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, `./gradlew dtedCorpus
--console=plain`, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
