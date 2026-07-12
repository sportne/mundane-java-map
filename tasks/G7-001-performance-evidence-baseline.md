# G7-001 — Performance evidence baseline

Status: Proposed
Depends on: G2-006, G3-003, G5-009, G6-004
Gate: G7
Type: AFK

## Goal

Create a reproducible performance-evidence lane and establish an unoptimized baseline before adding
spatial indexing, clipping, simplification, or cross-layer render caches.

## Context

The Level 1 symbol, interaction, shapefile, and raster paths now provide representative real-stack
workloads. Timing evidence must remain separate from correctness gates and report its environment
rather than imposing brittle universal wall-clock assertions.

## Scope

- New non-published `modules/mundane-map-performance-tests` harness and deterministic fixtures
- Root `performanceEvidence` Gradle lane, separate from `qualityGate`
- Baseline scenario definitions, machine-readable results, and a documented JFR recording workflow

## Out of scope

- Production optimization, microbenchmark-framework dependencies, custom native libraries, and
  machine-independent pass/fail timing thresholds
- Running performance evidence as part of every normal test

## Acceptance criteria

- Deterministic workloads cover dense vector viewport rendering, symbol-heavy rendering, hit
  testing, shapefile query/render, raster window/resample, repeated pan, and repeated zoom.
- Fixture sizes and seeds are fixed and reported; generated coordinates use packed production paths
  and do not require network or external data.
- The harness performs configurable warmup and measured iterations, consumes results to prevent dead
  work, and reports median and a tail percentile plus throughput/counts appropriate to each scenario.
- Output records Java/runtime/OS/architecture, processor count, heap settings, fixture/seed, revision
  when available, configuration, iteration counts, and cache state without embedding timestamps in
  comparison-critical data.
- `performanceEvidence` runs offline, produces a reviewable report under `build/`, and is not a
  dependency of `qualityGate`.
- A documented Java 21 JFR command/profile identifies allocation, CPU, file I/O, and render hotspots
  for the same scenarios; profiling is optional for the automated lane but reproducible.
- Baseline results are captured on the current implementation and identify which G7 optimization is
  expected to address each observed bottleneck.
- No production dependency or native acceleration is introduced.

## Required tests

- Harness tests for seed reproducibility, workload counts, warmup/measurement separation, result
  consumption, percentile calculation, environment fields, and output normalization.
- Smoke-sized execution in the performance-test module; full-sized execution only in the dedicated
  lane.
- Correctness cross-checks that each workload returns the same semantic result as the normal path.

## Validation

```bash
./gradlew :modules:mundane-map-performance-tests:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use JDK timing/JFR facilities and a simple reviewable harness. Evidence guides the following tasks;
it is not a benchmark marketing claim and must include noise/environment caveats.
