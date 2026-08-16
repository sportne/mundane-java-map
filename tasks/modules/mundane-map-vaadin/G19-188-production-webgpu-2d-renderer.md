# G19-188 — Production WebGPU 2D renderer

Status: Proposed
Depends on: G19-184, G19-185, G19-186, G19-187
Gate: G19
Type: HITL

## Goal

Add an optional production WebGPU backend for the complete 2D prepared scene while retaining Canvas as required reference/fallback.

## Context

WebGPU can materially improve large-scene rendering and compute, but it is not available on every supported Vaadin 25 platform
and introduces shader, device-limit, memory, precision, loss/recovery and cross-renderer parity obligations.

## Scope

- Freeze supported WebGPU/WGSL versions, required/optional features and adapter/device/limit selection without fingerprinting or
  assuming hardware acceleration; detect absence/denial/blocklisting/software paths explicitly.
- Implement bounded pipelines/bind groups, vertex/index/uniform/storage buffers, textures/samplers/atlases, clips, blends,
  antialiasing/color/alpha, vector/raster/elevation/text and interaction overlays from the neutral prepared scene.
- Use only versioned project-owned validated WGSL/pipeline inventories; no caller shader, dynamic code, remote shader or metadata execution.
- Handle device/context loss, uncaptured errors, out-of-memory/limit shortfall, cancellation, scene/backend replacement, resize,
  worker interaction and teardown by atomically recovering or selecting complete Canvas.
- Compare semantic output and declared numeric/pixel/hit/edit tolerance against Canvas/AWT/SVG; measure WebGL2 only and require a
  separate decision before adding it as a third backend.

## Out of scope

- Required WebGPU support, WebGL2 implementation, public renderer/plugin API, 3D/globe/terrain/extrusion/models, caller shaders or pixel identity.

## Acceptance criteria

- Every G19 prepared construct renders or falls back before publication with no feature loss; Canvas remains complete when WebGPU is unavailable.
- GPU resources/work stay within exact negotiated and project ceilings and release exactly once through every failure/lifecycle path.
- Approved real-device results show a material named performance benefit without semantic, accessibility or interaction regression.

## Required tests

- Generated pipeline/WGSL inventory, shader validation, Canvas/GPU fixture parity and real-device Chrome/Firefox/Safari/Edge evidence where available.
- Adapter absence/denial, tiny limits, software device, validation/OOM/uncaptured errors, device loss/recovery, resize, rapid replacement,
  hostile buffers/textures, teardown and sustained navigation/scene-update memory/performance soak.

## Validation

Run Canvas/WebGPU parity, security, real-device and performance lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve WebGPU/WGSL profiles, real-device matrix, tolerances, performance benefit and the WebGL2 decision.
