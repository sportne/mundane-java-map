# AWT and Swing capability profile

This module owns Java2D rendering and Swing interaction for the neutral API. It is the authoritative
renderer for the current built-in profile, but Java2D implementation details do not redefine neutral
portrayal semantics or format standards.

## Capability matrix

| Area | Current profile | G19 target | Explicit boundary |
| --- | --- | --- | --- |
| Geometry rendering | Complete current non-empty XY built-in families | Empty/dimensional/collection inputs through explicit deterministic XY presentation policy | No 3D terrain, perspective, volume, globe, or silent ordinate loss |
| Vector portrayal | Current built-in marker, line, fill, composite and endpoint profile | Every G19 neutral cap/join/dash/offset, graphic paint, mask, color-map, advanced text and composite primitive | No format-specific renderer or undocumented Java2D approximation |
| Raster/elevation | Bounded raster/elevation rendering and colorization | Consume core raster warps, masks, bands and color maps with atomic preflight | Codec support stays in explicit decoders/adapters; no source-to-image ownership shortcut |
| Labels | Point-label layout/paint | Renderer-neutral accepted line/polygon layout, registered deterministic fonts/shaping policy, wrap/collision parity | No reliance on arbitrary host fonts for canonical output |
| Hit/interaction parity | Swing tools, selection, editing, measurement and visible-paint hit testing for current symbols | Visible-paint parity for all accepted new geometry/portrayal constructs within frozen tolerances | Invisible/unsupported paint never becomes interactable by accident |
| Accessibility | Keyboard routing and application-provided Swing labels | Reviewed `AccessibleContext` role/name/description/state/action/events plus keyboard-only inspect/select/tool/cancel workflows | Embedding applications remain responsible for their surrounding labels/navigation |
| Printing | Screen/offscreen rendering and image/vector exports | Deterministic bounded `Printable`/`Pageable` scale, margins, DPI, tiling, cancellation and pagination | No printer-driver/color-device certification or claim that screen and paper pixels are identical |
| Provider boundary | Explicit instance registries; bounded JDK PNG/JPEG Image I/O exception | Preserve explicit catalogs and exact duplicate/unsupported diagnostics for expanded portrayal/resources | No classpath scanning, application-provider discovery, or format-library dependency in renderer core |

## Rendering and lifecycle contract

- New neutral constructs have one documented outcome: exact supported mapping, named bounded
  approximation, or atomic preflight rejection before visible mutation.
- Golden images and operation traces use declared tolerances, registered fonts/color assumptions,
  deterministic ordering, and cross-renderer fixtures rather than treating platform antialiasing as
  a portable standard.
- Swing mutation remains on the EDT; owned/borrowed bindings, cooperative cancellation, replacement,
  close, print, and failure paths preserve exact resource ownership and listener cleanup.
- The module may depend on `java.desktop` but format adapters remain separate and no format-specific
  AST or external codec type enters its public contracts.

## Completion rule

G19-020 and G19-021 complete this matrix only after every completed neutral geometry/portrayal feature
has render/hit/export evidence and the supported Swing accessibility and print matrices have automated
plus human platform evidence with accurate exclusions.
