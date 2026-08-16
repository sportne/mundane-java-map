# G19-190 — Released API baselines and SemVer governance

Status: Proposed
Depends on: G19-001, G19-002
Gate: G19
Type: HITL

## Goal

Mechanically compare every published Java API with a verified release and enforce the approved pre/post-1.0 version policy.

## Context

Current compilation and Javadocs do not detect accidental binary/source drift or require a sufficient version change.

## Scope

- Add per-artifact baseline manifests naming exact released coordinates, SHA-256 and POM/module provenance; support reviewed
  deterministic provisional signature snapshots only before first publication.
- Pin Apache-2.0 Revapi Java analysis as build/test-only offline-governed tooling and supplement it for enum/sealed exhaustiveness,
  record shape, overload ambiguity, generics, checked exceptions, nullness/annotations/constants and leaked public types.
- Govern public/protected API in supported/exported packages and distinguish binary, source and API-shape changes.
- Enforce patch compatibility before 1.0, reviewed breaking pre-1.0 minors, and major/minor/patch SemVer at/after 1.0, including
  deprecation retention and narrow documented emergency exceptions.
- Require exact scoped reviewed declarations, expiry, rationale, migration/replacement and release-note linkage; reject blanket ignores.

## Out of scope

- General behavioral equivalence, internal/test/example packages, reflection into internals or Java serialization compatibility.

## Acceptance criteria

- Every published artifact compares against the exact verified baseline and an unapproved incompatible change fails with a stable report.
- The minimum required version change is calculated and insufficient bumps, coordinate reuse and baseline substitution fail.
- Revapi/configuration upgrades cannot change classifications without synthetic-fixture and dependency/license/checksum review.

## Required tests

- Synthetic old/new JAR matrices for every strict compatible/incompatible construct and approved exception lifecycle.
- Missing/corrupt/wrong baseline, provisional-to-release transition, offline resolution, SemVer/pre-release/deprecation/emergency and
  multi-artifact version mismatch tests.

## Validation

Run architecture, offline repository and publication dry-run checks, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve Revapi graph/configuration, strict classification, baseline provenance and SemVer policy.
