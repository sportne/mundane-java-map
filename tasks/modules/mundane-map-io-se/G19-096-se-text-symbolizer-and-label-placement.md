# G19-096 — SE TextSymbolizer and label placement

Status: Proposed
Depends on: G19-013, G19-085, G19-094
Gate: G19
Type: HITL

## Goal

Implement deterministic SE 1.1 TextSymbolizer compilation and portrayal using explicit registered fonts
and the neutral cartographic label engine.

## Context

TextSymbolizer is terminally unsupported, so standard SE labels, placement, halo, and font behavior are absent.

## Scope

- Implement label expressions, font family/style/weight/size and ordered fallback using registered font resources.
- Add point placement anchor/displacement/rotation and line placement perpendicular offset, alignment,
  generalization, gap, repeated-label, and initial-gap behavior covered by SE 1.1.
- Implement halo radius/fill, glyph fill/opacity, UOM conversion, Unicode/bidi/language handling, and
  deterministic shaping/metrics without system-font discovery.
- Map applicable placement/vendor-independent options to the neutral label contract; reject vendor options.
- Bound text length/code points, runs/glyphs, fonts/fallbacks, candidates, shaping, path placement,
  collision work, resources, and produced labels prospectively.

## Out of scope

- System fonts, remote fonts, arbitrary CSS text layout, vendor labeling options, and text animation.

## Acceptance criteria

- Approved TextSymbolizers render with deterministic glyph selection, placement, halo, and collision behavior.
- Missing glyph/font, invalid placement, and limit failures occur before partial label publication.
- AWT, Vaadin, SVG, and SE writer/read-back results agree within declared text/placement tolerances.

## Required tests

- Font/fallback/style/size/point/line/anchor/displacement/rotation/halo/UOM/Unicode/bidi matrix.
- Registered-font goldens, world-wrap/path cases, missing glyphs, hostile strings/candidates, and parity evidence.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, label/rendering/corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact SE text-placement profile, fonts, tolerances, and visuals.
