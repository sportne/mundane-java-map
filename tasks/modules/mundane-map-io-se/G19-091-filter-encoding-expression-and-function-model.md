# G19-091 — Filter Encoding 1.1 expression and function model

Status: Proposed
Depends on: G19-002, G19-090
Gate: G19
Type: HITL

## Goal

Implement the complete approved Filter Encoding 1.1 expression/value profile and a closed,
deterministic function catalog.

## Context

The current adapter supports only property names and literals. Arithmetic, nested expressions,
typed values, and functions are absent.

## Scope

- Pin the FE 1.1/Corrigendum expression grammar used by SE 1.1.
- Add immutable property/value references, typed literals, add/subtract/multiply/divide, and nested
  expression trees with explicit null/nil, numeric, string, boolean, date/time-value, and geometry types.
- Define deterministic conversion, promotion, precision, overflow, locale, Unicode, and missing-value rules.
- Add a closed approved standard function registry with stable names/signatures/costs; reject arbitrary
  code, reflection, dynamic discovery, catastrophic regular expressions, and vendor functions.
- Bound AST nodes/depth, literal sizes, numeric work, string expansion, collection values, and aggregate
  function cost prospectively.

## Out of scope

- Predicate operators, FES 2.0 functions, scripting, and caller-defined executable functions.

## Acceptance criteria

- Every approved expression parses, compiles, evaluates, and round-trips through the neutral AST.
- Type/null/coercion/precision behavior is explicit and identical across rule and symbolizer use sites.
- Unsupported or over-budget expressions fail during compilation with stable diagnostics.

## Required tests

- Grammar/type/null/arithmetic/function/signature/Unicode/numeric-boundary matrices.
- Deep/wide AST, expansion/cost limits, unknown functions, hostile strings, and independent FE fixtures.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, the approved OGC/corpus lane,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact FE 1.1 function inventory and any independent corpus.
