# G19-084 — SVG clipping, masking, and static compositing

Status: Proposed
Depends on: G19-082, G19-083
Gate: G19
Type: AFK

## Goal

Implement group opacity, paint order, clipping paths, alpha/luminance masks, overflow, and bounded
offscreen compositing in the SVG-specified rendering order.

## Context

Current output flattens per-shape opacity and cannot represent clips, masks, or isolated group
composition. These operations affect both visible pixels and map-symbol bounds.

## Scope

- Implement group isolation/opacity, fill/stroke/marker paint order, display/visibility, and overflow.
- Add `clipPath` with user/object-bounding units, transforms, clip rules, nested use, and geometry-only
  semantics.
- Add alpha/luminance `mask`, mask/content units, regions, color interpolation, nested static content,
  and transparent-outside behavior.
- Freeze render order among filters, clipping, masking, and opacity and expose conservative/exact
  bounds needed by placement and hit-testing.
- Prospectively bound clip/mask graphs, rasterized regions/pixels/bytes, nesting, references,
  compositing operations, and temporary ownership.

## Out of scope

- Filter graph implementation, blend modes beyond those separately approved, and dynamic compositing.

## Acceptance criteria

- Nested clips/masks/group opacity follow specified order and match independent renderer pixels.
- Empty/degenerate/offscreen and object-bounding-box cases remain deterministic.
- Rejection/cancellation cannot retain or publish partially composited resources.

## Required tests

- Clip/mask unit/transform/rule/region/luminance matrices; nested use/paint/order/opacity/overflow;
  degenerate bounds, cycles, huge offscreen regions, cancellation, pixel/work limits, and renderer
  comparisons.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, SVG rendering lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
