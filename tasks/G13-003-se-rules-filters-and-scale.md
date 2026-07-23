# G13-003 — SE rules, filters, and scale

Status: Proposed
Depends on: G13-002
Gate: G13
Type: AFK

## Goal

Evaluate bounded ordered SE rules by feature attributes and scale while keeping paint, hit testing,
query projection, and multiple-match composition consistent.

## Context

G13-002 proves secure parsing and literal markers. G13-001 fixes the closed rule/predicate bridge and
explicit scale-context policy.

## Scope

Implement immutable ordered rule plans, approved Filter 1.1 explicit-null/comparison/between/boolean
predicates, ElseFilter, min/max scale denominators, canonical scalar conversion, required-attribute
projection, one all-role evaluation result, geometry-compatible role composition, linear-CRS MapView
scale context, and public evaluation tests.

## Out of scope

Arithmetic/string/date functions, geometry expressions, spatial filters, callbacks, locale coercion,
implicit geographic scale, label rules, line/polygon symbolizers, or expression JIT/optimization.

## Acceptance criteria

- Rule order follows the painter's model and all matching rules compose deterministically.
- Filters have exact missing/null/type/numeric semantics and bounded depth/node counts.
- ElseFilter and lower-inclusive/upper-exclusive scale ranges follow the approved profile.
- Source queries request exactly the stable deduplicated attributes needed by the style.
- Unsupported CRS/unit scale context fails with a neutral portrayal problem at attachment;
  paint/hit/selection resolve through the same all-role operation.

## Required tests

Predicate truth/type tables, boolean depth/limits, ElseFilter, scale boundaries, multi-rule order,
attribute projection, source/snapshot interaction agreement, immutable copies, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-io-se:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The bridge remains a finite closed evaluator; no adapter can inject executable predicates.
