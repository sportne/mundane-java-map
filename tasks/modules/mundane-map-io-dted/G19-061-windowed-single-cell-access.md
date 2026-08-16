# G19-061 — Windowed single-cell DTED access

Status: Proposed
Depends on: G19-060
Gate: G19
Type: AFK

## Goal

Add bounded random-access profile and rectangular-window reads for one DTED cell without eagerly
materializing the complete grid.

## Context

The released facade validates and loads every post before publishing an in-memory grid. Regional
work needs a reusable single-cell access primitive before a catalog or mosaic can remain bounded.

## Scope

- Add an immutable cell descriptor and explicit eager versus windowed open mode.
- Preflight headers, layout, metadata, and file identity once; validate every accessed profile's
  frame, sequence, checksum, signed magnitude, void state, and mutation consistency.
- Read exact profile/range windows with cancellation and prospective sample, byte, allocation, and
  retained-cache limits.
- Define external serialization, descriptor ownership, bounded profile caching, invalidation, and
  exactly-once close behavior.
- Preserve the eager facade and identical values/diagnostics for equivalent reads.

## Out of scope

- Multi-cell selection, mosaics, filesystem discovery, reprojection, and resampling.

## Acceptance criteria

- A small window from the largest standard cell does not allocate or read the whole cell.
- Eager and windowed paths return identical post/no-data results and stable corruption diagnostics.
- Cancellation, mutation, I/O failure, and close release descriptors and cached buffers exactly once.

## Required tests

- L0/L1/L2 edge/interior windows, reversed row storage, voids, checksum, short/zero reads, mutation,
  cancellation, concurrent access-policy, descriptor-limit, cache-eviction, overflow, and cleanup.
- Environment-labelled allocation/read evidence for a small Level 2 window.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, its focused performance evidence,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
