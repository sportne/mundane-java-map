# G15-007 — Million-track stress and evidence lane

Status: Proposed
Depends on: G15-006
Gate: G15
Type: HITL

## Goal

Run the same bounded live picture at one million tracks and produce complete machine-readable and
LLM-readable evidence for the 10k, 100k, and 1m tiers.

## Context

G15-006 qualifies the 100k execution shape. Full one-million evidence is intentionally opt-in and
uses `/tmp` because repository work under `/mnt/d` previously distorted performance iteration.

## Scope

Add the 1m tier with preflight allocation checks; create `liveTrackEvidence`; implement fixed warmup/
measurement profiles, `/tmp` workspace selection, JSON and Markdown reports, environment/configuration
capture, FPS/frame-latency/update/backlog/shard/memory/accuracy metrics, terminal failure reports, and
documented invocation for each tier. Run and retain one named-machine evidence set.

## Out of scope

Portable minimum-FPS gates, default quality/CI execution, hardware comparisons, runtime downloads,
native/GPU/vector acceleration, report dropping, production benchmarking APIs, or performance claims
for untested operating systems.

## Acceptance criteria

- `liveTrackEvidence` accepts exactly the approved 10k/100k/1m profiles and records cap, seed,
  workers, durations, JVM/OS/CPU/heap, workload, storage, updates, frames, latency, backlog, and error
  summaries.
- JSON is schema/version tagged and machine parseable; Markdown is concise, self-contained, and
  LLM readable; both describe success, cancellation, or failure.
- The 1m run preflights memory, remains controllable, publishes no corrupt/stale frame, and reports
  achieved FPS rather than enforcing a portable target.
- One complete named-machine evidence set covers all tiers with the same documented configuration or
  explicitly records differences.
- A maintainer approves the evidence interpretation and honest support wording.

## Required tests

Report-schema/round-trip tests, deterministic metric aggregation, profile/argument rejection,
workspace/cleanup tests, bounded reduced-duration 1m headless correctness, terminal-failure report,
and manual canonical runs for all tiers.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew liveTrackSmoke --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=10k --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=100k --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=1m --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **named-machine 10k/100k/1m evidence and support-wording approval**. This task
creates `liveTrackEvidence`; it remains separate from `qualityGate` and ordinary CI.
