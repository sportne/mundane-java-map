# G19-210 — Pinned JMH harness and methodology

Status: Proposed
Depends on: G19-192
Gate: G19
Type: HITL

## Goal

Install a pinned OpenJDK JMH microbenchmark harness with explicit, reproducible fork, warmup, measurement, JVM, GC, profiler, and parameter policies.

## Context

The existing deterministic runner is useful for integration evidence but reimplements a single-process warmup/sampling loop and cannot provide expert-recognized microbenchmark isolation.

## Scope

- Pin JMH and its complete build/test-only graph with licenses, checksums, lockfiles, and offline staging.
- Add dedicated benchmark source sets/tasks that cannot leak JMH into production publications or runtime graphs.
- Freeze quick-smoke and full profiles for forks, warmup, measurement, time units, modes, JVM/JDK, heap, GC, threading, profilers, and parameter ceilings.
- Keep the deterministic runner for integration workloads and prohibit it from becoming a competing microbenchmark harness.
- Record complete environment and invocation metadata with every result.

## Out of scope

- Production JMH dependencies, portable timing promises, or benchmarking end-to-end browser/network workflows inside JMH.

## Acceptance criteria

- Benchmarks compile/run through pinned JMH online and offline with identical resolved inputs.
- PR smoke and controlled full profiles cannot silently omit required forks, parameters, profilers, or scenarios.
- Production dependency/publication inventories prove no JMH or benchmark-only artifact leakage.

## Required tests

- Harness/profile parser, fork/iteration/parameter enforcement, dependency/checksum/license/offline, and production-graph leakage tests.
- Synthetic misconfigured benchmark and skipped-scenario failures.

## Validation

Run the JMH smoke task, `./gradlew :modules:mundane-map-performance-tests:check offlineRepositoryVerification --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve the pinned JMH graph and complete benchmark profiles.
