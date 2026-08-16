# G19-176 — Package integrity and caller verification

Status: Proposed
Depends on: G19-175
Gate: G19
Type: AFK

## Goal

Require SHA-256 integrity for portable workspace content and expose a bounded caller verification hook without
embedding signing, encryption, or PKI policy.

## Context

ZIP CRC detects accidental transfer errors weakly and does not bind declared workspace resources. Strong content
digests are needed for integrity, while authorship/trust belongs to the host application's security domain.

## Scope

- Canonically identify and SHA-256 digest every embedded entry/resource set and the approved manifest/package facts;
  define ordering, duplicate identity, streaming verification and mismatch diagnostics.
- Verify sizes, CRC and digests prospectively/before resource publication; prevent substitution, confused-deputy,
  alias, time-of-check/time-of-use and partial-verification paths.
- Add an explicit immutable bounded caller verifier invoked with canonical identities/media/sizes/digests only;
  define accept/reject/failure/cancellation/cost/lifecycle behavior without exposing raw secrets or granting authority.
- Ensure save recomputes all integrity facts from owned bytes and rewrite cannot carry stale/mismatched digests.

## Out of scope

- Built-in digital signature syntax, encryption, passwords, keys, certificates, trust stores, revocation, timestamping,
  remote transparency services, ambient verifier discovery and claims that a digest proves authorship.

## Acceptance criteria

- Every byte/name/media/relationship change is detected before opening a resource or installing workspace state.
- Verifier decisions are deterministic, bounded, cancellable and cannot authorize unrelated filesystem/network access.
- Plain XML remains usable without pretending to have packaged-content integrity.

## Required tests

- Entry/resource-set/manifest/package digest golden and mutation matrix, streaming/chunk boundaries and deterministic saves.
- Substitution/alias/TOCTOU/truncation/reorder/duplicate/stale digest, verifier reject/throw/hang/cancel/reentrancy/limit,
  huge package, cleanup and no-authenticity-overclaim documentation tests.

## Validation

Run workspace integrity/verifier/security checks, then qualityGate and `git diff --check`.

## Notes

None.
