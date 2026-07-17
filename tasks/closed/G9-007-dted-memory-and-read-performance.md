# G9-007 — DTED memory and read performance

Status: Complete
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

Implementation evidence (2026-07-16, worktree based on `bed57ba`): all benchmark runtime classes,
generated fixtures, staged corpus files, workspaces, Java temporary files, measured report writes,
and JFR recording/inspection ran beneath invocation-unique `/tmp` directories. Only completed bounded
reports and JFR artifacts were copied to `build/performance-evidence`. `performanceQuick` completed in
9.14 seconds; the final canonical 5-warmup/20-measurement lane completed in 115.99 seconds after the
harness was corrected to release each open-once source at scenario completion.

The generated hashes are
`99bd897d6d4af55ffe1092be7a3ee8051d1fbfff1613d6f008fbfb447c46fad5` (Level 0 smoke) and
`2e1e3adcb1f65d41d93ad5d31c63211522ca830bd8f2716415070e3ae8b72330` (maximum Level 2).
Canonical medians were 18.65 ms for the three-file corpus open, 90.40 ms for maximum eager open,
193.86 ms for 12,967,201 row-major samples, and 15.54 ms for 65,536 position queries. The probe on
OpenJDK 21.0.11/Linux amd64 observed 232,805,464 heap bytes after publication and 251,360,456
current-thread allocated bytes; these are labelled environment observations, not retained-size
claims. Exact logical storage is 105,358,512 published bytes and 210,726,938 open-peak bytes.

The final JFR investigation completed in 6.68 seconds and recorded 22 execution samples, 240 allocation
samples, and 11 garbage collections. Execution samples were in fixture authoring and DTED
`readProfiles`/`unsigned16`; no `FileRead` or `FileWrite` events crossed the recording threshold.
This supports CPU/allocation work in the parser and publication copies—not `/mnt/d` benchmark I/O—as
the maximum-cell long pole. The canonical evidence JSON/Markdown hashes were
`c30871fc690753308d804b6486ae5fe3d98c845f04aaa8fad06c37e1d36dfb6e` and
`6cc7b7383b518e5735e7cfe7810be2411b4943c52c3c9620e913c93854d24962`; the probe hash was
`7e367238203398117681deef1a43a8f8eb48935b1c990dccceaf662dc6b62b57`, and the JFR hash was
`5fa8bbd77486e2756f97f5dd397b466c2c898cdf6b72c0137e3665400904373a`.

Decision: **retain eager access**. All four frozen semantics passed in the canonical 512-MiB fork,
the maximum cell published, and both logical ceilings passed. The analytical 1/64/256-profile LRU
results matched the designed local/scattered hit, miss, read-byte, and retained-byte values. No
windowed-source follow-up, public contract change, native library, memory mapping, or parser change is
justified by this evidence.
