# G19-211 — Benchmark correctness and result provenance

Status: Proposed
Depends on: G19-210
Gate: G19
Type: AFK

## Goal

Ensure every reported measurement represents completed correct work with bounded state, closed resources, and a versioned auditable result record.

## Context

JMH prevents many measurement errors but cannot know whether a benchmark computes the intended result, times setup accidentally, leaks resources, or permits optimization to remove useful work.

## Scope

- Require untimed semantic oracles, observable result consumption, explicit JMH state scopes, and setup/teardown declarations.
- Detect constant folding, dead-code elimination, unbounded parameter combinations, shared mutable state, accidental I/O/setup timing, and trial-order contamination.
- Verify exact closure and baseline resource counts after successful, failed, and repeated trials.
- Define a versioned result/evidence schema containing raw samples, statistics, parameters, environment, revision, fixtures, toolchains, and dirty-state provenance.
- Calibrate harness overhead and retain no-op/control benchmarks without subtracting unverifiable scores.

## Out of scope

- Claiming that one benchmark result proves algorithmic correctness beyond its owning semantic tests.

## Acceptance criteria

- Every benchmark has an independently exercised oracle and fails when its result is ignored, work is elided, parameters escape bounds, or resources remain owned.
- Result bundles reject missing, inconsistent, stale, nonfinite, or unit-mismatched observations.
- A benchmark change cannot silently make prior results incomparable without a schema/profile transition.

## Required tests

- Synthetic dead-code, constant-fold, setup-leak, shared-state, missing-teardown, invalid-result, and profile-drift fixtures.
- Repeated benchmark lifecycle tests with files, sources, cursors, sessions, databases, images, and executors as applicable.

## Validation

Run harness self-tests and schema tests, the JMH smoke task, `./gradlew :modules:mundane-map-performance-tests:check --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.
