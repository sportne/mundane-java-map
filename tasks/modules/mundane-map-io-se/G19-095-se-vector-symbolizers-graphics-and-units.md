# G19-095 — SE vector symbolizers, graphics, and units

Status: Proposed
Depends on: G19-044, G19-089, G19-094
Gate: G19
Type: HITL

## Goal

Complete the approved SE 1.1 PointSymbolizer, LineSymbolizer, and PolygonSymbolizer surface, including
standard graphics, offsets, expressions, and units.

## Context

The current adapter supports basic pixel-unit marks and solid strokes/fills but rejects many standard
graphic, placement, line, polygon, and unit constructs.

## Scope

- Implement point graphic ordering/fallback, opacity, size, rotation, anchor, displacement, mark/external
  mark/external graphic formats, and standard well-known marks through the closed resource catalog.
- Complete stroke/fill parameters, cap, join, dash array/offset, graphic stroke, initial/gap rules,
  graphic fill, perpendicular offset, displacement, and polygon-hole behavior.
- Apply compiled SE expressions to every standard parameter that permits them with type/unit validation.
- Implement the SE pixel, metre, and foot UOM URIs with explicit scale-denominator, map/display, CRS-axis,
  and non-finite/overflow rules.
- Bound graphic candidates, marks, dash work, pattern/stroke expansion, resources, primitives, and pixels;
  preflight before portrayal publication.

## Out of scope

- Text/raster symbolizers, ambient graphic loading, vendor marks/options, and silent style approximation.

## Acceptance criteria

- Every approved vector construct maps losslessly to neutral portrayal and renders equivalently in AWT/Vaadin.
- Graphic fallback and resource/media failures follow deterministic SE order with stable diagnostics.
- Unit behavior remains correct across scale, DPI, CRS, zoom, rotation, and world-wrap boundaries.

## Required tests

- Point/line/polygon/property/expression/UOM/resource/fallback/dash/offset matrix and independent styles.
- Cross-renderer goldens, extreme units/scales, hostile expansion, missing/wrong media, and atomic limits.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, rendering/corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves unit/scale tolerances and independent renderer/style evidence.
