# G19-040 — Static PNG samples, color types, and Adam7

Status: Proposed
Depends on: G18-061
Gate: G19
Type: HITL

## Goal

Decode every normative static PNG Third Edition sample layout and interlace combination through the
existing bounded toolkit-neutral decoder contract.

## Context

The current pure-Java PNG path rejects standard 16-bit samples and does not implement the complete
packed-sample and Adam7 matrix. Color-management and ancillary-chunk semantics are separated into
G19-041 so sample reconstruction can be reviewed independently.

## Scope

- Pin W3C PNG Third Edition, 24 June 2025, as the normative PNG baseline.
- Decode every valid grayscale, truecolor, indexed-color, grayscale-alpha, and truecolor-alpha
  bit-depth combination, including packed 1/2/4-bit and 16-bit samples.
- Implement all five filters for non-interlaced images and each of the seven Adam7 passes.
- Validate applicable `IHDR`, `PLTE`, `IDAT`, `IEND`, compression, filter, interlace, palette, and
  decoded-length rules before publishing pixels.
- Define deterministic 16-bit-to-project-raster conversion without platform image toolkits.
- Preserve prospective limits for chunks, compressed bytes, inflated bytes, rows, passes, pixels,
  arithmetic, cancellation, and atomic failure.

## Out of scope

- PNG color-management precedence and broad ancillary metadata, assigned to G19-041.
- Animation playback, PNG encoding, transcoding, and metadata editing.

## Acceptance criteria

- The sample/color-type/interlace matrix in `modules/mundane-map-io-image/CAPABILITIES.md` is
  implemented without broadening the public raster model implicitly.
- Applicable PngSuite sample/filter/interlace fixtures and independent decoder comparisons agree
  within the documented 16-bit conversion rule.
- Malformed passes, filters, palettes, row sizes, and inflate streams fail atomically with stable,
  value-safe diagnostics and bounded allocations.

## Required tests

- Exhaustive valid color-type/bit-depth/filter/interlace matrix tests, including small Adam7 edge
  dimensions and split `IDAT` streams.
- 16-bit conversion, packed-sample, palette, transparency-input, truncation, overflow, cancellation,
  decompression-bomb, and allocation-limit tests.

## Validation

Run `./gradlew :modules:mundane-map-io-image:check --console=plain`, its approved PNG corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the pinned PNG edition, 16-bit output policy, external corpus
provenance, and independent-decoder evidence before completion.
