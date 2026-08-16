# G19-082 — SVG CSS cascade and presentation properties

Status: Proposed
Depends on: G19-080, G19-081
Gate: G19
Type: HITL

## Goal

Resolve the approved SVG presentation-property surface through presentation attributes, inline style,
embedded/catalog stylesheets, selectors, cascade, inheritance, and deterministic CSS value parsing.

## Context

Current style handling reads a few direct attributes and manual inheritance. Real SVG symbols rely on
stylesheets, specificity, `!important`, `currentColor`, units, and inherited group presentation.

## Scope

- Pin exact editions/subsets of CSS Syntax, Cascade, Selectors, Color, Values/Units, Fonts, Writing
  Modes, and Compositing used by the static SVG profile.
- Parse `style` attributes/elements and catalog stylesheets with comments/escapes/tokenization,
  supported selectors, specificity, source order, origins, `!important`, inheritance, initial/unset/
  inherit, and approved custom-property substitution.
- Implement the complete approved SVG presentation-property/value table, `currentColor`, opacity,
  display/visibility, paint order, stroke/dash/join/cap/miter, and geometry-property interaction.
- Reject or ignore unknown properties/declarations exactly per pinned CSS/SVG rules without accepting
  unsafe URL or active behavior.
- Bound stylesheet bytes, tokens, rules, selectors, specificity work, matches, declarations, custom-
  property expansion, computed values, and aggregate element-style work.

## Out of scope

- HTML layout CSS, media/network imports, transitions, animations, and arbitrary browser selectors.

## Acceptance criteria

- Equivalent attribute/inline/stylesheet declarations produce the specified computed values and
  independent-renderer visual results.
- Adversarial selectors/cascade/custom properties remain prospectively bounded.
- No URL-bearing property escapes the closed catalog or enables active content.

## Required tests

- Complete property/value/default/inheritance matrix; selector/specificity/order/important cases;
  comments/escapes/invalid recovery/custom-property cycles; currentColor/units/dashes/display; hostile
  stylesheet size/match/expansion, cancellation, and rendering comparisons.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, CSS/SVG rendering corpus lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact CSS module editions, selector/property/value matrix,
error-recovery policy, and external fixtures before completion.
