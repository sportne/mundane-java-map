# G19-229 — WebP interoperability, security, and capability closeout

Status: Proposed
Depends on: G19-228
Gate: G19
Type: HITL

## Goal

Close the optional static WebP adapter with independent interoperability, hostile-input, dependency,
platform, and public-capability evidence.

## Context

WebP parsing and codec execution are security-sensitive. Passing project-authored fixtures does not
establish the approved common static profile or justify exposing the adapter to untrusted tiles and
uploaded resources.

## Scope

- Map the approved RIFF WebP, VP8, VP8L, alpha, color/profile, metadata, and static extended-container
  behavior to implementation and evidence; identify every unsupported construct precisely.
- Run provenance-recorded WebP conformance and independent-producer corpora plus project-owned hostile,
  fuzz-regression, allocation, cancellation, concurrency, and cleanup suites.
- Compare representative decoded pixels/color/alpha/orientation and tile/render output with current
  libwebp and at least one independent consumer under documented tolerances.
- Audit the pinned TwelveMonkeys source/release, known security advisories, dependency graph, licenses,
  checksums, update policy, JPMS/public API, offline/publication behavior, and supported JVM platforms.
- Reconcile adapter `CAPABILITIES.md`, package Javadocs, root support wording, module consumers, and the
  explicit exclusions in `mundane-map-io-image/CAPABILITIES.md`.

## Out of scope

- Claiming WebP animation, writing, Native Image support, a custom codec, or general ImageIO plugin
  compatibility.

## Acceptance criteria

- An external image-format/security review finds no untracked gap in the declared static WebP profile
  and no unsafe or implicit codec/discovery boundary.
- Applicable corpora and every registered consumer pass with bounded work, stable diagnostics, exact
  resource cleanup, and current dependency evidence.
- Public documentation makes the optional AWT dependency and exclusions unmistakable and does not
  broaden the core image module's support claim.

## Required tests

- Corpus/fuzz/mutation/differential/color/alpha/orientation/subsample/limit/cancellation/concurrency/
  cleanup suites with reproducible seeds and provenance.
- Full consumer, offline repository, publication dry-run, dependency verification, Javadoc, staged
  consumer, and supported-platform JVM matrix.
- Architecture checks proving no Native or toolkit-neutral graph includes the adapter accidentally.

## Validation

Run all predecessor lanes, corpus and differential evidence, offline/publication checks,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer and independent image-format/security reviewer approve the corpus,
dependency audit, capability matrix, platform statement, and static decode-only support wording.
