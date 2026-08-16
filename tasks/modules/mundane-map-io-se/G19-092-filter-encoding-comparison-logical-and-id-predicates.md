# G19-092 — Filter Encoding comparison, logical, and ID predicates

Status: Proposed
Depends on: G19-091
Gate: G19
Type: AFK

## Goal

Complete deterministic FE 1.1 comparison, logical, and feature-identity predicate behavior.

## Context

The released evaluator has a useful comparison/logical subset but lacks a complete pinned contract,
identity predicates, and consistent typed/null/coercion semantics.

## Scope

- Implement all approved FE 1.1 binary comparison, between, like, null, logical, and feature-ID forms.
- Define wildcard/single-character/escape, case matching, Unicode, numeric precision, date/time-value,
  null/nil/unknown, short-circuiting, and three-valued evaluation semantics.
- Map feature IDs to the neutral identity contract without leaking or guessing source identifiers.
- Preserve document order and bounded deterministic evaluation; charge predicate nodes, string matching,
  conversions, and feature-ID sets against prospective limits.
- Emit stable compile/evaluation diagnostics without source-value echo.

## Out of scope

- Spatial predicates, temporal predicates, SQL semantics, and datastore query pushdown.

## Acceptance criteria

- Approved predicates agree with the pinned FE 1.1 semantics for all supported value types.
- Evaluation results are invariant across parser, in-memory compiler, AWT, and Vaadin rule selection.
- Invalid patterns/types/identities and all limit breaches fail deterministically.

## Required tests

- Operator/type/null/case/wildcard/escape/identity/short-circuit truth tables and independent fixtures.
- Large ID sets, hostile patterns, Unicode edge cases, numeric/time boundaries, and diagnostic hygiene.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

Filter Encoding 2.0 temporal predicates remain outside this SE 1.1 task family.
