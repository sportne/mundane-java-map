# G19-145 — Background, circle, fill, and line layers

Status: Proposed
Depends on: G19-002, G19-013, G19-141, G19-144
Gate: G19
Type: AFK

## Goal

Implement every pinned 2D layout/paint property for background, circle, fill, and line layers.

## Context

The current adapter covers small literal/expression subsets and rejects patterns, gradients, dash/gap,
translations, most caps/joins/order/elevation-reference settings, and complete transition behavior.

## Scope

- Generate exact v26.2.1 property/default/dependency/expression-parameter inventories for the four layers.
- Implement colors/color spaces, opacity, antialiasing, blur, translation/anchors, patterns, caps/joins,
  gap/offset/dashes/gradients/trimming, sort/order, line/fill elevation references, and transition sampling.
- Map semantics into neutral portrayal/render primitives, extending toolkit-neutral APIs where necessary.
- Define projection, clipping, tile-boundary, wrap, hit-test, interaction, label, AWT/Vaadin/SVG parity.
- Bound expression evaluations, pattern/gradient/dash/offset geometry, transitions, primitives, and paint work.

## Out of scope

- Shader injection, GPU pixel identity, and fill-extrusion rendering.

## Acceptance criteria

- Every pinned property is implemented or rejected only by the documented 3D boundary, never ignored.
- Official/independent fixtures match reference 2D geometry/order/color behavior within declared tolerances.
- Invalid/over-budget layers fail before partial style/scene publication.

## Required tests

- Generated property/default/dependency/expression matrix and visual fixtures for every property family.
- Tile/wrap/clip/hit/transition/order boundaries, huge patterns/dashes/gradients, and cross-renderer parity.

## Validation

Run module and rendering-regression lanes, qualityGate, and `git diff --check`.

## Notes

None.
