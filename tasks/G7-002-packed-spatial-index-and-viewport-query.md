# G7-002 — Packed spatial index and viewport query

Status: Proposed
Depends on: G7-001, G4-003
Gate: G7
Type: AFK

## Goal

Accelerate immutable feature viewport queries with a packed-primitive spatial index while preserving
the exact results and stable ordering of linear scans.

## Context

G7-001 provides baseline query/render scenarios. G4-003 defines feature queries and deterministic
cursor behavior; the existing linear implementation remains the correctness oracle.

## Scope

- Immutable index construction and query algorithms in `modules/mundane-map-core`
- Optional explicit index use by in-memory/source query paths without changing format contracts
- Performance-test scenarios comparing indexed and linear execution

## Out of scope

- Mutable/dynamic indexes, disk indexes, SHX replacement, nearest-neighbor search, and geometry
  clipping
- A native index library or external spatial dependency

## Acceptance criteria

- Index nodes/entries use packed primitive arrays with bounded construction memory and no
  per-coordinate object graph.
- Construction validates finite envelopes, handles empty/degenerate features by documented rules,
  and is deterministic for identical ordered input.
- Intersection queries return exactly the same feature identities as the linear scan for empty,
  edge-touching, containing, disjoint, and world-wrap/domain-boundary viewports.
- Results follow the G4 stable source ordering regardless of index traversal order and contain no
  duplicates.
- The public surface exposes explicit index construction/selection; no reflection, scanning, or
  implicit global cache is used.
- Index/source values are immutable, thread-safe for concurrent reads, and bounded by configurable
  feature/node/allocation limits.
- Evidence reports build time, retained primitive storage, query median/tail, candidates examined,
  and crossover behavior against linear scans for small and large fixtures.
- Existing render/query tests pass unchanged when indexing is disabled.

## Required tests

- Unit tests for construction/query boundary cases, duplicate envelopes, stable ordering, and limits.
- Property-style fixed-seed comparisons of indexed and linear results over many viewports.
- Concurrent-read and defensive-immutability tests.
- Performance scenarios for index build, cold query, repeated viewport queries, and small-data
  overhead.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-performance-tests:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Choose the simplest packed structure supported by G7-001 evidence. Do not expose internal node types
as public API or optimize away deterministic ordering.
