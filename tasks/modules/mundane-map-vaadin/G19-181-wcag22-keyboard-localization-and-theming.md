# G19-181 — WCAG 2.2, keyboard, localization, and theming

Status: Proposed
Depends on: G19-180
Gate: G19
Type: HITL

## Goal

Implement the component-applicable WCAG 2.2 A/AA, keyboard, localized-message and host-theming contract.

## Context

The map has basic accessible names/status and keyboard workflows, but lacks a criterion-by-criterion contract, complete
spatial alternatives, localized component text and a stable contrast-safe host theme surface.

## Scope

- Publish an applicable/not-applicable/host-owned WCAG 2.2 A/AA matrix with implementation and evidence for every row.
- Make navigation, inspection, selection, measurement and editing discoverable and fully keyboard operable with logical
  focus order, visible focus, target sizing, non-color cues, errors/instructions and bounded live-region behavior.
- Provide non-canvas semantic summaries/collections/actions for essential map state without mirroring an unbounded scene DOM.
- Add a caller Java message provider, default English catalog, stable typed keys/parameters, explicit locale, bidi/isolation,
  deterministic number/unit formatting and atomic live locale changes; retain locale-neutral diagnostic codes.
- Add closed Java theme variants and CSS tokens/parts for focus/loading/error/empty/accessibility chrome, forced colors,
  contrast and reduced motion; prohibit CSS portrayal/private-renderer overrides.

## Out of scope

- Certifying an embedding page, exposing map portrayal through CSS, ambient locale/resource bundles, or unbounded DOM twins.

## Acceptance criteria

- Every applicable component criterion has automated and/or named human evidence and an explicit host responsibility.
- Essential workflows remain usable at keyboard-only, browser zoom/reflow, high contrast/forced colors and reduced motion.
- Locale/theme changes are bounded and preserve authoritative scene, focus, selection, tool and lifecycle state.

## Required tests

- Keyboard workflow, focus, zoom/reflow, target-size, contrast/forced-color, reduced-motion, semantic-tree and axe-equivalent
  automation; hostile announcement/message/theme/locale inputs and rapid live-change tests.
- Catalog completeness, missing/failing provider, plural/number/unit/bidi/RTL, stable-code, CSS value and host integration tests.

## Validation

Run Vaadin/component/example accessibility and localization lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve the WCAG applicability matrix, host boundary, default copy, theme tokens and human evidence plan.
