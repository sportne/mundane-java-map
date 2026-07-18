# G10-024 — GeoJSON fixtures, viewer, and consumer evidence

Status: Proposed
Depends on: G10-023
Gate: G10
Type: HITL

## Goal

Demonstrate GeoJSON interoperability through provenance-recorded fixtures, a runnable viewer,
tolerant rendering regression, and a clean staged-artifact consumer.

## Context

The adapter is functionally complete and hardened after G10-023; this task supplies externally
observable interoperability and packaging evidence.

## Scope

Add small RFC and independent-producer fixtures with provenance, a GeoJSON viewer example, tolerant
rendering regression, Javadocs, staged publication checks, and offline consumer read/write/reopen/
query/render coverage.

## Out of scope

A new corpus command, remote retrieval, broad real-world compatibility claims, UI editing, or Native
Image evidence.

## Acceptance criteria

- The viewer opens every supported fixture and reports stable unsupported/malformed outcomes.
- Rendering assertions tolerate platform variation while proving geometry and hole behavior.
- A clean Java 21 consumer uses staged artifacts and the controlled Jackson dependency only.

## Required tests

Fixture provenance/digest, viewer, rendering regression, Javadoc/doclint, publication metadata, and
offline consumer tests.

## Validation

```bash
./gradlew renderRegression publicationDryRun consumerSmoke offlineRepositoryVerification --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoJSON interoperability review**. Review the viewer and fixture provenance;
approval does not broaden the supported profile.
