# G19-154 — Complete raster tile-pyramid profile

Status: Proposed
Depends on: G19-014, G19-044, G19-150, G19-151
Gate: G19
Type: AFK

## Goal

Complete standard PNG/JPEG and optionally registered WebP raster tile pyramids, matrices, windows, and CRUD preparation.

## Context

The reader supports a bounded PNG/JPEG subset but lacks full matrix validation, alternate zoom intervals,
WebP, mixed encoding policy, empty/partial pyramids, bulk updates and neutral tile-matrix integration.

## Scope

- Implement complete tile matrix set/table schemas, matrix/pixel/tile sizes, bounds, row/column/zoom domains and extents.
- Implement factor-two and registered `gpkg_zoom_other` intervals through the neutral OGC tile matrix model.
- Decode standard PNG/JPEG and optionally registered WebP with exact sniff/declaration/color/alpha/no-data policy;
  write only caller-supplied encoded tiles accepted by an explicit validating tile-codec capability.
- Add bounded tile/window/pyramid sources, caches, wrap, resampling, empty/partial coverage and writer preparation.
- Define tile insert/update/delete/bulk-load matrix/statistics maintenance for transactional cards.
- Bound levels/matrices/tiles/blobs/pixels/cache/requests/concurrency/output/owned bytes/work.

## Out of scope

- Inferring undeclared private encodings and claiming generic image blobs as conforming raster tiles.

## Acceptance criteria

- Applicable OGC tiles/zoom/WebP fixtures and independent producer pyramids read correctly; encoded tile blobs
  round-trip unchanged when their declared validating codec permits writing.
- Matrix/window/wrap/cache behavior is deterministic at all row/column/zoom/extent boundaries.
- Malformed/contradictory/over-budget tile data fails before decode allocation or partial source publication.

## Required tests

- Schema/matrix/zoom/extent/empty/partial/PNG/JPEG/WebP/mixed-declaration/window/cache matrices.
- Corrupt blobs, decompression/dimension bombs, boundaries, cancellation, aggregate limits and render parity.

## Validation

Run module/tile/image/render checks, qualityGate, and `git diff --check`.

## Notes

None.
