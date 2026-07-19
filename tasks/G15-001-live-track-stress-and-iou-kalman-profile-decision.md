# G15-001 — Live-track stress and IOU-Kalman Filter profile decision

Status: Proposed
Depends on: G5-010, G7-005
Gate: G15
Type: HITL

## Goal

Approve the exact independently implemented IOU-Kalman Filter state estimator, stochastic workload, packed
execution, Natural Earth, display, telemetry, and evidence profile for the live-track stress example.

## Context

The [G15 design](../design/G15-live-track-stress-and-iou-tracking.md) derives a bounded forward
position/velocity estimator from a public NPS account of the IOU process. Existing shapefile,
CRS, MapView, and performance lanes provide the chart and evidence foundations.

## Scope

Review and freeze the estimator equations and numerical policy; independent-implementation and
support wording; population tiers; `[1 s, 60 s]` deterministic renewal distribution; truth and
measurement models; EPSG:3857 domain; timing wheel and shard ownership; packed storage ceilings;
10-FPS reference cap and other cap controls, overlay, overload, telemetry, smoke/evidence durations;
the approximately 100,000-report/second 1m reference load; Natural Earth version, terms, and
provenance obligations; and G15-002 through G15-008 boundaries.

## Out of scope

Production code, example-module creation, dataset files, public tracking APIs, historical tracker
variants, data association, networking, operational accuracy claims, or performance conclusions.

## Acceptance criteria

- The precise IOU state transition, process covariance, initialization, measurement update,
  prediction-to-display-time, and numerical-stability policies are approved with public sources.
- Workload timing, deterministic replay, truth/noise, population, coordinate, storage, threading,
  pacing, overload, and lifecycle behavior are exact.
- Natural Earth files, version, provenance, terms, hashing, and runtime-download prohibition are
  exact.
- Smoke/evidence durations, metrics, machine-readable schema obligations, and non-portable timing
  policy are approved.
- Every later G15 card remains a 1–5 day vertical capability with no speculative production API.

## Required tests

No production tests. Review the scalar equations against an independently written matrix sketch,
calculate expected logical storage/update rates for all tiers, inspect the timing-wheel and frame-
ownership state machines, and verify source/dataset provenance references.

## Validation

```bash
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **IOU-Kalman Filter state-estimator profile, Natural Earth provenance obligations, stress
workload, and support-wording approval**. Approval is for the documented bounded forward IOU Kalman
profile, not a claim of equivalence to every Daniel H. Wagner Associates implementation.
