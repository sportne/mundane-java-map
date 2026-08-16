# G19-147 — Raster, heatmap, hillshade, and color-relief layers

Status: Proposed
Depends on: G19-012, G19-141, G19-143, G19-145
Gate: G19
Type: AFK

## Goal

Implement every pinned 2D property for raster, heatmap, hillshade, and color-relief layers.

## Context

These layer types are currently rejected. They require bounded raster color processing, point-density
accumulation, elevation derivatives, ramps, resampling, fades, edge behavior, and expression contexts.

## Scope

- Generate exact v26.2.1 property/default/dependency/expression inventories for all four layers.
- Implement raster opacity/hue/brightness/saturation/contrast/fade/resampling/color controls and transitions.
- Implement heatmap weights/intensity/radius/density/color/opacity with deterministic bounded accumulation.
- Implement hillshade illumination/exaggeration/shadow/highlight/accent/resampling and tile-edge behavior.
- Implement color-relief ramps/resampling/opacity over neutral elevation; preserve no-data/unit semantics.
- Define wrap/clip/tile seams, hit/interaction, AWT/Vaadin/SVG representability and total pixel/kernel work limits.

## Out of scope

- Terrain mesh, GPU shader identity, arbitrary kernels/shaders, and unbounded temporal raster processing.

## Acceptance criteria

- Every pinned property compiles and renders with documented reference tolerances or fails explicitly at a 2D boundary.
- Tile seams, wrap, transitions, no-data and source updates remain atomic and deterministic.
- Pixel/kernel/elevation work is prospectively bounded before large allocation or partial paint.

## Required tests

- Generated property matrix and official/independent raster/heatmap/DEM/color-relief visual fixtures.
- No-data/edge/wrap/resampling/fade/color/transition/update, hostile dimensions/kernels, and exact budgets.

## Validation

Run module/raster/elevation/rendering lanes, qualityGate, and `git diff --check`.

## Notes

None.
