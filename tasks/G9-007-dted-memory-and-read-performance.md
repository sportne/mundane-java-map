# G9-007 — DTED memory and read performance

Status: Proposed
Depends on: G9-004, G9-006
Gate: G9
Type: AFK

## Goal

Measure DTED open, read, query, and memory behavior and make an evidence-based decision on eager versus
windowed or lazy access for larger Level 1/2 datasets.

## Context

G7 creates the `performanceEvidence` lane and its reproducibility conventions. DTED density varies
substantially by level; architectural complexity must follow measurements rather than assumptions.

## Scope

Add deterministic DTED scenarios to `performanceEvidence`, including corpus inputs and reproducibly
generated larger valid grids. Record environment, fixture dimensions, warmup, repetitions, open/read
time, query throughput, retained heap, and peak allocation. Compare the current reader with a simple
theoretical/experimental windowed approach and record the decision and documented target.

## Out of scope

Microbenchmark theater, CI pass/fail thresholds across unlike machines, native code, memory mapping by
default, unrelated rendering optimization, and a broad reader rewrite without supporting evidence.

## Acceptance criteria

- Evidence covers representative Level 0, 1, and 2 dimensions and both sequential and position-query
  workloads with reproducible inputs.
- Results include enough environment and methodology detail for another maintainer to repeat them.
- The repository records whether eager access meets the documented memory/read targets or whether a
  bounded windowed/lazy follow-up task is required.
- If a redesign is required, a separate implementation task specifies cache/window size, lifecycle,
  correctness tests, and measurable acceptance targets; this task does not hide it in incidental code.
- No custom native performance library or native parsing path is introduced.

## Required tests

Correctness checks around performance fixtures, repeatability checks for generated inputs, and evidence
runs for open/read, sequential traversal, random queries, retained memory, and peak allocation.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Treat performance numbers as evidence with an environment, not universal guarantees. A custom native
library requires a separate decision after pure Java fails a documented target.
