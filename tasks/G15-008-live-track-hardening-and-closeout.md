# G15-008 — Live-track hardening and closeout

Status: Proposed
Depends on: G15-007
Gate: G15
Type: HITL

## Goal

Close the live-track stress capability with deterministic lifecycle, overload, replay, documentation,
visual, and holistic simplicity evidence.

## Context

G15-007 delivers all population tiers and evidence. Closeout must ensure the stress-specific kernel,
coordinator, and overlay remain local unless real reuse—not architectural preference—justifies a
public abstraction.

## Scope

Audit configuration/overflow failures, report backlog, worker failure, pause/reset, resize/navigation,
frame races, cancellation, shutdown, and buffer cleanup; add deterministic replay evidence and a
bounded long-run soak; complete example usage/support/provenance documentation and internal Javadocs;
review telemetry/rendering; reconcile design/task/roadmap status; and perform G15 simplicity review.

## Out of scope

Public tracker/live-layer APIs, new modules or dependencies, Native Image, operational accuracy
claims, networking, track association, selection, labels, trails, persistence, richer symbology, or
new performance optimization without fresh evidence.

## Acceptance criteria

- Every failure/overload/lifecycle transition has deterministic counters, visible terminal state,
  bounded cancellation, and no leaked worker, map source, or frame buffer.
- Repeated seeded runs agree on reports/estimates and bounded soak evidence shows stable logical and
  observed resource use.
- Example documentation states workload, equations/provenance, coordinate/accuracy limits, controls,
  reports, `/tmp` behavior, JVM-only scope, and how to reproduce each tier.
- The final visual example and evidence reports are maintainer approved.
- Holistic review confirms example-local specialization is still simpler than a public live-source,
  tracking, or rendering framework and preserves all earlier project boundaries.

## Required tests

Failure-injection and race tests, pause/reset/replay, navigation/resize stale-frame tests, bounded
soak, thread/resource leak checks, 10k smoke, representative evidence report, Javadocs, rendering
regression for the chart/overlay contract, and manual viewer/evidence review.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew liveTrackSmoke --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=10k --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **final live-picture, evidence, documentation, and G15 simplicity approval**. The
full 100k/1m evidence is not repeated by default when the prior immutable reports remain current.
