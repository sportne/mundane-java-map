# G19-043 — JPEG common-interchange color, metadata, and orientation

Status: Proposed
Depends on: G19-042
Gate: G19
Type: HITL

## Goal

Interpret the color, profile, density, and orientation conventions used by common JPEG interchange
files through deterministic bounded rules.

## Context

The current decoder is grayscale/RGB-oriented, ignores EXIF orientation and ICC application, and
rejects common CMYK/YCCK data. T.81 coding alone does not settle the application-marker precedence
used by real JFIF, Exif, Adobe, and ICC producers.

## Scope

- Pin the JFIF (ITU-T T.871), Exif, Adobe APP14, and ICC specifications/versions that define the
  approved interchange profile.
- Decode grayscale, YCbCr, RGB, CMYK, and YCCK component interpretations with explicit marker,
  component-ID, and fallback precedence.
- Assemble, validate, limit, and apply split ICC profiles; define behavior for missing, duplicate,
  conflicting, or unsupported profiles.
- Apply all Exif orientation values exactly once and expose bounded useful density/orientation/color
  metadata without retaining arbitrary APP payloads.
- Freeze output color space, alpha, precision, conversion tolerance, and JFIF/Exif/Adobe/ICC
  precedence in the module capability matrix and public documentation.

## Out of scope

- General Exif editing/preservation, arbitrary application metadata APIs, JPEG encoding/transcoding,
  and the coding families excluded by G19-042.

## Acceptance criteria

- Common grayscale/YCbCr/RGB/CMYK/YCCK files match independent decoders within the approved color
  tolerance and orientation/dimensions match exactly.
- Marker/profile conflicts resolve deterministically according to documented precedence.
- Split-profile, metadata-length, nesting, and orientation inputs cannot evade byte/work limits or
  cause partial publication.

## Required tests

- JFIF/Exif/Adobe marker permutations, every Exif orientation, split ICC, grayscale, YCbCr, RGB,
  CMYK, YCCK, density, component-ID fallback, and independent producer corpus tests.
- Missing/duplicate/conflicting profile chunks, malformed TIFF-in-Exif, oversized APP segments,
  unsupported profiles, truncation, cancellation, and allocation tests.

## Validation

Run `./gradlew :modules:mundane-map-io-image:check --console=plain`, its approved JPEG corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the pinned convention versions, color/metadata precedence,
output tolerance, corpus provenance/licensing, and independent-decoder evidence before completion.
