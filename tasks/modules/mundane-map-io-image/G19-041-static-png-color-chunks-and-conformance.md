# G19-041 — Static PNG color, ancillary chunks, and conformance

Status: Proposed
Depends on: G19-040
Gate: G19
Type: HITL

## Goal

Complete PNG Third Edition static-image color interpretation, ancillary-chunk behavior, and decoder
conformance without adding animation playback or writing.

## Context

The current decoder does not apply the standard color-management decision tree and rejects APNG
animation chunks outright. PNG Third Edition defines those as ancillary data and requires every PNG
to retain a static/default image that a non-animation-capable decoder can display.

## Scope

- Implement and document the applicable ordering, multiplicity, validation, and precedence for
  `PLTE`, `tRNS`, `cHRM`, `gAMA`, `iCCP`, `sBIT`, `sRGB`, `cICP`, `mDCV`, and `cLLI`.
- Freeze the project-raster output color space, alpha, precision, rendering-intent, and conflicting-
  metadata policy.
- Validate bounded known ancillary chunks and apply conforming unknown-ancillary behavior without
  retaining attacker-controlled metadata indefinitely.
- Accept a structurally valid APNG-bearing PNG as PNG, ignore `acTL`/`fcTL`/`fdAT` animation data,
  and decode only its standard static/default image.
- Publish exact support wording that claims applicable static PNG decoder behavior, not animated
  playback or an image-authoring surface.

## Out of scope

- Animated-frame delivery, timing, disposal/blending, playback, PNG/APNG encoding, and editing.

## Acceptance criteria

- Color decisions follow the pinned PNG Third Edition precedence and match independent decoders
  within documented transfer/quantization tolerances.
- Conforming files with animation chunks yield their static/default image; malformed animation or
  ancillary structures remain bounded and cannot alter the static decode contract.
- The module capability matrix, public Javadocs, README support statement, and stable diagnostics
  describe the same static-only boundary.

## Required tests

- Gamma/chromaticity/sRGB/ICC/cICP/HDR metadata precedence and conflict matrices.
- Transparency, significant-bit, unknown ancillary, APNG-default-image, malformed profile/chunk,
  profile bomb, truncation, cancellation, and independent PNG corpus tests.

## Validation

Run `./gradlew :modules:mundane-map-io-image:check --console=plain`, its approved PNG corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the output color policy, static APNG behavior, corpus
provenance/licensing, and final PNG conformance wording before completion.
