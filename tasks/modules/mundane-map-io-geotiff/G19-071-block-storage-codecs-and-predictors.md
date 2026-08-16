# G19-071 — GeoTIFF block storage, codecs, and predictors

Status: Proposed
Depends on: G19-044, G19-070
Gate: G19
Type: HITL

## Goal

Decode the declared common geospatial strip/tile, chunky/planar, compression, and predictor matrix
with bounded work and exact malformed-stream diagnostics.

## Context

Current blocks are chunky and use None, Deflate, or PackBits without predictors. LZW, TIFF JPEG,
planar storage, and floating/horizontal prediction are common interoperability gaps.

## Scope

- Complete strip/tile geometry, edge padding, chunky/planar indexing, sparse/missing block policy,
  encoded overlap, and aggregate decoded-work accounting.
- Add bounded TIFF LZW, Deflate/Adobe Deflate, PackBits, and common new-style TIFF JPEG decoding;
  retain None and stably reject old-style JPEG and undeclared vendor codecs.
- Implement horizontal and floating-point predictors for valid sample/bit/byte-order combinations.
- Reuse the approved common JPEG decoder through an explicit module boundary instead of adding a
  second JPEG implementation.
- Bound dictionaries, coefficients, segments, expansion, checkpoints, temporary buffers, and total
  request work before publication.

## Out of scope

- CCITT/fax workflows, old-style JPEG, JBIG, LERC, WebP, Zstandard, and other vendor codecs.

## Acceptance criteria

- Cross-producer strips/tiles and chunky/planar files decode identically for every declared codec
  and predictor combination.
- Invalid codec state, predictor arithmetic, expansion, and aggregate bombs fail before excess work
  and do not poison reusable sources.
- JPEG reuse preserves the image module's decode-only boundary.

## Required tests

- Independent codec/predictor corpus across byte orders, containers, layouts, edge blocks, and sample
  widths; malformed dictionaries/packets/tables/restarts, bombs, cancellation, overflow, and limits.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, image/GeoTIFF corpus lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact codec/predictor matrix and licenses/provenance of
independently generated fixtures before completion.
