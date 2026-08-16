# G19-184 — Renderer-neutral G19 scene and Canvas parity

Status: Proposed
Depends on: G19-002, G19-011, G19-012, G19-013, G19-014, G19-020, G19-089, G19-096, G19-128, G19-146, G19-147
Gate: G19
Type: AFK

## Goal

Carry every applicable completed G19 neutral construct through one renderer-neutral prepared scene and the required Canvas 2D backend.

## Context

The G18 private protocol is closed over its implemented slice. Directly adding Canvas operations for every G19 feature would
couple semantics to one backend and make the approved WebGPU path inconsistent.

## Scope

- Inventory every completed G19 geometry, dimensional value, portrayal, label/glyph, raster/elevation, tile/wrap, military
  symbol, hit/edit and interaction construct as rendered, preserved server-only or rejected before publication.
- Build immutable bounded backend-neutral display/prepared-scene values with stable logical/visual identity, order, clipping,
  blending, resources, hit/edit footprints and public diagnostic translation; keep all browser values private.
- Consume registered neutral shaped text/glyph outlines or atlases with original Unicode semantics; prohibit authoritative
  browser `fillText`, ambient fonts and browser-local collision decisions.
- Implement complete Canvas 2D painting, hit/selection/edit/export parity with AWT/SVG using declared numeric/pixel/operation
  tolerances, atomic preflight/commit and exact resource ownership.
- Remain 2D: support elevation-derived 2D effects but reject globe, perspective terrain, extrusion, models and depth picking.

## Out of scope

- A public display-list/protocol SPI, caller JavaScript/shaders, 3D approximation, browser source adapters or system fonts.

## Acceptance criteria

- The generated G19 inventory has one tested disposition for every applicable construct and no silent omission/approximation.
- Canvas, AWT and SVG agree semantically and meet approved visual/hit/edit/text tolerances on shared fixtures.
- Invalid/over-budget candidates leave the previous visible, interaction and resource state unchanged.

## Required tests

- Generated construct matrix; cross-renderer geometry/paint/text/raster/elevation/wrap/hit/edit fixtures and real-browser images.
- Missing glyph/resource, hostile numeric/path/raster/text/identity/order inputs, 2D exclusions, limits, failure atomicity and
  replacement/detach/session cleanup tests.

## Validation

Run API/core/AWT/SVG/Vaadin renderer and browser parity lanes, then qualityGate and `git diff --check`.

## Notes

None.
