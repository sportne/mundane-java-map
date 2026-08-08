# G18-040 — Browser raster and elevation slice

Status: Proposed
Depends on: G18-020, G9-002
Gate: G18
Type: AFK

## Goal

Display bounded raster and elevation-source windows through the browser component using a secure
same-origin binary transport and Canvas `ImageData` without AWT or an image-encoding dependency.

## Context

Existing raster/elevation sources return detached packed RGBA values after project-owned request,
resampling, colorization, and hillshade work. A browser transport is needed, but an XYZ service or
PNG encoder would distort the format-neutral source boundary and duplicate map-grid behavior.

## Scope

- Add owned/borrowed raster and elevation bindings, request planning, opacity/interpolation/style
  configuration, diagnostics, cancellation, and lifecycle.
- Define the exact bounded binary framing for detached RGBA windows and serve immutable same-origin
  resources with checked session/generation authorization, cache identity, expiry, and headers.
- Fetch, validate, convert to `ImageData`, cache, and paint grid/affine windows in the bundled client.
- Reuse existing elevation colorization/hillshade and raster accounting/resampling behavior.
- Preserve the current explicit `EncodedRasterDecoder` boundary for sources backed by PNG/JPEG;
  the web adapter begins with an already opened `RasterSource` and adds no codec implementation.

## Out of scope

WMS/XYZ endpoints, browser-side GeoTIFF/PNG/JPEG parsing, remote tiles, AWT/ImageIO, a new public
raster codec, cross-CRS warping, GPU shaders, or offline browser storage.

## Acceptance criteria

- Any already opened compatible raster or elevation implementation can render through its existing
  `RasterSource`/`ElevationSource` contract without format-specific frontend code; encoded sources
  retain their application-supplied decoder requirement.
- Binary resources enforce exact byte/pixel/window limits, same-origin authorization, content type,
  no-sniff/cache policy, expiry, cancellation, and no partial accepted buffer.
- Grid-edge visibility, affine placement, nearest/bilinear requests, opacity, no-data, color ramp,
  and hillshade agree with existing core semantics.
- Stale windows cannot paint into a newer viewport; accepted cache entries have one owner and are
  released on source version, binding replacement, detach expiry, or close.
- Missing/sparse windows and source/transport/client decode failures publish stable diagnostics and
  leave the prior complete scene or explicit absence intact.

## Required tests

Binary framing and hostile lengths; authorization/expiry/headers; raster window/placement/request
parity; affine and sparse sources; elevation color/hillshade/no-data; stale generation/cache
invalidation; cancellation and owned/borrowed cleanup; tolerant Canvas image-region fixtures.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The framing is a private transport between one adapter and its bundled client. It is not a new map
format or general binary protocol.
