# G10-056 — KML hardening and fixtures

Status: Complete
Depends on: G10-055
Gate: G10
Type: HITL

## Goal

Close the static KML profile against hostile XML, rejected dynamic/network constructs, bounded warned
data loss, and changing input, backed by legally redistributable fixtures.

## Context

G10-055 completes supported geometry. G10-005 defines ignored presentation and altitude behavior,
rejected KML constructs, exact limits/accounting, stable diagnostics, cancellation, snapshot mutation,
and cleanup precedence.

## Scope

Complete presentation-warning behavior; reject NetworkLink, overlay, model, tour, update, region,
time, schema, unsupported altitude, and other out-of-profile constructs without dereferencing them;
enforce every XML, feature, geometry, text, warning, input, and owned-byte limit; and close diagnostic,
cancellation, mutation, and cleanup matrices. Add security fixtures and a small provenance-, license-,
and SHA-256-recorded real-producer KML set.

## Out of scope

KML styling/labels, ExtendedData semantics, dynamic resources, altitude rendering, KMZ, writing,
general XML validation, and Native Image execution.

## Acceptance criteria

- Supported static KML succeeds, ignored presentation/altitude data yields the exact bounded warnings,
  and every rejected construct fails with the approved stable code/context without causing I/O.
- Exact limits succeed, one-over and overflow fail before allocation/publication, retained-warning
  omission is bounded, and cancellation/mutation/cleanup races publish no partial source.
- XXE, DTD, schema-location, href, style URL, malformed UTF-8/XML, and resolver canaries cannot access
  a public network or files outside their temporary fixture tree.
- Every independent fixture has maintainer-approved redistribution terms, source provenance, and
  recorded SHA-256; fixture mutation is detected.

## Required tests

Presentation/altitude warnings; rejected dynamic/network constructs; complete diagnostic and
precedence matrix; XML/UTF-8/XXE canaries; exact/one-over limits and overflow; cancellation; snapshot
mutation; cleanup; query conformance; architecture; and fixture provenance/integrity tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-kml:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 KML fixture provenance approval**. The maintainer must approve the source and
redistribution record for every non-generated fixture before completion.

Completion evidence (2026-07-24): the maintainer's advance HITL approval accepts the synthetic
`simplekml` 1.3.6 fixture and its recorded LGPL-3.0-or-later generator/BSD-3-Clause output
disposition. Its 1,330 bytes are pinned at SHA-256
`32fc9de3e4cc1a09254f01a3b922a406b2237f79c3c6dc403ede3b5c7f37e2f2`. The closed ordered grammar,
warned presentation/altitude loss, rejected dynamic semantics, prospective limit matrix,
mutation/cancellation/cleanup precedence, security canaries, and deterministic mutations are covered
through structured outcomes without external dereferencing.
