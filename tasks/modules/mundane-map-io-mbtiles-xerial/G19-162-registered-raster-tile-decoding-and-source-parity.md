# G19-162 — Registered raster tile decoding and source parity

Status: Proposed
Depends on: G19-044, G19-161, G19-228
Gate: G19
Type: AFK

## Goal

Complete raster MBTiles reading through explicit PNG/JPEG and optional WebP decoder capabilities.

## Context

The current raster source covers a narrow PNG/JPEG profile. MBTiles completeness requires standard
WebP recognition while preserving toolkit-neutral and optional-dependency boundaries.

## Scope

- Decode registered PNG/JPEG and optional static WebP tiles only after declaration, magic, dimensions,
  color/alpha, decoded-pixel, and aggregate source limits pass.
- Support sparse/empty/partial zooms, windows, TMS/XYZ conversion, bounds clipping, wrap, resampling,
  no-data/alpha/color policy, caches, cancellation, concurrent requests, and atomic publication.
- Keep the WebP AWT adapter test/runtime optional; MBTiles public and production main code depends only
  on a neutral explicit decoder contract and retains stable unsupported-media behavior when absent.
- Reconcile direct image, HTTP, GeoPackage, MapLibre, AWT, and Vaadin raster semantics and diagnostics.

## Out of scope

- Image encoding/transcoding, decoder discovery, non-global-mercator MBTiles, or treating arbitrary
  IETF image media as supported without a registered decoder.

## Acceptance criteria

- Independent PNG/JPEG/WebP MBTiles render with equivalent placement/color/alpha and bounded caches.
- Missing optional decoders and all declaration/payload/limit failures are stable and failure-atomic.
- No AWT/TwelveMonkeys type or dependency enters the MBTiles main graph, API, core, or Native lanes.

## Required tests

- Format/magic/color/alpha/dimension/zoom/sparse/window/wrap/cache/render parity and independent files.
- Corrupt/bomb/mixed/conflicting tiles, aggregate limits, cancellation/races/close and no-partial-source tests.
- Optional WebP absent/present dependency, architecture, publication and staged consumer tests.

## Validation

Run MBTiles/image/render checks and applicable browser evidence, then qualityGate and `git diff --check`.

## Notes

None.
