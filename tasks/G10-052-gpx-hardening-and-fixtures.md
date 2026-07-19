# G10-052 — GPX hardening and fixtures

Status: Proposed
Depends on: G10-051
Gate: G10
Type: HITL

## Goal

Close the approved GPX profile against malformed, hostile, changing, and lossy inputs and prove it
with generated and legally redistributable fixtures.

## Context

G10-051 completes supported waypoint and track behavior. G10-005 defines the closed grammar,
UTF-8/XML boundary, ignored-content warnings, limits/accounting, diagnostic precedence, snapshot
mutation, cancellation, and cleanup rules that must now be exhaustive.

## Scope

Complete root/metadata/waypoint/track grammar and cardinality enforcement; rejected routes and foreign
content; bounded extension and ignored-field reporting; exact/one-over structural, text, coordinate,
feature, warning, input, and owned-byte limits; checked arithmetic; stable diagnostics; cancellation
checkpoints; snapshot mutation detection; and cleanup precedence. Add hand-built security fixtures and
a small provenance-, license-, and SHA-256-recorded real-producer GPX set.

## Out of scope

Route support, extension semantics, alternate encodings, XML Schema validation, network resources,
writing, KML, and Native Image execution.

## Acceptance criteria

- Every approved GPX construct succeeds and every closed unsupported or malformed construct fails or
  warns with the exact stable code/context and precedence defined by G10-005.
- Exact limits succeed, one-over and overflow fail before allocation/publication, retained-warning
  omission is bounded, and cancellation/mutation/cleanup races never publish partial sources.
- DTD, entity, schema-location, XInclude, malformed UTF-8/XML, and resolver canaries cannot access a
  public network or files outside their temporary fixture tree.
- The independent fixture set has maintainer-approved redistribution terms, source provenance, and
  recorded SHA-256 values; fixture mutation is detected by tests.

## Required tests

Full grammar and diagnostic matrix; warning retention/omission; XML/UTF-8/XXE canaries; exact/one-over
limits and overflow; already/mid-parse cancellation; file mutation and close precedence; query
conformance; architecture; and fixture provenance/integrity tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-gpx:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GPX fixture provenance approval**. The maintainer must approve the recorded
source and redistribution terms for every non-generated fixture before this task can complete.
