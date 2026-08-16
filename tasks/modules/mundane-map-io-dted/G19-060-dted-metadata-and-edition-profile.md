# G19-060 — DTED metadata and edition profile

Status: Proposed
Depends on: None
Gate: G19
Type: HITL

## Goal

Expose a complete immutable metadata model for the approved MIL-PRF-89020B Level 0/1/2 cell profile
and pin every accepted edition, datum, padding, accuracy, and producer variation.

## Context

The reader validates much of UHL, DSI, and ACC but publishes only generic grid metadata and discards
provenance, security, accuracy, edition, and product fields. ACC subregions are rejected wholesale.

## Scope

- Freeze the field-by-field UHL, DSI, ACC, data-record, and notice-applicability matrix in module
  documentation and public Javadocs.
- Add immutable bounded metadata values that distinguish blank, unknown/not-applicable, and present
  standard fields without exposing mutable header bytes.
- Preserve classification/control/handling, references, edition/maintenance, producer/specification,
  datums, collection, corners/orientation, partial state, accuracy, producer-use, and comments.
- Implement supported multiple-accuracy subregion records and exact cross-record consistency checks.
- Pin legitimate Level 0/1/2, SRTM, datum, padding, and producer variations plus stable unsupported
  diagnostics for variants outside the declared profile.
- Define redaction-safe diagnostics; security markings remain descriptive metadata, not authority.

## Out of scope

- Treating classification text as access authorization or redistributing restricted fixture data.

## Acceptance criteria

- Every field in the approved record matrix is preserved, derived, or explicitly reported outside
  the supported profile.
- Metadata survives eager open without retaining file handles or leaking source-controlled values in
  diagnostics.
- Legitimate pinned variations interoperate without weakening fixed-frame, checksum, or consistency
  validation.

## Required tests

- Field inventory, presence/unknown, immutability, equality, bounds, and Javadoc tests.
- Independent L0/L1/L2 and SRTM metadata fixtures, ACC subregions, padding/edition variations, and
  corrupt/conflicting/security-redaction cases.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, `./gradlew dtedCorpus
--console=plain`, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact MIL-PRF-89020B/notice field matrix and the
provenance/licensing of any newly checked-in external fixtures before completion.
