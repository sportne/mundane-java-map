# G3-002 — Symbol-Aware Hit Testing and Selection

Status: Proposed
Depends on: G3-001, G2-005
Gate: G3
Type: AFK

## Goal

Hit-test the visible painted footprint of points, lines, polygons, and symbols in screen-pixel units
and use the deterministic topmost hit for single-feature selection.

## Context

Layers and features are currently painted in list order by `MapView`; there is no public hit result or
selection state. G2-005 completes vector/raster/composite symbol rendering, and G3-001 provides click
routing. Hit order must mirror paint order and account for viewport scale, symbol placement, stroke
width, polygon holes, and transparent content.

## Scope

- Immutable hit-result and single-selection values in `mundane-map-api`.
- Toolkit-neutral geometry distance/containment algorithms in `mundane-map-core`.
- Symbol-aware screen-space hit orchestration and click selection in `mundane-map-awt`.
- Unit and offscreen integration tests plus public Javadocs.

## Out of scope

- Multi-selection, selection overlays, hover events, spatial indexing, geometry repair, and editing.
- Selection persistence across source refreshes beyond stable layer and feature identifiers.

## Acceptance criteria

- Hit queries accept a finite non-negative screen-pixel tolerance and return an immutable, stable
  ordered result identified by layer and feature IDs.
- Point/vector/raster/composite symbols use their actual anchor, transform, opacity, and painted
  footprint; fully non-painted content is not hittable.
- Line distance includes rendered stroke width plus tolerance and handles repeated/zero-length
  segments without non-finite math.
- Polygon fill excludes holes, while visible exterior/interior strokes remain hittable within stroke
  width plus tolerance.
- Results order layers, features, and composite children in reverse paint order; the first result is
  always the same topmost visible feature for identical inputs.
- An unmodified primary click selects the topmost hit and a click on empty space clears selection;
  programmatic get/set/clear operations use defensive immutable state.
- Selection invalidates predictably when the selected layer/feature is removed.

## Required tests

- Core tests for point, segment, ring, hole, boundary, tolerance, and degenerate geometry cases.
- AWT tests for every built-in vector marker, raster icon, transformed/composite symbol, line width,
  polygon fill/stroke, overlap order, and transparent symbols.
- Interaction tests for click-select, empty-clear, programmatic state, and removed content.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use direct iteration for this slice; the packed spatial index belongs to G7. Avoid Java2D types in
public hit results or core algorithms even when AWT uses shapes internally for renderer-specific
footprints.

