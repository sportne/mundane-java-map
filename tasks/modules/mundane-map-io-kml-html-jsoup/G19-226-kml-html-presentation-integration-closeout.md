# G19-226 — KML HTML presentation integration and closeout

Status: Proposed
Depends on: G19-225
Gate: G19
Type: HITL

## Goal

Integrate sanitized KML description/balloon HTML with AWT and Vaadin, catalog resources, accessibility, canonical
writing, and independent interoperability while preserving safe core fallback.

## Context

Sanitization alone does not prove usable presentation. Both renderers need deterministic bounded layout and
interaction without turning the adapter into a browser or permitting unsafe links/resources.

## Scope

- Define the renderer-neutral sanitized block/inline/table/image/link model and deterministic AWT/Vaadin layout,
  fonts, wrapping, sizing, scrolling, focus, link activation callback, selection, and accessibility behavior.
- Integrate BalloonStyle substitutions/fields, description precedence, plain-text core fallback, catalog/KMZ images,
  privacy/no-value-leak rules, and canonical KML/KMZ writer markup policy.
- Bound layout boxes/lines/glyphs/tables/images/pixels/links/substitutions, viewport size, interaction callbacks,
  retained state, and relayout work; make detach/close/resource cleanup exception-safe.
- Add cross-renderer visual/accessibility tests, independent KML markup fixtures, browser hostile-content tests,
  native/publication/offline/consumer evidence, public docs, examples, diagnostics, and capability closeout.

## Out of scope

- Full HTML/CSS conformance, scripting, forms, embedded browsing, automatic navigation, media, and ambient resources.

## Acceptance criteria

- Approved sanitized markup displays equivalently and accessibly in AWT/Vaadin within declared tolerances.
- Unsafe content/resources/navigation cannot execute; absent adapter always yields safe escaped plain text.
- Writer/read-back and independent KML applications preserve the approved markup semantics.

## Required tests

- Cross-renderer text/list/table/image/link/balloon/substitution/accessibility goldens and browser interaction tests.
- Hostile content/navigation, missing resources, layout/pixel/work limits, lifecycle cleanup, native/publication/offline.

## Validation

Run adapter/KML/AWT/Vaadin checks, rendering/accessibility/browser/native/publication/offline lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves visual/accessibility tolerances, browser security evidence, independent
interoperability, optional-adapter wording, and exact public claims.
