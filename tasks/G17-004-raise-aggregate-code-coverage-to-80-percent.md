# G17-004 — Raise aggregate code coverage to 80 percent

Status: Proposed
Depends on: G17-002, G17-003
Gate: G17
Type: AFK

## Goal

Raise the existing JaCoCo coverage floor from 60% to 80% for every covered Java project by adding
meaningful behavioral tests and then enforcing the higher threshold in the normal quality gate.

## Context

`mundane-map.java-library-conventions.gradle` currently applies JaCoCo's default bundle-level
instruction covered ratio with `minimum = 0.60` to each project using the convention. The threshold
change must expose and close real test gaps before the value becomes `0.80`; it must not be achieved
by excluding difficult code, removing behavior, or writing assertion-free execution tests.

## Scope

- Tests and fixtures for production modules and examples governed by the shared Java convention
- Shared JaCoCo convention, coverage reports, architecture/build-logic tests, and developer
  documentation for the exact metric and report location
- Deterministic unit, boundary, malformed-input, lifecycle, concurrency, rendering-invariant, and
  integration tests needed to reach the new floor

## Out of scope

- Per-source-file enforcement, which belongs to G17-005
- Generated/vendored bytecode, test-code coverage, arbitrary coverage exclusions, production
  rewrites whose only purpose is manipulating instrumentation, or tests without behavioral
  assertions
- Changing from the existing bundle-level instruction metric or folding specialized evidence lanes
  into unit coverage

## Acceptance criteria

- A checked-in baseline report lists the bundle-level instruction covered ratio for every governed
  project and identifies uncovered behavior by risk, not only by percentage.
- Tests added to close gaps assert externally observable results, stable diagnostics, limits,
  lifecycle/cleanup, or algorithmic invariants and remain deterministic and bounded.
- Every governed project reaches at least `0.80` JaCoCo `INSTRUCTION/COVEREDRATIO` at `BUNDLE`
  scope using its normal test task.
- The shared convention sets the minimum to exactly `0.80`; the normal `check` and `qualityGate`
  tasks fail when a governed project's aggregate ratio drops below it.
- No class, package, source file, method, branch, generated-pattern, or low-coverage project is
  excluded relative to the pre-task coverage population.
- Coverage reports remain XML and HTML, offline, reproducible, and easy to locate; failure output
  names the project, metric, actual ratio, and required ratio.
- Build-logic tests prove exact-threshold success, below-threshold failure, and that the coverage
  verification task remains attached to `check`.

## Required tests

- Focused behavioral tests for every production area changed to close a gap.
- Build-logic functional tests for threshold value, pass/fail behavior, report generation, and task
  wiring.
- Full-project JaCoCo verification under the normal quality gate.

## Validation

```bash
./gradlew :build-logic:check checkAll --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Preserve the current JaCoCo metric so the change is a true 60%-to-80% increase. Specialized corpus,
rendering, performance, Native Image, and external deployment lanes provide different evidence and
must not be counted artificially as ordinary unit coverage.
