# G19-128 — Military symbology renderer, export, and application integration

Status: Proposed
Depends on: G19-127
Gate: G19
Type: AFK

## Goal

Integrate the complete point/tactical profile with portrayal rules, catalogs, AWT, Vaadin, SVG export, workspace state,
and bounded application workflows without duplicating standard logic.

## Context

The current module resolves a small point profile into neutral symbols. Complete catalogs and tactical graphics need
stable feature attributes/rules, viewport/query behavior, editing persistence, legends, export, and client parity.

## Scope

- Define standard feature attribute/binding/resolver contracts for edition, SIDC, text/graphic modifiers, control points,
  parameters, palette/options, translation policy, and stable source/display identity.
- Integrate catalog browsing/legends, point/tactical portrayal, scale/units, labels, selection/hover/hit, wrap, editing,
  workspace serialization/migration, and source-query cancellation with no adapter-specific bypass.
- Use one authoritative toolkit-neutral output in AWT/Vaadin and deterministic SVG; provide bounded coordinate-free
  symbol rendering and in-memory AWT raster results without adding PNG encoding.
- Add scene/protocol/profile accounting for catalogs, paths, tactical geometry, text, resources, primitives, client bytes,
  hit/edit work, and lifecycle cleanup.
- Add examples demonstrating current and translated point/tactical workflows with exact support diagnostics.

## Out of scope

- C2 message formats, operational tracking/inference, sprite/PNG file encoders, and 3D battlefield display.

## Acceptance criteria

- Identical inputs select and render the same standard semantics across AWT, Vaadin, SVG, workspace, and legends.
- Complete-profile scenes/edits remain bounded, atomic, cancellation-aware, and lifecycle-clean.
- Examples expose edition/support/translation losses rather than silently degrading symbols.

## Required tests

- Rule/binding/catalog/legend/workspace/export/AWT/Vaadin/browser parity for point and every tactical family.
- Large-scene/protocol/work limits, wrap/edit/lifecycle/failure, native/publication/offline, and example smoke tests.

## Validation

Run module plus affected integration/rendering/browser/native/publication/offline lanes, qualityGate, and `git diff --check`.

## Notes

None.
