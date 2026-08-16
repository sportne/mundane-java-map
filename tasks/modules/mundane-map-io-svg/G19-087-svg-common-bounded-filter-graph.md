# G19-087 — SVG common bounded filter graph

Status: Proposed
Depends on: G19-084, G19-086
Gate: G19
Type: HITL

## Goal

Implement the approved common static SVG filter graph with prospective region, pixel, kernel, graph,
and operation limits and exact compositing order.

## Context

Filters are absent. The approved profile includes common map effects but deliberately excludes
turbulence, displacement, and lighting complexity.

## Scope

- Implement filter/result graph semantics and inputs for SourceGraphic/SourceAlpha and prior named
  results, with filter/primitive units, regions, transforms, color interpolation, and edge behavior.
- Support `feGaussianBlur`, `feDropShadow`, `feOffset`, `feFlood`, `feColorMatrix`, `feBlend`,
  `feComposite`, `feMerge`/`feMergeNode`, and `feMorphology` plus their approved CSS filter-function
  equivalents where semantics are exact.
- Apply filter, clipping, masking, and opacity in the specified order with correct premultiplied-
  alpha/color-space behavior and conservative output bounds.
- Reject unknown/wrong-input/cyclic graphs atomically; do not silently pass through unsupported
  primitives when that changes rendering.
- Prospectively bound nodes/edges/references, expanded regions, offscreen pixels/bytes, blur and
  morphology radii, passes, aggregate operations, cancellation, and temporary ownership.

## Out of scope

- Turbulence, displacement maps, diffuse/specular lighting and light sources, convolve matrices,
  component transfer, filter images/tiles, custom shaders, and animated filter values.

## Acceptance criteria

- Declared filter graphs match independent renderers within pinned edge/color/pixel tolerances.
- Every exclusion has stable behavior and cannot masquerade as a successfully rendered graph.
- Huge/cyclic/adversarial filters fail before excessive allocation/work and release all buffers.

## Required tests

- Every primitive mode/input/result/unit/region/color-space/edge case; multi-node graphs and ordering
  with clip/mask/opacity; wrong/cyclic references; huge kernels/regions/graphs, cancellation, pixel/
  operation limits, cleanup, and cross-renderer images.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, filter/rendering evidence lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the Filter Effects draft baseline, exact primitive/exclusion
matrix, implementation tolerances, and external fixtures before completion.
