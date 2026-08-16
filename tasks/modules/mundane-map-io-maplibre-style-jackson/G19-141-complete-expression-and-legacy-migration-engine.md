# G19-141 — Complete expression and legacy migration engine

Status: Proposed
Depends on: G19-002, G19-011, G19-140
Gate: G19
Type: AFK

## Goal

Implement the complete pinned 87-operator typed expression language and deterministic migration of legacy filters/functions.

## Context

The adapter supports a small closed algebra. Valid v26.2.1 styles use arrays/objects, variables, state,
formatting, images, colors, locale, spatial predicates, math, strings, and property-specific contexts.

## Scope

- Implement the reference type system, overload resolution, assertions/coercions, errors, scopes, short circuiting,
  variables, decisions, interpolation, collections, feature/geometry/state/camera/elevation inputs, math, strings,
  collation/formatting/images/colors, and spatial predicates for all 87 operators.
- Enforce each source/layout/paint property's declared expression parameters and runtime context restrictions.
- Supply immutable explicit feature, global state, camera, locale, Unicode, geometry, resource, and clock contexts.
- Parse legacy filters/property functions separately, migrate losslessly to typed current expressions with observations,
  and reject ambiguous or mixed syntax; never emit legacy syntax.
- Bound AST/nodes/depth/variables/branches/stops/literals/strings/arrays/objects/geometry/collation/evaluations/work.

## Out of scope

- JavaScript, user code execution, unlisted operators, and implicit process locale/clock/state.

## Acceptance criteria

- Generated tests account for every operator/overload/property parameter in v26.2.1.
- Reference-compatible evaluations and legacy migrations are deterministic for the same explicit context.
- Type, context, evaluation, migration, and work failures cannot partially compile or publish a style.

## Required tests

- Full operator/overload/type/property-context matrix and upstream expression/reference fixtures.
- Legacy/current/mixed grammar, Unicode/collation, spatial boundaries, deep AST, huge stops, and evaluation budgets.

## Validation

Run module/reference/differential/fuzz checks, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
