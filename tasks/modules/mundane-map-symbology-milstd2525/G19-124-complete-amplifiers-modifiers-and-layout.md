# G19-124 — Complete amplifiers, modifiers, and layout

Status: Proposed
Depends on: G19-013, G19-123
Gate: G19
Type: HITL

## Goal

Implement every applicable current-edition graphic amplifier and text modifier with deterministic standard layout.

## Context

The module has seven sector-modifier examples and no complete echelon, mobility, operational, movement, country,
order-of-battle, engagement, sonar, or surrounding text-modifier layout model.

## Scope

- Model/validate complete 2525E C1/APP-06E graphic amplifiers and text modifiers with catalog applicability and ordering.
- Render echelon, HQ/task-force/feint-dummy combinations, mobility/auxiliary equipment, direction of movement,
  operational condition, engagement bars, sonar confidence, and all other approved current graphic amplifiers.
- Format/place complete applicable text fields, country/entity/order-of-battle, quantities, dates/times, altitude/depth,
  location, speed/direction, unique designation, equipment/type, multi-line zones, fallback and collision rules.
- Use explicit registered fonts/metrics, Unicode/bidi policy, size/palette/frame-dependent zones, and deterministic bounds.
- Bound values/code points/lines/glyphs/candidates/collisions/paths/primitives/work and fail invalid combinations before portrayal.

## Out of scope

- Inferring modifiers from operational data, system fonts, arbitrary free-form labels, and tactical-graphic labels.

## Acceptance criteria

- Every catalog-applicable amplifier/modifier combination validates and lays out according to the selected current edition.
- AWT/Vaadin/SVG placement, bounds, collision, and text output agree within declared tolerances.
- Inapplicable/hostile/over-budget modifiers fail or omit only under an explicit standard rule, never silently.

## Required tests

- Exhaustive applicability and amplifier/text-zone combinations, formatting, Unicode/bidi, palette/size/frame matrices.
- Reference goldens, collisions/extreme text, missing glyph/font, work/primitive limits, and cross-renderer tests.

## Validation

Run the module check and label/rendering corpus lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves fonts, formatting/placement policy, reference evidence, and visual tolerances.
