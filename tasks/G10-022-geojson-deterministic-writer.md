# G10-022 — GeoJSON deterministic writer

Status: Proposed
Depends on: G10-021
Gate: G10
Type: AFK

## Goal

Write a whole FeatureSource as one bounded deterministic GeoJSON FeatureCollection with atomic local
replacement and semantic reopen evidence.

## Context

G10-002 fixes the writer contract and G10-021 completes every geometry value that the writer accepts.

## Scope

Add `GeoJsonFiles.write`, `GeoJsonWriteLimits`, stable write failures, direct Jackson generator use,
CRS/name/attribute/geometry validation, bounded byte encoding, cursor ownership, cancellation, and
same-directory atomic replacement.

## Out of scope

Generic Iterable writers, streaming network sinks, implicit projection, pretty-print options, bbox,
foreign members, date/binary policies, and general vector export.

## Acceptance criteria

- Supported sources produce deterministic UTF-8 bytes and reopen with equivalent geometry and
  properties under G10-002's explicit numeric normalization.
- Unrepresentable CRS, names, attributes, or geometry fail before target replacement.
- The writer closes its cursor, never closes its source, and preserves the old target on pre-move
  failure or cancellation.

## Required tests

Exact-byte, all-geometry, scalar-property, semantic round-trip, limit, cancellation, source failure,
cursor cleanup, atomic replacement, force/move/cleanup failure, and old-target preservation tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Buffer and validate the complete bounded result before touching the target. Do not claim exact ID
round-trip because the reader origin-prefixes JSON string IDs.
