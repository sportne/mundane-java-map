# G2-004 — Line Endpoints and Hatch Fills

Status: Complete
Depends on: G2-003
Gate: G2
Type: AFK

## Goal

Render styled lines with optional start/end markers and polygons with bounded basic hatch patterns,
including arrowheads, through the established symbol and placement pipeline.

## Context

`MapView` currently applies one `BasicStroke` to a line and an even-odd solid fill to a polygon.
G2-003 supplies deterministic symbol transforms and composition. Endpoint orientation must derive
from visible line geometry, and hatches must respect polygon holes without moving Java2D constructs
into `mundane-map-api`.

## Scope

- Line-symbol endpoint and hatch-fill public values in `mundane-map-api`.
- Toolkit-neutral endpoint/tangent and bounded hatch-layout algorithms in `mundane-map-core`.
- Java2D line, arrowhead, and clipped hatch rendering in `mundane-map-awt`.
- Unit and offscreen rendering tests plus public Javadocs.

## Out of scope

- Dashed-line authoring beyond the approved Level 1 line contract.
- Pattern images, gradients, arbitrary textures, polygon repair, or cartographic line casing beyond
  what composition already provides.

## Acceptance criteria

- Line symbols support independently optional start and end markers, including the built-in arrow,
  using the first and last non-zero segment tangents for orientation.
- Repeated coordinates and zero-length lines do not produce non-finite transforms; endpoints are
  skipped according to a documented deterministic rule when no tangent exists.
- Endpoint size, offset, opacity, and screen/map-relative rotation compose with G2-003 semantics.
- Basic forward-diagonal, backward-diagonal, and crossed hatch fills expose finite positive spacing
  and stroke width, render within the visible clip, and preserve polygon holes.
- Hatch generation is bounded by the clipped screen extent and a documented maximum segment count;
  over-limit work yields a stable diagnostic rather than unbounded allocation or looping.
- Arrowheads and hatches render correctly under zoom, rotation, multipart-ready path subparts, and
  translucent composite symbols.

## Required tests

- Core tests for endpoint tangents, repeated points, short lines, hatch bounds, and work limits.
- API validation and defensive-copy tests for endpoint and hatch values.
- Offscreen AWT tests for start/end orientation, arrowheads, each hatch pattern, clipping, holes,
  opacity, and zoom behavior.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep endpoint and hatch calculations JDK-only and allocation-bounded. Invalid polygon topology is not
repaired here; render only geometry accepted by the public model.

Completed on 2026-07-13. Immutable solid-line, solid-fill, and bounded diagonal-hatch values now
complete the Level 1 geometry roles. Core supplies outward endpoint bearings, bearing-overridden
marker transforms, and packed clipped hatch segments with preflight limits. AWT renders line
composites, optional oriented endpoints, even-odd fills, unclipped outlines, and all four independent
hatch rotation/spacing policies. The basic viewer now exercises the symbol path while compatibility
tests retain the deprecated style path.
