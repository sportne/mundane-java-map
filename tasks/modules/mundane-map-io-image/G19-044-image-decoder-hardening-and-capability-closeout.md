# G19-044 — Image-decoder hardening and capability closeout

Status: Proposed
Depends on: G19-041, G19-043
Gate: G19
Type: HITL

## Goal

Close the decoder-only image module with one coherent public capability contract, cross-format
hostile-input evidence, and externally recognizable PNG/JPEG interoperability.

## Context

Format implementation cards can pass independently while leaving inconsistent decoder selection,
limits, metadata, cancellation, diagnostics, caching, or documentation. This closeout proves the
adapter as a whole and records deliberate non-goals rather than leaving apparent omissions.

## Scope

- Reconcile PNG and JPEG output color/precision, metadata, placement, decoder registration, and
  stable diagnostic contracts across byte-array, path, source, cache, and cancellation entry points.
- Verify exact encoded, compressed, inflated, coefficient, pixel, profile, metadata, cache, and
  concurrent-source accounting with atomic reservation/release on every exceptional path.
- Maintain toolkit neutrality and explicit decoder registration; do not add AWT/ImageIO, discovery,
  reflection, JNI, native codecs, or implicit resource scanning to the production module.
- Publish an auditable supported/unsupported matrix and corpus provenance from
  `modules/mundane-map-io-image/CAPABILITIES.md`, package Javadocs, README, and release evidence.
- Obtain independent review of the claim that the decoder covers the approved static PNG and common
  JPEG interchange profiles.

## Out of scope

- PNG/JPEG encoding, transcoding, editing, APNG playback, arithmetic/lossless/hierarchical/high-
  precision JPEG, JPEG-LS, JPEG 2000, JPEG XL, and new image families.

## Acceptance criteria

- Every public decode/source path reports the same closed capability and limit semantics and leaves
  no partial raster, cache entry, reservation, source claim, or worker after failure/cancellation.
- Approved PNG/JPEG corpora, world-file placement, cache, concurrent decode, hostile-input, and
  lifecycle suites pass on every supported runtime lane.
- Public claims precisely distinguish released behavior, approved decode-only profiles, and explicit
  exclusions; an external image-format reviewer records no untracked common-profile gap.

## Required tests

- Cross-entry-point byte equivalence, color/output equivalence, world-file affine placement, cache
  identity, cancellation, concurrent-close, registration, and stable-diagnostic tests.
- Combined hostile corpus with fuzz seeds for structural, arithmetic, allocation, metadata, profile,
  decompression, truncation, and cleanup boundaries.
- Architecture tests proving the decoder-only toolkit-neutral dependency and forbidden-API rules.

## Validation

Run `./gradlew :modules:mundane-map-io-image:check --console=plain`, the approved PNG/JPEG corpus and
fuzz-regression lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer and independent image-format reviewer approve the final capability
matrix, corpus provenance/licensing, evidence report, and decoder-only support wording.
