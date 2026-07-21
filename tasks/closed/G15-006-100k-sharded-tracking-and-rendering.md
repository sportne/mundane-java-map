# G15-006 — 100k sharded tracking and rendering

Status: Complete
Depends on: G15-005
Gate: G15
Type: AFK

## Goal

Scale the approved live-picture behavior to 100,000 tracks using evidence-guided worker sharding and
packed frame construction without changing correctness or user controls.

## Context

G15-005 establishes the real end-to-end path and fast smoke. This task measures that path before
altering shard sizing, report batches, projection loops, or frame composition.

## Scope

Add the 100k tier; capture JFR/manual profiler evidence; qualify fixed shard counts and ranges;
remove measured allocation/contention hotspots; bound frame/update batches and cancellation
intervals; expose shard skew, backlog, and logical storage; and preserve deterministic equivalence
with the scalar/10k oracle.

## Out of scope

One-million qualification, new public APIs, work stealing, GPU/native/vector acceleration, external
libraries, semantic report drops, timing-based automatic tuning, or richer track graphics.

## Acceptance criteria

- 100k uses the same truth, report, filter, display, and telemetry semantics as 10k.
- A named before/after profile identifies the actual long poles and justifies every retained
  optimization.
- Report/counter conservation, deterministic virtual-time results, stable pixel composition, bounded
  memory, cancellation latency, and EDT responsiveness remain intact.
- No per-track or per-report object allocation appears in the measured steady-state path.
- The 10k smoke remains a short iteration lane and succeeds unchanged.

## Required tests

100k deterministic batch/filter/frame tests, shard-count equivalence, counter/backlog conservation,
logical-storage ceilings, cancellation interval, buffer-pool bounds, 10k regression, and recorded
JFR/allocation evidence using `/tmp` work files.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew liveTrackSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Profiling precedes optimization. Keep environment-labelled observations out of portable acceptance
thresholds.
