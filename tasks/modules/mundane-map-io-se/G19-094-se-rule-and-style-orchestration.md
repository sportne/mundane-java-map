# G19-094 — SE rule and style orchestration

Status: Proposed
Depends on: G19-092, G19-093
Gate: G19
Type: AFK

## Goal

Complete SE 1.1 feature/coverage style, rule, scale, geometry-selection, and omission behavior before
expanding individual symbolizers.

## Context

The current ordered-rule bridge supports basic filters, else rules, and scale ranges, but not the full
SE orchestration contract or CoverageStyle.

## Scope

- Implement feature-type and coverage style ordering, semantic-type identifiers, descriptions,
  feature-type/coverage constraints, rule names/titles/abstracts, filters, else filters, and symbolizer order.
- Define inclusive/exclusive scale-denominator boundaries, invalid scale handling, and consistent browser/
  AWT portrayal contexts.
- Add standard geometry expressions/property selection, type checking, default-geometry resolution,
  empty/unknown behavior, and per-symbolizer omission semantics.
- Compile immutable style plans before source traversal and bound rules, symbolizers, geometry selection,
  evaluation work, diagnostics, and produced portrayal prospectively.
- Preserve stable rule/style/source context while preventing source-value leakage.

## Out of scope

- Individual advanced symbolizer rendering and datastore-side filter pushdown.

## Acceptance criteria

- Supported documents select the same ordered symbolizers in core, AWT, and Vaadin contexts.
- Else/scale/geometry/empty/failure behavior is exact, bounded, and atomic.
- Feature and coverage orchestration can host all later approved symbolizers without adapter bypasses.

## Required tests

- Rule/filter/else/scale-boundary/semantic-type/geometry/order/omission matrices.
- Mixed feature/coverage styles, missing properties, hostile rule counts, evaluation limits, and parity tests.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

None.
