# G15-002 — Optimized IOU-Kalman Filter kernel

Status: Complete
Depends on: G15-001
Gate: G15
Type: AFK

## Goal

Create a working allocation-free, packed scalar IOU position/velocity estimator and prove it against
an independent dense matrix oracle across the approved variable report intervals.

## Context

G15-001 freezes the bounded forward IOU-Kalman Filter state-estimator profile. The implementation
starts in the new `examples:live-track-stress` project so a one-example algorithm does not
prematurely become public API.

## Scope

Create the runnable/testable example project; implement example-owned packed estimator state,
stable transition/process-noise coefficients, initialization, prediction, position update,
display-time prediction, configuration validation, and counters; add a test-only generic 4-by-4
oracle and deterministic numerical fixtures.

## Out of scope

Truth simulation, scheduling, Swing UI, map rendering, public tracker modules/APIs, association,
smoothing, adaptive/multiple models, bearing/velocity observations, benchmarks, or Native Image.

## Acceptance criteria

- One and many packed tracks initialize, predict, update, and predict for display without per-report
  allocation.
- Scalar and dense-oracle means/covariances agree across boundary, irregular, 1-second, and
  60-second intervals within approved tolerances.
- Near-zero decay, long intervals, repeated updates, rejected timestamps, and invalid/non-finite
  parameters have deterministic behavior.
- Covariance remains finite, symmetric, and positive-semidefinite within the approved tolerance.
- The example project is wired only when working behavior and tests exist.

## Required tests

Unit tests for coefficient limits, initialization, prediction/update, Joseph-form covariance,
display-only prediction, packed range isolation, invalid parameters, oracle comparison, and a fixed
Monte Carlo innovation/RMSE sanity run with deterministic non-wall-clock assertions.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep matrices and general linear algebra test-only. Do not introduce a math dependency or a public
tracking abstraction.

Completion record (2026-07-21): the example-local packed scalar filter implements the approved
stable coefficients, initialization, Joseph update, pure display prediction, timestamp rejection,
counters, and PSD checks. Tests prove storage isolation, numerical boundaries, deterministic
innovation/RMSE behavior, and agreement with an independently coded dense 4-by-4 oracle. No new
dependency or public tracking API was introduced.
