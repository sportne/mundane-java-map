# G19-213 — Integration, browser, and native performance evidence

Status: Proposed
Depends on: G19-189, G19-204, G19-212
Gate: G19
Type: HITL

## Goal

Retain and expand deterministic end-to-end performance evidence where JMH is not the correct execution model, without mixing incomparable measurements.

## Context

Scene construction, rendering, browser transport/paint, file/network lifecycle, workspace packaging, and Native Image startup cross subsystem boundaries that a microbenchmark harness should not erase.

## Scope

- Version the existing deterministic runner as an integration-only harness with fixed semantic/work oracles and bounded scenarios.
- Cover representative full query/render/edit/navigation, format streaming, workspace open/save/package, and cleanup workloads.
- Consume real-browser Vaadin query/transfer/decode/paint/memory evidence from the owning browser matrix.
- Consume Native Image build/startup/steady-state/workload/resource evidence from the owning native host matrix.
- Keep JVM JMH, JVM integration, browser, and native scores in separate named result families with explicit non-comparability.
- Retain JFR only as diagnostic evidence tied to a selected run, not as the primary benchmark score.

## Out of scope

- Simulating browser or Native Image performance inside JMH, live public-network timing, or one synthetic aggregate score.

## Acceptance criteria

- Each material cross-system workflow has bounded, repeatable, semantically asserted evidence in its owning environment.
- Transfer, query, encode/decode, paint, allocation/memory, resource, startup, and settle timings are named rather than collapsed.
- Repeated soak returns resources to baseline and records failures/cancellations without hiding partial work.

## Required tests

- Deterministic integration runner self-tests and full scenario execution.
- Real-browser and five-host native performance evidence, repeated navigation/open/close/failure soak, schema/provenance checks, and result-family isolation tests.

## Validation

Run `./gradlew performanceEvidence --console=plain`, the Vaadin browser and Native Image performance lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve representative end-to-end scenarios and environment-specific evidence.
