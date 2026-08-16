# G19-123 — Complete current point-symbol rendering

Status: Proposed
Depends on: G19-002, G19-122
Gate: G19
Type: HITL

## Goal

Render every point symbol in the approved 2525E C1 and APP-06E inventories with complete frame, identity, status, icon, and palette behavior.

## Context

Current project-authored paths cover only 15 Land/Activities entity examples and a narrow frame/status/palette profile.

## Scope

- Generate/implement deterministic toolkit-neutral vector layers for every current point-rendered symbol set/entity/type/subtype.
- Complete standard identity frames, framed/unframed families, status/presence, anticipated/planned rendering, alternate frame shapes,
  fills/knockouts, installation/inside/top icons, context differences, and applicable edition variants.
- Implement approved standard palettes, monochrome/unframed modes, line/fill/alpha/background behavior, explicit size/stroke scaling,
  map/screen units, rotation, anchoring, bounds, and deterministic missing-path failure.
- Preflight path commands/layers/resources/primitives/owned bytes and generate immutable data reproducibly with provenance.
- Define pixel/vector tolerances and compare AWT/Vaadin/SVG from the same authoritative symbol tree.

## Out of scope

- Graphic/text amplifiers assigned to G19-124, tactical graphics, copied restricted artwork, and visually similar fallback.

## Acceptance criteria

- Every renderable current point catalog entry resolves to the correct complete base symbol for all applicable identities/statuses.
- Output matches reviewed official/independent references within per-family vector/pixel/color tolerances.
- Missing/conflicting/over-budget definitions fail before partial rendering and are absent from release inventory.

## Required tests

- Exhaustive entry/identity/status/frame/palette/size/rotation inventory plus vector and raster goldens.
- Layer/order/knockout/bounds, scale/zoom/wrap, generator provenance, limit, native, and cross-renderer tests.

## Validation

Run the module check and rendering/corpus/native lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves provenance, exhaustive coverage reports, reference set/license, and visual tolerances.
