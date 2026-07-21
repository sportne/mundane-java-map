# G15-005 — First 10k live-picture slice

Status: Complete
Depends on: G15-003, G15-004
Gate: G15
Type: HITL

## Goal

Run and display 10,000 simulated, individually filtered tracks over the global chart with a maximum
FPS control, achieved-FPS telemetry, and one bounded asynchronous frame handoff.

## Context

G15-003 supplies deterministic report processing and G15-004 supplies the real map background. The
G15 design keeps dense track pixels in an example-owned transparent overlay rather than publishing
10,000 immutable feature objects every frame.

## Scope

Implement the non-intercepting overlay, immutable viewport snapshots/generations, packed projected
frame planning, pooled ARGB buffers, deterministic shard composition, one-request/one-frame atomic
handoff, stale-frame rejection, EDT frame pacing, 10k controls, visible telemetry, lifecycle, and
headless harness. Create `liveTrackSmoke`.

## Out of scope

100k/1m qualification, general live map layers, per-track Swing objects, track selection/labels/
trails, rich symbol dispatch, network input, portable FPS thresholds, or Native Image.

## Acceptance criteria

- The runnable example starts, pauses, resets, closes, pans, and zooms while showing 10k latest
  filtered positions over the Natural Earth chart.
- The maximum-FPS control bounds frame requests without sleeping or blocking the EDT; achieved FPS,
  frame counts/latencies, update counts, seed, workers, and memory are visible.
- At most one request and one published frame exist; buffer ownership is race-free and stale viewport
  frames never paint.
- `liveTrackSmoke` exercises deterministic 10k simulation/filter/frame/cleanup behavior headlessly
  and remains under the approved five-minute ceiling.
- A maintainer approves visual coherence, responsive navigation, pacing, and readable telemetry.

## Required tests

Unit/integration tests for projection, pixel composition, frame ownership, stale generation/resize,
timer cap semantics, skipped requests, telemetry arithmetic, EDT confinement, cancellation/close,
10k deterministic output, offscreen pixels, and a manual runnable-example review.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew liveTrackSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **10k live-picture visual, navigation, pacing, and telemetry approval**. This task
creates `liveTrackSmoke`; earlier G15 tasks must not invoke it.
