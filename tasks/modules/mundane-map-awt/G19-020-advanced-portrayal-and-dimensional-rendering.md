# G19-020 — Advanced portrayal and dimensional rendering

Status: Proposed
Depends on: G19-001, G19-002, G19-013
Gate: G19
Type: AFK

## Goal

Render the complete G19 neutral geometry and portrayal model through deterministic Java2D behavior.

## Context

The AWT module is authoritative for the existing profile but has no contract for new dimensional,
collection, advanced stroke/fill/text, or raster-band constructs.

## Scope

- Add explicit XY projection behavior for empty/Z/M/collection geometry.
- Implement every accepted advanced portrayal primitive with frozen Java2D mappings.
- Add line/polygon labels, graphics, masks, color maps, and raster-warp consumption.
- Preserve atomic preflight, resource bounds, hit-test parity, and stable diagnostics.
- Update public Javadocs and rendering-equivalence documentation.

## Out of scope

- Treating Java2D implementation accidents as portable portrayal semantics.

## Acceptance criteria

- Every G19 neutral symbol has an accepted render, documented approximation, or early rejection.
- Hit testing and interaction overlays match visible paint within declared tolerances.
- No new format dependency enters the AWT renderer.

## Required tests

- Golden images/operation traces for every new primitive and geometry family.
- Empty, huge, transparent, wrap, cancellation, and resource-limit regressions.
- Cross-renderer parity fixtures with Vaadin.

## Validation

Run `./gradlew :modules:mundane-map-awt:check --console=plain`, rendering-regression tests, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
