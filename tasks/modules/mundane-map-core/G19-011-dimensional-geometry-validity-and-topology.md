# G19-011 — Dimensional geometry validity and topology

Status: Proposed
Depends on: G19-001
Gate: G19
Type: AFK

## Goal

Make core geometry operations dimension-preserving and add bounded validity/topology services needed
by expert format ingestion, editing, clipping, and hit testing.

## Context

Current algorithms assume non-empty XY homogeneous geometries. G19 adapters require explicit behavior
for empty, Z/M, mixed collections, ring validity, containment, intersection, clipping, and repair.

## Scope

- Define dimensional propagation for transforms, splitting, clipping, snapping, and editing.
- Add OGC simple-feature validity checks with stable location/reason diagnostics.
- Add bounded predicates and overlay operations required by current render/query workflows.
- Offer explicit opt-in canonical repair for a frozen set of defects.
- Retain deterministic ordering and packed primitive storage.

## Out of scope

- Unbounded general-purpose computational geometry or heuristic repair of arbitrary corrupt data.

## Acceptance criteria

- All public algorithms document emptiness and Z/M propagation.
- Validity and topology agree with an independent reference corpus within declared numeric tolerances.
- Work and intermediate storage have prospective limits and atomic failure.
- Repair never runs implicitly during parsing or rendering.

## Required tests

- Dimension/empty/collection matrix for every touched algorithm.
- OGC-style validity and predicate fixtures, fuzz/property tests, and adversarial complexity cases.
- Regression tests proving existing XY results remain stable.

## Validation

Run `./gradlew :modules:mundane-map-core:check --console=plain`, relevant corpus/performance lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
