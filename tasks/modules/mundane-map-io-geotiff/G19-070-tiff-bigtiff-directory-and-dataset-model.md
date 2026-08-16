# G19-070 — TIFF/BigTIFF directory and geospatial dataset model

Status: Proposed
Depends on: G18-061
Gate: G19
Type: HITL

## Goal

Parse bounded classic TIFF and BigTIFF directory graphs and expose an explicit geospatial primary
dataset with associated reduced-resolution and transparency-mask images.

## Context

The reader currently accepts one sorted classic-TIFF IFD and rejects BigTIFF, next IFDs, SubIFDs,
overviews, masks, and orientation. Container structure must be complete before codecs or COG access.

## Scope

- Pin TIFF 6.0 and the public BigTIFF header, value/offset-width, alignment, type, and IFD profiles.
- Parse bounded next-IFD and SubIFD graphs with cycle, alias, overlap, depth, count, and byte limits.
- Classify one explicit primary geospatial dataset and its reduced-resolution/mask IFDs from standard
  subfile tags; expose deterministic selection or ambiguity diagnostics for unrelated primaries.
- Validate both byte orders, all required field types, sorted/duplicate tag policy, inline/out-of-line
  payloads, orientation 1–8, and unknown/private-tag retention policy.
- Preserve immutable bounded directory metadata without retaining the whole file.

## Out of scope

- General multipage document semantics, arbitrary private-tag interpretation, and image decoding.

## Acceptance criteria

- Equivalent classic TIFF and BigTIFF directory graphs produce the same dataset/association model.
- No cyclic, aliased, overlapping, truncated, or oversized graph causes unbounded traversal or I/O.
- Multiple unrelated primaries require explicit deterministic selection rather than first-IFD choice.

## Required tests

- Both byte orders and containers, inline/out-of-line every declared field type, next/SubIFD
  overview/mask graphs, orientations, multiple primaries, cycles, aliases, overlap, alignment,
  truncation, offset arithmetic, cancellation, and every aggregate limit.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, its corpus lane, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact TIFF 6.0/BigTIFF structural and field-type matrix,
external fixtures, and deliberate general-TIFF exclusions before completion.
