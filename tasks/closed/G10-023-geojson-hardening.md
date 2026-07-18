# G10-023 — GeoJSON hostile-input and writer hardening

Status: Complete
Depends on: G10-022
Gate: G10
Type: AFK

## Goal

Close the GeoJSON reader and writer security envelope with exact limits, diagnostics, cancellation,
cleanup, and deterministic adversarial evidence.

## Context

G10-020 through G10-022 provide the complete supported read/write behavior; this task proves every
approved failure boundary without expanding the profile.

## Scope

Complete malformed UTF-8/JSON, duplicate members, Unicode scalar, numeric, nesting, allocation,
warning omission, lifecycle, mutation, cancellation, write-source, and cleanup cases. Add bounded
deterministic parser mutation and adversarial FeatureSource/cursor tests.

## Out of scope

New syntax, properties, geometry families, formats, performance optimization, or new verification
lanes.

## Acceptance criteria

- Every G10-002 diagnostic and limit has exact, equality, and one-over evidence.
- Hostile inputs and sources cannot cause unbounded retention, partial publication, or raw-data leaks.
- Primary failures remain stable and cleanup failures are suppressed deterministically.

## Required tests

Hostile matrix, deterministic mutation/fuzz, exact-limit, cancellation checkpoint, lifecycle,
diagnostic-shape, data-leak canary, and cleanup-failure tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geojson-jackson:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep mutation runs deterministic and bounded for the normal development loop.
