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

- Settings/inventory/root wiring for one checked, non-published `mundane-map-performance-tests`
  support project and a separate root `performanceEvidence` lane
- Six versioned deterministic fixture families, twelve fixed real-stack scenarios, a sequential Java
  21 harness, exact statistics/oracles, and JSON/Markdown reports
- Focused architecture/task-graph tests, a separate offline-execution CI job, optional same-scenario
  JFR helper, and the lean G7 reference-baseline interpretation

## Out of scope

- Production optimization, microbenchmark-framework dependencies, custom native libraries, and
  machine-independent pass/fail timing thresholds
- Running performance evidence as part of every normal test
- JMH/profiler agents, network/process/external data in the runner, benchmark SPIs/servers, public
  metrics APIs, OS cold-cache claims, or committed timing leaderboards

## Acceptance criteria

- Fixture versions/counts and seed `0x4d554e44414e454a` are fixed and reported. Six families cover the
  65,536-record feature grid, packed vector paths, 4,096-feature symbol field, layered hit stack,
  support-generated 50,000-record shapefile, and checked 1,024-by-768 PNG/JPEG affine resources.
- Twelve stable scenarios cover linear full/window query, dense/symbol rendering, hit testing,
  shapefile query/render, disabled/preseeded/mixed raster behavior, and repeated pan/zoom.
- Baseline and SMOKE cardinalities, generator formulas, public screen-coordinate hit tolerance, initial
  viewports, request windows, navigation traces, source-binding kind, throughput batch/unit, and cache
  reset/persistence are exact compatibility inputs for later comparisons.
- The harness builds fixtures outside timing, runs sequential fixed batches, validates/consumes every
  warmup and measurement, excludes EDT queue time, retains raw nanos, and reports exact median,
  nearest-rank p95, throughput, semantic counters, and cache labels.
- Scenario setup/teardown occurs once; every sample has untimed deterministic state reset and cleanup
  around its timed batch. File/source work is off-EDT and every MapView lifecycle/hit/paint is on-EDT.
- Investigation overrides accept only one exact scenario, warmups 0–100, and measurements 1–100;
  exact integer rounding/overflow rules keep JSON and Markdown statistics identical.
- Output records Java/runtime/OS/architecture, processor count, heap settings, fixture/seed, revision
  when available, configuration, iteration counts, and cache state without embedding timestamps in
  comparison-critical data.
- Revision/environment/JVM inputs use bounded allowlists/grammars and never copy arbitrary command
  lines, paths, host/user data, or control text into reports.
- Scenario code runs offline without process/external data access, produces deterministic schema-v1
  JSON and equivalent Markdown under `build/`, and never joins `qualityGate`/other specialized lanes.
- A documented Java 21 JFR command/profile identifies allocation, CPU, file I/O, and render hotspots
  for the same scenarios using the same toolchain's `jfr` executable, including allocation-sample
  events; profiling is optional for the automated lane but reproducible and replaces stale output.
- The implementation run adds a concise checked-in interpretation linking actual counter/JFR stages
  to G7-002, G7-003, G7-004, or no change, without committing a timing leaderboard.
- A typed FNV-1a oracle with canonical finite-double/RGBA/string/integer encodings freezes one reviewed
  observation digest per profile/scenario pair; only BASELINE digests enter the interpretation, and
  render digests contain portable tolerance classes rather than pixels.
- Duration never fails the lane or creates a portable performance claim.
- No production dependency or native acceleration is introduced.

## Required tests

- Harness tests for seed reproducibility, workload counts, warmup/measurement separation, result
  consumption, percentile calculation, environment/revision fields, deterministic dual reports,
  cleanup, and sensitive/time/path exclusion.
- Smoke-sized execution in the performance-test module; full-sized execution only in the dedicated
  lane.
- Correctness cross-checks that each workload returns the same semantic result as the normal path.
- Architecture/task-graph tests for support-only publication/dependencies and lane separation.

## Validation

```bash
./gradlew :modules:mundane-map-performance-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

G7-001 alone creates `performanceEvidence`; descendants extend the same registry/schema without
renaming baseline scenario IDs or silently changing fixture versions. Use only JDK timing/JFR and a
reviewable harness. Do not run rendering, corpus, native, publication, or consumer lanes in this task.

Implement in three reviewable milestones: (A) lane/harness/report and two query scenarios, (B) all
fixtures and twelve scenarios, then (C) graph/CI/JFR/full evidence/frozen digests/interpretation. No
milestone marks this task Complete or unblocks G7-002 before the final validation passes.
