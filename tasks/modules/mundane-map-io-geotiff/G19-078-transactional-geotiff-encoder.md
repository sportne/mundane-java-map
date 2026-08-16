# G19-078 — Transactional GeoTIFF encoder

Status: Proposed
Depends on: G19-077
Gate: G19
Type: AFK

## Goal

Encode the approved plan into deterministic classic-TIFF or BigTIFF tiled GeoTIFF and publish it
transactionally with reader verification.

## Context

The builder defines all semantics and byte planning; this slice owns canonical TIFF/GeoTIFF bytes,
lossless codecs, cancellation, and filesystem failure safety.

## Scope

- Emit sorted TIFF tags and GeoKeys, inline/out-of-line values, georeferencing/CRS metadata, primary/
  mask/overview IFDs, tile offsets/counts, and canonical padding for both containers/byte orders.
- Encode supported raw/display/elevation samples using None, LZW, or Deflate and the valid none/
  horizontal/floating predictor; do not add a JPEG encoder.
- Enforce exact planned offsets/lengths/work and cancellation while reading source blocks, predicting,
  compressing, checksumming/validating, and writing.
- Write a private sibling staging file, flush, reopen through production random access, compare all
  declared metadata/bands/masks/no-data/placement, and commit under explicit create/replace policy.
- Aggregate cleanup failures while preserving an existing target and removing owned staging.

## Out of scope

- COG-specific optimized ordering/conformance, in-place editing, remote writes, and lossy codecs.

## Acceptance criteria

- Equivalent plans produce byte-identical files that reopen to exact declared raw samples and
  metadata.
- Every encode/flush/verify/move/cancel failure preserves the prior target and leaves no staging.
- Largest supported outputs remain within prospective work/allocation/file limits.

## Required tests

- Golden classic/BigTIFF headers/IFDs/tags/keys/tiles for both byte orders; every lossless codec/
  predictor/sample family; masks/overviews/no-data; deterministic output; independent reopen;
  short/failed writes, symlink/path races, flush/move/cleanup failure, cancellation, and limits.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, focused writer evidence, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
