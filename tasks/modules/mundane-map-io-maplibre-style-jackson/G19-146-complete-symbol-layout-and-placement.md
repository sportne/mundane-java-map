# G19-146 — Complete symbol layout and placement

Status: Proposed
Depends on: G19-013, G19-141, G19-144, G19-145
Gate: G19
Type: HITL

## Goal

Implement the complete pinned symbol property, shaping, placement, collision, and ordering model.

## Context

Current symbol support is a point-only catalog-icon/text subset. MapLibre symbol layers combine sprites,
formatted multilingual text, line/polygon placement, variable anchors, collision and data-driven ordering.

## Scope

- Generate the exact 59-property v26.2.1 symbol layout/paint matrix and enforce all defaults/dependencies.
- Implement formatted text/images, fonts/glyphs, language/script/direction, bidi, shaping, wrapping, writing modes,
  spacing/justification, transforms, letter/line spacing, halos, padding, optionality, and offsets.
- Implement point/line/line-center placement, rotation/pitch anchors, variable anchors, repeat distance,
  icon-text fit, overlap/ignore/avoid edges, collision groups, z/sort order, and transition behavior.
- Produce one bounded neutral shaped-placement/collision result consumed by AWT, Vaadin, hit testing, and SVG.
- Bound candidates/glyphs/spans/lines/anchors/collision boxes/retries/evaluations/cache/owned bytes and work.

## Out of scope

- Ambient font fallback, GPU glyph shader identity, 3D symbol elevation, and executable rich text.

## Acceptance criteria

- Every pinned symbol property has tested supported or explicit non-renderable behavior.
- Complex multilingual/icon line-placement fixtures agree with reference placement within documented tolerances.
- Placement is deterministic, bounded, generation-safe, and consistent across renderer/export/hit paths.

## Required tests

- Generated property matrix; script/bidi/font/glyph/formatted/icon/line/polygon/variable-anchor/collision corpus.
- Missing resources, hostile text/fonts, dense labels, wrap/tile seams, transitions, limits, and renderer parity.

## Validation

Run module/font/shaping/rendering/browser lanes, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve shaping engines/fonts, visual tolerances, collision evidence, and accessibility behavior.
