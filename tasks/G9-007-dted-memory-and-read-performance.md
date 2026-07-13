# G9-007 — DTED memory and read performance

Status: Proposed
Depends on: G9-004, G9-005, G9-006
Gate: G9
Type: AFK

## Goal

Measure public DTED open, traversal, position-query, and memory behavior at the largest supported
standard cell and make an evidence-based eager-versus-windowed decision.

## Context

G7 creates the `performanceEvidence` lane and its reproducibility conventions. DTED density varies
substantially by level; architectural complexity must follow measurements rather than assumptions.

## Scope

Append four deterministic DTED scenarios to `performanceEvidence`, using the approved corpus and an
independently generated maximum zone-I Level 2 cell. Record existing timing statistics and semantic
oracles, exact logical retained/open-peak bytes, and explicitly labelled JVM heap/allocation/JFR
observations. Compare eager storage with a support-only analytical decoded-profile LRU model and
record the result against the fixed decision rubric.

## Out of scope

Production/API/parser changes, a second reader, timing thresholds across unlike machines, GC-forced
heap claims, native code, memory mapping, allocation agents, unrelated rendering optimization, and a
broad reader rewrite without supporting evidence.

## Acceptance criteria

- Append-only evidence covers all three approved producer files plus a deterministic
  3,601-by-3,601 Level 2 cell through public open, row-major traversal, and nearest/bilinear query
  workloads with pinned semantic digests and cleanup.
- Normal `SMOKE`/module checks use only the independently asserted generated Level 0 surrogate; only
  the full separate performance lane resolves the approved corpus paths.
- The canonical report retains G7 methodology/environment fields and exact logical
  sample/mask/profile/open-peak accounting; the bounded probe and checked decision record label JVM
  heap, allocation, and JFR values as environment-specific observations.
- An untimed analytical model reports exact hit/miss/read-byte results for one-, 64-, and 256-profile
  LRUs over fixed local and scattered traces without masquerading as a production parser benchmark.
- Eager access is retained only if semantics pass, the maximum cell opens in the canonical 512-MiB
  fork, retained logical storage is at most 128 MiB, and logical open peak is at most 256 MiB.
- A failed mandatory condition creates a separate bounded fallible/windowed-source implementation task
  and adds it to G9-008's dependencies before this task completes; no existing contract is weakened.
- No custom native performance library or native parsing path is introduced.

## Required tests

Fixture header/size/hash/formula and public-reader correctness; scenario phase/order/profile/oracle/
cleanup tests; normal-SMOKE corpus-isolation checks; checked logical-memory and threshold-boundary
tests; bounded probe schema/labelling; analytical LRU constants; task-graph, publication, and
prohibited-mechanism architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check :modules:mundane-map-performance-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew dtedCorpus --console=plain
./gradlew performanceEvidence --console=plain
./gradlew :modules:mundane-map-performance-tests:performanceJfr -PperformanceScenario=dted-eager-open --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Durations and JVM observations remain evidence with an environment, not universal guarantees. The
AFK rubric uses exact semantics/logical bytes and the canonical heap only. A custom native library
would require a separate decision after pure Java failed a documented caller target.
