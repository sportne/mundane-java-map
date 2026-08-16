# G19-081 — SVG geometry, viewports, units, and transforms

Status: Proposed
Depends on: G19-080
Gate: G19
Type: AFK

## Goal

Implement the complete declared static geometry and coordinate-system surface for root/nested SVG
viewports, reusable symbols, and marker-compatible vector content.

## Context

The importer handles basic shapes and paths but requires a root viewBox, accepts only unitless values,
rejects nested SVG, and supports a narrow aspect/geometry-property profile.

## Scope

- Complete path grammar/degenerate behavior and geometry properties for path, rect/rounded rect,
  circle, ellipse, line, polyline, and polygon.
- Add root/nested `svg`, groups, `defs`, `symbol`, and structural placeholders required by `use`.
- Implement viewport establishment, `viewBox`, `preserveAspectRatio`, overflow, object/stroke bounding
  boxes, supported absolute/font/percentage units, and context-dependent percentage resolution.
- Complete transform lists/origins, nested coordinate composition, vector-effect/non-scaling stroke,
  and finite/overflow checks in the specified order.
- Bound path commands/coordinates/arcs, transformed geometry, viewport nesting, unit resolution, and
  prospective retained scene work.

## Out of scope

- CSS cascade, resolved `use`, markers, paints, text, images, clipping, and filters.

## Acceptance criteria

- Declared SVG geometry/viewport fixtures match independent static renderers under exact/tolerant
  coordinate comparisons.
- Nested units, percentages, transforms, aspect modes, and degenerate shapes follow one documented
  processing order.
- Extreme finite values fail before non-finite retained geometry or partial output.

## Required tests

- Every path command/implicit repetition/arc flag, shape boundary, nested viewport/aspect/overflow,
  absolute/em/font/percentage unit, transform/vector-effect, degeneracy, numeric overflow, cancellation,
  and work-limit matrix.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, SVG geometry/rendering fixtures,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
