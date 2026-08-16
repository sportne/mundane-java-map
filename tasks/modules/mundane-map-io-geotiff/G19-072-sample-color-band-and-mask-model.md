# G19-072 — GeoTIFF sample, color, band, and mask model

Status: Proposed
Depends on: G19-071
Gate: G19
Type: HITL

## Goal

Support the declared common geospatial sample formats, photometrics, raw bands, alpha, palettes,
color metadata, and mask semantics without forcing all data through 8-bit display RGBA.

## Context

The adapter primarily exposes 8-bit grayscale/RGB display data or one numeric elevation band. Expert
imagery and analysis require lossless band access and deterministic display conversion.

## Scope

- Pin packed 1/2/4-bit, 8/16-bit display samples and bounded unsigned/signed integer and IEEE float
  raw sample profiles, including declared homogeneous/per-band width constraints.
- Implement common WhiteIsZero, BlackIsZero, RGB, palette, YCbCr, and CMYK reading plus associated/
  unassociated alpha and transparency-mask IFD semantics.
- Add immutable lossless raw-band metadata/window access distinct from display snapshots.
- Freeze ICC/chromaticity/transfer/reference-white precedence, YCbCr subsampling/reference-black-
  white behavior, palette scaling, precision, rounding, alpha, and output-color policies.
- Define alpha, mask, and per-dataset no-data precedence with prospective band/profile/palette/mask/
  conversion limits.

## Out of scope

- Domain-specific remote-sensing analytics, spectral interpretation, and arbitrary private metadata.

## Acceptance criteria

- Raw supported sample values round-trip losslessly through window access even when display
  conversion is unavailable or lossy.
- Declared display photometrics match independent readers within pinned numeric/color tolerances.
- Alpha/mask/no-data precedence and orientation are deterministic for every layout.

## Required tests

- Cross-producer packed/integer/float, palette, grayscale, RGB, YCbCr, CMYK, ICC/chromaticity, alpha,
  mask, chunky/planar, band-selection, precision, NaN/infinity, huge-band/profile, and hostile table
  fixtures.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, image/GeoTIFF corpus lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the raw-band public contract, color-conversion tolerances,
and exact sample/photometric matrix before completion.
