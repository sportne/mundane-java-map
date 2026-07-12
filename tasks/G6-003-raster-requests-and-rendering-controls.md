# G6-003 — Raster requests and rendering controls

Status: Proposed
Depends on: G6-002
Gate: G6
Type: AFK

## Goal

Serve bounded raster windows and render affine PNG/JPEG imagery with explicit opacity and nearest or
bilinear interpolation.

## Context

G4-004 defines window requests, lifecycle, cancellation, and renderer integration. G6-002 supplies
pixel-to-map affine placement, including rotation and shear.

## Scope

- Window/subsampling request implementation in `mundane-map-io-image`
- AWT decode/resample/render path and rendering controls
- Raster-viewer controls and focused fixtures

## Out of scope

- Decode/resample caching, advanced filters, pyramids, remote tiles, and color management profiles
- Bicubic interpolation or general reprojection/warping

## Acceptance criteria

- A request specifies source window, output dimensions, cancellation, opacity, and one of the
  supported interpolation modes with immutable validated values.
- Requests clip or reject out-of-bounds windows according to the G4 contract and cap source/output
  pixels, decoded bytes, and resampling work before allocation.
- PNG/JPEG decoding uses source-region/subsampling facilities when available and never claims a
  window optimization that was not applied.
- Nearest and bilinear modes are deterministic for edge pixels, transparent/opaque input, and
  partially visible windows.
- Opacity is clamped or rejected by a documented API rule and composed once without mutating source
  pixels.
- Rotated and sheared affine imagery is placed correctly; viewport clipping prevents unnecessary
  decode/render work.
- Cancellation is observed before decode, between bounded stages, and before publication; partial
  results are not cached or rendered.
- Viewer controls expose interpolation and opacity without importing format logic into AWT.

## Required tests

- Request validation and clipping tests for zero/negative/overflow dimensions and boundary windows.
- Small-matrix nearest/bilinear tests with exact expected sample values.
- Integration tests for opacity, viewport clipping, subsampling, rotated/sheared placement, and
  cancellation at each stage.
- Offscreen rendering assertions based on bounds and tolerant sampled colors rather than full-image
  pixel identity.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :examples:raster-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep encoded-format decisions in `mundane-map-io-image` and all Java2D/ImageIO operations in AWT.
Use checked arithmetic for every window-to-byte calculation.
