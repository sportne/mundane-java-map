# G19-187 — Worker Canvas execution and PNG view capture

Status: Proposed
Depends on: G19-184, G19-185
Gate: G19
Type: AFK

## Goal

Move expensive prepared-scene work off the UI thread where supported and provide a bounded non-authoritative PNG current-view capture.

## Context

Large validation/preparation/painting can block input even before WebGPU. Users also need a convenient raster capture for scenes
that are not representable by canonical SVG, without turning browser encoding into a deterministic format claim.

## Scope

- Add version-locked worker messages and automatic Worker/OffscreenCanvas feature negotiation for validation, buffer preparation,
  spatial indexes and Canvas painting where supported, with a feature-equivalent main-thread Canvas fallback.
- Define ordered generation transfer, immutable/transferred buffer ownership, cancellation, worker crash/restart, context loss,
  backpressure, lifecycle teardown and main-thread semantic/ARIA synchronization.
- Add Java-triggered current accepted view capture using the browser's native PNG encoder, covering accepted map content, labels,
  rasters and interaction overlays under explicit inclusion policy.
- Bound output dimensions/pixels/bytes/time/concurrency/memory; stage bytes atomically through a session resource/download and
  expire/revoke on replacement, failure, detach and close.
- Document PNG capture as browser-dependent convenience, not byte/pixel reproducible or a Java image-writer capability.

## Out of scope

- JPEG/WebP export, arbitrary DOM/chrome capture, hidden remote resources, deterministic archival output or reduced fallback features.

## Acceptance criteria

- Worker and main-thread Canvas paths are semantically identical and meet declared visual/hit/input tolerances.
- Worker absence/crash/context loss returns to a complete Canvas path without stale paint, lost state or leaked buffers/resources.
- PNG capture either publishes one complete bounded current view or nothing and is clearly separated from canonical exports.

## Required tests

- Worker/main parity, capability absence, message corruption/reorder, cancellation/backpressure, crash/restart/context loss and
  detach/session cleanup; UI responsiveness and memory evidence.
- Canvas/WebGPU-ready raster/vector/label/overlay captures, exact limits, encoder rejection/timeout, old-publication expiry,
  accessibility-triggered workflow and real-browser PNG decode/placement checks.

## Validation

Run Vaadin worker/frontend/component/browser/export/performance lanes, then qualityGate and `git diff --check`.

## Notes

None.
