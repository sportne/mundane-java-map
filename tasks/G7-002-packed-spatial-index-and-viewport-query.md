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

- An immutable `FeatureIndexLimits` value and explicit indexed factories on the core in-memory source
- One package-private packed STR-16 construction and viewport-query implementation in
  `modules/mundane-map-core`
- Source-ordered candidate accounting through the existing query/cursor lifecycle and limits
- Architecture coverage for primitive storage and API/format/AWT boundaries
- Fixed performance-test scenarios comparing explicit indexed and linear in-memory sources

## Out of scope

- Mutable/dynamic or disk indexes, SHX reinterpretation, shapefile integration, nearest-neighbor
  search, geometry clipping, simplification, and antimeridian-wrap semantics
- Automatic strategy selection, timing-derived thresholds, silent linear fallback, public tree/node
  types, public metrics, query-plan caching, and new source-concurrency guarantees
- Query padding, projected-path/render caches, a native index library, or an external spatial
  dependency

## Acceptance criteria

- `InMemoryFeatureSource.openIndexed(...)` explicitly selects indexing; existing `open(...)` remains
  the unchanged linear oracle, with no automatic threshold or fallback.
- One fixed fanout-16 STR tree stores source ordinals, envelopes, nodes, and edges in primitive arrays;
  construction has exact checked record, retained-byte, cumulative-build-byte, and query-plan-byte
  ceilings and no per-node object graph.
- Empty, duplicate, degenerate, spanning, and equal-envelope input builds deterministically for the
  same ordered snapshot, including total stable tie-breaking.
- A bounded cursor-owned plan uses one candidate bitset, then exact-tests candidate envelopes in
  ascending source order through the existing `maximumRecordsExamined` accounting and publication
  path.
- Inclusive empty/disjoint/containing/edge/corner/domain-boundary queries produce the same ordered
  records, attributes, and diagnostics as linear execution whenever both work limits admit
  completion; candidates contain no duplicates.
- Public in-memory-source one-live-cursor/external-serialization behavior remains unchanged; only the
  private immutable index supports concurrent read-only plan construction.
- Capacity failures use the existing structured source-limit diagnostic with stable
  `spatialIndexBuild` scope, exact limit name, requested value, and maximum.
- Evidence uses fixed point-grid sizes and 256 exactly defined viewports, reports build median/p95,
  retained primitive storage, candidate counts, query median/p95, semantic equality, and descriptive
  crossover against linear scans; every size and implementation has its own scenario row.
- Comparison sources use identical explicit limits that admit the 131,072-record full query without
  relying on the lower Level 1 returned-record default.
- Existing render/query tests pass unchanged when indexing is disabled.

## Required tests

- Value/Javadoc tests for index-limit defaults, positive boundaries, withers, equality, hash, and
  string form.
- Unit tests for exact node/leaf/height/byte formulas; empty and 1/15/16/17-record trees; every limit
  at minus/equal/plus one and overflow; stable tie keys; and duplicate/degenerate/spanning envelopes.
- Fixed-seed property comparisons of indexed and linear record order, attributes, reports, and
  diagnostics over bounded, edge-touching, disjoint, full, point, and domain-edge viewports.
- Work-limit, cancellation, close/failure cleanup, partial-publication, cursor-slot reuse,
  defensive-immutability, private concurrent-plan, and public second-cursor-rejection tests.
- AWT substitution tests through paint, hit, hover, and selection, plus architecture tests for packed
  storage, non-leaking internals, no format/AWT dependency, and no global state or external library.
- Fixed build/query/crossover performance scenarios, formula/candidate-counter proofs, and semantic
  digest equality against linear execution, including exact comparison-source capacity arithmetic,
  BASELINE/SMOKE batches and oracle inheritance, and filtered-investigation crossover omission.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-performance-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The selected structure is a single packed STR-16 tree. G7-001 evidence determines whether callers
benefit from choosing the indexed factory; it does not create an implicit runtime policy. Keep SHX and
all format-specific validation semantics out of this task.
