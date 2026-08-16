# G19-114 — KML overlays, models, and KMZ resources

Status: Proposed
Depends on: G19-012, G19-044, G19-112, G19-113
Gate: G19
Type: HITL

## Goal

Implement complete overlay/model interchange, approved 2D representations, and secure bounded KMZ resources.

## Context

GroundOverlay, ScreenOverlay, PhotoOverlay, Model, and KMZ are rejected. Full 3D COLLADA and panorama engines
are deliberately outside the approved 2D toolkit profile.

## Scope

- Implement GroundOverlay color/draw order/icon/altitude/LatLonBox/LatLonQuad placement and raster reprojection.
- Implement ScreenOverlay units/anchors/size/rotation/order/accessibility/interaction as viewport-fixed portrayal.
- Preserve PhotoOverlay view volume/image pyramid/point/shape/rotation/resource data and render a geographic
  footprint/location plus optional authorized thumbnail; do not implement panorama viewing.
- Preserve Model location/orientation/scale/link/resource map/aliases and package assets while rendering a
  deterministic 2D anchor/footprint; do not parse/render COLLADA.
- Add normalized path-confined KMZ reading/catalogs with `doc.kml`, deterministic entry selection, media validation,
  duplicate/alias/traversal defense, decompression ratios, entry/count/size/nesting/owned-byte limits, and cleanup.

## Out of scope

- General 3D/COLLADA engines, panorama viewers, archive extraction APIs, and ambient resource loading.

## Acceptance criteria

- All overlay/model fields/resources round-trip while declared 2D representations render consistently.
- KMZ cannot traverse, alias, duplicate-normalize, recursively expand, or exceed prospective archive/resource limits.
- Resource decode/placement failure is atomic and releases staged archive/decoder state.

## Required tests

- Ground/screen/photo overlay, model/resource-map, KMZ path/media/order/dedup matrices and independent fixtures.
- 2D rendering goldens, ZIP slip/bomb/duplicate/alias/nesting/truncation, raster/resource limits, cancellation/cleanup.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, KML/rendering corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the 2D Model/PhotoOverlay representations and independent visuals.
