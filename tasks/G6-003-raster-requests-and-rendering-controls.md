# G6-003 — Raster requests and rendering controls

Status: Proposed
Depends on: G6-002, G2-006
Gate: G6
Type: AFK

## Goal

Serve bounded raster windows and render affine PNG/JPEG imagery with explicit opacity and nearest or
bilinear interpolation.

## Context

G4-004 defines window requests, lifecycle, cancellation, and renderer integration. G6-002 supplies
pixel-to-map affine placement, including rotation and shear.

## Scope

- Source-compatible `RasterRequest` interpolation extension in `mundane-map-api`, reusing the
  G2-owned enum, plus exact shared resampling math in `mundane-map-core`
- Encoded region/subsampling hints and request integration across `mundane-map-io-image`/AWT
- Affine viewport output planning, deterministic AWT decode/resample/draw, and immutable view-owned
  raster render options/opacity
- Raster-viewer controls and focused fixtures

## Out of scope

- Decode/resample caching, advanced filters, pyramids, remote tiles, and color management profiles
- Bicubic interpolation or general reprojection/warping
- Filter taps outside the strict requested window, upsample enhancement, or codec-work guarantees

## Acceptance criteria

- A request specifies a strict source window, output dimensions, and one supported interpolation
  mode with immutable validated values; the old constructor defaults to nearest and cancellation
  remains the per-invocation method token.
- Existing decoders default to nearest-only capability; unsupported bilinear fails with a stable
  decoder diagnostic, while the explicit Level 1 AWT decoder declares and implements both modes.
- Nearest retains G4's exact pixel-center/tie formula. Bilinear uses checked integer axis weights and
  premultiplied-alpha RGBA accumulation with half-up rounding, transparent-black zero alpha, window-
  local edge clamp, and no platform floating behavior.
- Direct requests retain G4's strict out-of-bounds rejection. MapView computes contained visible
  windows/output dimensions and caps source/output pixels, decoded bytes, and resampling work before
  allocation.
- PNG/JPEG always request the strict source region. Nearest uses integer subsampling only per exactly
  divisible axis with `factor=source/output`, `offset=floor(factor/2)`; bilinear never subsamples, and
  no public claim says the opaque codec avoided full decode.
- Nearest and bilinear modes are deterministic for edge pixels, transparent/opaque input, and
  partially visible windows.
- View-owned immutable render options hold interpolation/opacity. Opacity is finite in `[0,1]`,
  rejects rather than clamps, canonicalizes negative zero, composes once, and updates without
  replacing/closing the binding/source; zero skips read/draw and preserves prior source report state.
- Rotated/sheared imagery uses the G6-002 strict window and screen-basis downsample plan; final Java2D
  placement is nearest to avoid double filtering. Region hints reduce requested data but do not prove
  compressed decode work avoided.
- Screen-density planning caps a basis length at/above one directly to source size before multiply,
  so finite zoom-in cannot overflow an irrelevant uncapped output calculation.
- Cancellation is observed before decode, between bounded stages, and before publication; partial
  results are not cached or rendered.
- Viewer controls expose interpolation and opacity without importing format logic into AWT.
- New public APIs have complete Javadocs and compatibility/value tests.

## Required tests

- API request compatibility/validation tests; core exact nearest/bilinear axis, premultiplied-alpha,
  edge/window-locality, overflow, cancellation, and synthetic-source parity tests.
- Controlled ImageIO tests for exact region/subsampling plans, no bilinear subsampling, shape mismatch,
  inherited nearest-only decoder rejection, explicit dual-mode capability, opaque-stage accounting,
  and PNG/JPEG equivalence with extracted-matrix oracles.
- Integration tests for opacity, viewport clipping, subsampling, rotated/sheared placement, and
  cancellation at each stage.
- Offscreen rendering assertions based on bounds and tolerant sampled colors rather than full-image
  pixel identity.
- Render-options/viewer tests for opacity zero/half/full, single SrcOver composition, repaint without
  source replacement, positive/negative-zero equality, huge zoom-in capping, status, and close.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check :examples:raster-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep encoded-format decisions in `mundane-map-io-image` and all Java2D/ImageIO operations in AWT.
Use checked arithmetic for every window/weight/byte calculation. Do not run native, publication,
corpus, or performance lanes.
