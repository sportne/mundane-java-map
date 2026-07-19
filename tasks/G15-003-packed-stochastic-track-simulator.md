# G15-003 — Packed stochastic track simulator

Status: Proposed
Depends on: G15-002
Gate: G15
Type: AFK

## Goal

Simulate every track independently and feed deterministic stochastic position reports through the
IOU-Kalman Filter state estimator with work proportional to reports due rather than total population
per tick.

## Context

G15-002 supplies the packed estimator. The approved G15 workload uses fixed 10k/100k/1m populations,
per-track report intervals from 1 to 60 seconds, deterministic replay, and stable worker ownership.

## Scope

Implement example-owned packed truth state, per-track deterministic random streams, bounded
random-tour motion, Gaussian position measurements, timing-wheel renewal scheduling, stable track-ID
shards, virtual/real clocks, start/pause/reset/close coordinator behavior, and primitive counters.
Complete a headless 10k report-processing slice.

## Out of scope

Swing/map rendering, frame buffers, 100k/1m performance claims, report loss, track creation/deletion,
association, networking, trails, persistence, or a generic scheduler.

## Acceptance criteria

- Every track has independently evolving truth, report schedule, measurement stream, and estimate.
- All intervals remain in `[1 s, 60 s]`; identical configuration/seed/time produces identical
  reports and estimates across repeated runs.
- A timing wheel visits due tracks without a population scan and keeps each track in exactly one
  slot.
- Stable shards have exclusive mutation ownership and complete scheduled work with exact counters.
- Pause, reset, cancellation, worker failure, and close leave no report, state, or thread ownership
  ambiguity.

## Required tests

Unit and integration tests for random-stream independence, motion/domain bounds, schedule bounds and
distribution, wheel wrap/requeue, counter conservation, virtual-time replay, shard equivalence,
pause/reset/cancel, failure propagation, logical allocation checks, and 10k end-to-end filtering.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not create one `Random`, task, future, matrix, or domain object per track. The fixed worker count
and virtual clock are explicit configuration, not ambient globals.
