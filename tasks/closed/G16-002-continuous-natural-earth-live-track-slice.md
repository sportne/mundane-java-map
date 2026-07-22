# G16-002 — Continuous Natural Earth and live-track slice

Status: Complete
Depends on: G16-001
Gate: G16
Type: AFK

## Goal

Let the G15 viewer pan repeatedly east or west across the dateline while continuously displaying
Natural Earth and every canonical track copy with no blank world edge or EDT regression.

## Context

G15 already owns one prepared EPSG:3857 Natural Earth snapshot, a detached track overlay, and a
coalescing off-EDT two-screen background cache. Tracks remain canonical and the background renderer
currently paints only the base Web Mercator world. This slice is the first observable proof of the
G16-001 wrap math and limits.

## Scope

Add the approved checked periodic-X planner needed by the example; render every Natural Earth and
track copy intersecting the current/overscan viewport; keep cache coordinates continuous; preserve
fit-world, zoom anchoring, frame generations, pacing, telemetry, source cleanup, and deterministic
track state. Extend the presentation probe with east/west multi-world pan and seam zoom traces.

## Out of scope

General `MapView` source wrapping, public binding configuration, arbitrary dateline-crossing geometry,
track trails/selection, raster sources, or new performance thresholds.

## Acceptance criteria

- 10k, 100k, and 1m viewers show land and tracks through repeated eastbound and westbound crossings.
- Canonical track state and IOU-Kalman Filter results do not change with the chosen visual copy.
- Covered pans remain cache-only; replacement rendering stays off the EDT and coalesces stale demand.
- Views wider than one world obey the approved copy limit with deterministic visible copies or the
  approved structured failure behavior.
- Close cancels background work and releases the prepared source, worker, frames, and staged files.

## Required tests

Periodic-X boundary/precision tests; Natural Earth seam and multiple-copy rendering; track-copy
projection; east/west pan and anchored zoom traces; cache coverage/coalescing; 10k smoke; 1m
presentation probe; lifecycle and tolerant color/geometry assertions.

## Validation

```bash
./gradlew :examples:live-track-stress:check liveTrackSmoke --console=plain
./gradlew :examples:live-track-stress:liveTrackPresentation -PliveTrackProfile=1m --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not modulo mutable track/filter state or relax the projection. Choose translated visual copies
from canonical positions. The presentation timing output remains evidence, not a pass/fail SLA.

Completion record (2026-07-22): the shared bounded core planner drives repeated Natural Earth and
canonical packed tracks, and the presentation probe exercises cached seam-adjacent and multi-world
east/west navigation without moving background work onto the EDT.
