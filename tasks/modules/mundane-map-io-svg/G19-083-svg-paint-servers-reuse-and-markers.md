# G19-083 — SVG paint servers, reusable definitions, and markers

Status: Proposed
Depends on: G19-081, G19-082
Gate: G19
Type: AFK

## Goal

Implement bounded gradients, patterns, definitions, `symbol`/`use`, paint references/fallbacks, and
path markers with exact SVG coordinate and inheritance semantics.

## Context

The released importer supports solid colors only and has no ID graph or reusable definitions.
Common map symbols depend heavily on gradients, patterns, reuse, and start/mid/end markers.

## Scope

- Add linear/radial gradients, stops, spread methods, objectBoundingBox/userSpace units, transforms,
  href inheritance, color interpolation, opacity, and fallback paint.
- Add bounded patterns with viewport/viewBox/content units/transforms and nested static scene content.
- Resolve `defs`, `symbol`, and local/catalog `use` with correct instance style/viewport/transform,
  document order, and reference identity without exponential materialization.
- Implement marker start/mid/end placement, orientation, units, viewBox/aspect, context fill/stroke,
  zero-length/subpath behavior, and interaction with vector effects.
- Bound definitions, stops, pattern pixels/work, instances, marker placements, reference depth/fan-out,
  and retained paints.

## Out of scope

- Animation, script-mutated definitions, ambient external references, and filter primitives.

## Acceptance criteria

- Gradient/pattern/reuse/marker output matches independent renderers within declared tolerances.
- Cycles, unresolved/wrong-type references, and expansion limits have stable atomic outcomes.
- Repeated instances retain semantics without unbounded duplicated scene ownership.

## Required tests

- Gradient/pattern unit/transform/spread/inheritance/fallback matrices, forward/cyclic references,
  nested symbol/use instances, marker orientation/scaling/zero-length paths, context paints, bounding-
  box degeneracy, cancellation, expansion/pixel/work limits, and renderer comparisons.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, SVG paint/rendering lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
