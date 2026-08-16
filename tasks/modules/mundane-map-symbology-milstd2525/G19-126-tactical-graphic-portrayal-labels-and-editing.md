# G19-126 — Tactical-graphic portrayal, labels, and editing

Status: Proposed
Depends on: G19-013, G19-125
Gate: G19
Type: HITL

## Goal

Complete tactical-graphic decorations, fills, labels, hit testing, and constrained editing across AWT and Vaadin.

## Context

Base geometry alone is not recognizable military symbology; standard arrows, teeth, ticks, sectors, fills, integral labels,
repeated text, and control-point editing behavior are essential.

## Scope

- Render all current per-family lines/fills/patterns/arrows/teeth/ticks/arcs/fans/boundary/echelon/identity decorations,
  integral marks, scale-dependent details, palette/status/identity behavior, and standard draw order.
- Implement standard tactical labels/modifiers, formatting, repetitions, orientation, offsets/leaders, collision/fallback, and bounds.
- Add exact hit testing/selection and immutable editor contracts for handles, insert/move/delete, typed parameters, previews,
  commit/cancel/undo, snapping, stale source/viewport/edition generation rejection, and accessibility/keyboard paths.
- Preserve dateline/world-copy identity, clipping, scale/zoom behavior, export semantics, and AWT/Vaadin/SVG parity.
- Bound generated decoration segments, fills, labels/candidates/glyphs, hit/edit work, previews/primitives/pixels/owned bytes.

## Out of scope

- Automated tactical planning, unconstrained CAD editing, vendor handles, and approximate unsupported graphics.

## Acceptance criteria

- Every current tactical graphic renders its complete standard portrayal and exposes only valid edit operations.
- Cross-renderer/export output, hit results, and committed control points agree within declared tolerances.
- Failure/cancellation/stale generations never partially update the graphic, source, history, or overlay.

## Required tests

- Exhaustive decoration/fill/label/applicability inventory, reference goldens, hit/select/edit/undo/keyboard/accessibility matrix.
- Wrap/clip/scale extremes, hostile text/candidates/segments, stale/failure lifecycle, rendering/browser/performance tests.

## Validation

Run module, rendering/browser/edit/performance corpus lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves reference visuals, edit semantics, accessibility, and tolerances.
