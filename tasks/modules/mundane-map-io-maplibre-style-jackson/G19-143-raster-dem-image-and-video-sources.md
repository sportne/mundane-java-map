# G19-143 — Raster, DEM, image, and video sources

Status: Proposed
Depends on: G19-012, G19-044, G19-052, G19-054, G19-079, G19-141
Gate: G19
Type: HITL

## Goal

Implement all pinned raster, raster-DEM, image, and video source semantics for bounded 2D use.

## Context

The adapter rejects these source types. Complete 2D styles require tiled rasters/elevation, four-corner
images, and video frames, but the project will not become a media-container or codec implementation.

## Scope

- Model all v26.2.1 properties/defaults for raster, raster-DEM, image, and video sources.
- Resolve TileJSON/templates and explicit raster decoders; implement tile size/scheme/zoom/bounds/cache/update behavior.
- Decode Mapbox, Terrarium, and custom DEM encodings into neutral elevation with units/no-data/precision policy.
- Implement image/video four-corner projective placement, resampling, color/alpha, wrap, updates, and lifecycle.
- Add a host-neutral time-indexed decoded-frame provider with seek/play/pause/loop/end, explicit clock,
  cancellation, invalidation, and exact close ownership; no built-in container/codec.
- Bound encoded/decoded resources, dimensions/pixels/frames/timestamps, concurrency/cache, and rendering work.

## Out of scope

- FFmpeg/JNI, container/audio/codec decoding, DRM, implicit URL playback, and 3D terrain mesh rendering.

## Acceptance criteria

- Every pinned source property produces correct neutral raster/elevation/frame placement or a stable incompatibility.
- Frame/source updates are generation-safe, bounded, cancelable, and failure-atomic.
- A caller-supplied provider makes video styles usable without installing a production media dependency.

## Required tests

- Property/default/TileJSON/encoding/four-corner/resampling/color/timing/update/lifecycle matrices.
- Malformed/hostile images, DEMs and frame providers; cancellation/races/cache/limits and independent fixtures.

## Validation

Run module/raster/elevation/render integration checks, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve the decoded-frame API, timing semantics, and absence of a built-in video codec.
