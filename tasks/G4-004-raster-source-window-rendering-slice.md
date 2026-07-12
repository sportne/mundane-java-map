# G4-004 — Raster Source Window Rendering Slice

Status: Proposed
Depends on: G4-003
Gate: G4
Type: AFK

## Goal

Render a synthetic toolkit-neutral raster through the approved window-request, cancellation,
diagnostic, and lifecycle contracts, proving the boundary before adding encoded image formats.

## Context

No raster model or layer exists today. G4-001 defines `RasterSource`, metadata, raster requests, pixel
ownership, and cancellation; G4-002 defines recognized CRS behavior; G4-003 delivers the shared
diagnostic, cancellation, limit, binding, report, and MapView lifecycle foundations. Java2D conversion
belongs in `mundane-map-awt`, while the source and returned pixel data must remain usable by JDK-only
non-AWT modules and Native Image.

## Scope

- Approved raster source, metadata, window, pixel-buffer, and layer contracts in
  `mundane-map-api`.
- A deterministic synthetic raster source and request validation in `mundane-map-core`.
- Explicit packed-pixel conversion and raster-layer painting in `mundane-map-awt`.
- API/core/AWT and architecture tests plus public Javadocs.

## Out of scope

- PNG/JPEG decoding, world files, affine rotation/shear, interpolation controls, caching, remote
  access, and GeoTIFF.
- Exposing `BufferedImage`, `Raster`, `ColorModel`, or other AWT types outside the AWT module.

## Acceptance criteria

- Raster metadata and returned windows are immutable, validate positive dimensions and bounded pixel
  counts before allocation, and defensively own packed primitive pixel storage.
- Requests specify source window/bounds and output dimensions according to G4-001, clip or reject
  out-of-range windows by one documented rule, and report invalid/over-limit requests structurally.
- The synthetic source produces a deterministic color grid that allows source-window and output-size
  behavior to be asserted without a codec or filesystem.
- A raster layer requests only the visible recognized-CRS extent, converts packed pixels inside AWT,
  and paints at the correct projected screen bounds.
- Cancellation before and during generation stops work predictably; failed or cancelled requests do
  not publish partial pixels.
- Request/window resources and owned sources close exactly once on replacement and view disposal,
  while caller-owned source lifecycle follows the G4-001 decision.
- Architecture tests prove API/core remain free of `java.desktop` and no implicit decoder discovery
  was introduced.

## Required tests

- API tests for metadata/window immutability, limits, copies, and invalid requests.
- Core tests for deterministic pixels, clipping policy, cancellation, diagnostics, and lifecycle.
- AWT offscreen tests for geographic/projected placement, viewport window selection, source
  replacement, empty intersection, and cleanup.
- Architecture tests for toolkit confinement and explicit conversion registration.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test :modules:mundane-map-architecture-tests:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use a synchronous source for this first slice and bound every output allocation. Decode/resample
caches and affine world-file behavior belong to G6, after encoded PNG/JPEG behavior exists.
