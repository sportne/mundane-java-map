# G10-056 — KML hardening and fixtures

Status: Proposed
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
