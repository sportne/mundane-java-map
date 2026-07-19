# G14-004 — Bounded MapLibre expressions

Status: Proposed
Depends on: G14-003
Gate: G14
Type: AFK

## Goal

Evaluate the approved typed MapLibre expression subset for supported paint, layout, and filter
properties without creating a generic JSON expression runtime.

## Context

G14-003 supplies source/filter/zoom binding. G14-001 fixes the exact literal, get/has, geometry/zoom,
comparison/boolean, match/case/step, linear-interpolate, and minimal conversion set.

## Scope

Add immutable bounded expression nodes and Jackson parsing; static type validation; exact missing/
null/numeric/color/string semantics; stable required-attribute discovery; deterministic evaluation;
and expression-backed circle/line/fill properties and filters.

## Out of scope

Feature/global state, formatted text, image expressions, locale/collator, runtime object/array
construction, exponential/cubic interpolation unless explicitly approved, user functions, scripting,
reflection, bytecode generation, or silent coercion.

## Acceptance criteria

- Every approved operator has construction-time arity/type validation and documented result rules.
- Node/depth/string/stop/category limits reject before excessive allocation or recursion.
- Missing, null, non-finite, type mismatch, interpolation, and color failures use stable diagnostics.
- Required attributes are exact and evaluation produces identical paint/hit/selection outcomes.
- Unsupported operators remain rejected even when their result would be unused.

## Required tests

Operator truth/type tables, nested expressions, all boundary stops/categories, null/missing cases,
limits and deterministic mutation, required-attribute projection, rendering/interaction agreement,
and JVM allocation sanity.

## Validation

```bash
./gradlew :modules:mundane-map-io-maplibre-style-jackson:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Only operators exercised by supported properties are admitted; “parse all expressions for later” is
not acceptable scope.
