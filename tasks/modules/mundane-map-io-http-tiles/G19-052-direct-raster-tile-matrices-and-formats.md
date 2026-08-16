# G19-052 — Direct raster tile matrices and formats

Status: Proposed
Depends on: G19-014, G19-050
Gate: G19
Type: AFK

## Goal

Generalize explicitly configured raster tile acquisition beyond one 256-pixel Web Mercator XYZ grid.

## Context

The current client hard-codes canonical Web Mercator XYZ zoom 0–22, square 256-pixel tiles, and a
template containing exactly `{z}`, `{x}`, and `{y}`. This prevents direct use of TMS row order,
variable tile sizes, other registered tile matrices, and common scale-factor URL profiles.

## Scope

- Accept an approved neutral G19 tile-matrix-set definition while retaining the current direct XYZ
  convenience API.
- Support explicit XYZ and TMS row conventions, bounded matrix identifiers, nonzero origins, variable
  rectangular tile dimensions, bounded zoom/matrix ranges, and declared coverage limits.
- Define a closed safe URI-template placeholder/encoding grammar for matrix, row, column, and approved
  scale/format tokens without becoming a general URI-template engine.
- Decode any explicitly registered bounded raster media profile whose dimensions match the selected
  matrix; keep content-type, signature, and decoder selection deterministic.
- Compute request regions, clipping, sparse/missing tiles, envelopes, resampling, cache identity, and
  aggregate fan-out prospectively for non-default matrices.

## Out of scope

- Metadata discovery, vector tile decoding, arbitrary RFC 6570 templates, reprojection during tile
  acquisition, or guessing a matrix from URL structure.

## Acceptance criteria

- Direct Web Mercator XYZ remains source-compatible and produces identical requests/results.
- Approved XYZ/TMS, rectangular/variable-size, sparse, scale, and non-Web-Mercator matrix fixtures
  produce exact bounded requests and raster envelopes.
- Unsupported placeholders, formats, dimensions, matrices, or excessive fan-out fail before network
  work with stable diagnostics.

## Required tests

- XYZ/TMS row, matrix identifier, origin, rectangular/variable tile size, scale token, format,
  coverage, sparse/missing, clipping, envelope, cache-key, and registered-decoder fixtures.
- Template injection/escaping, arithmetic/coordinate overflow, matrix/fan-out/byte limits,
  cancellation, partial batch, atomic publication, and current-API compatibility tests.

## Validation

Run `./gradlew :modules:mundane-map-io-http-tiles:check --console=plain`, its matrix/network fixture
lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
