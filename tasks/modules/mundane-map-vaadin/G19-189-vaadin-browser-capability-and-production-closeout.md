# G19-189 — Vaadin browser capability and production closeout

Status: Proposed
Depends on: G19-182, G19-183, G19-186, G19-187, G19-188
Gate: G19
Type: HITL

## Goal

Close the Vaadin adapter only after browser, accessibility, rendering, protocol, security, lifecycle and production evidence matches its matrix.

## Context

Passing component tests cannot establish real product/device/AT support, cross-renderer G19 parity, hostile private-protocol safety,
GPU fallback, long-session boundedness or accurate public embedding/deployment claims.

## Scope

- Audit every `CAPABILITIES.md` row and completed neutral G19 construct as implemented, tested or deliberately excluded.
- Run the complete Vaadin browser and focused AT matrices, WCAG/component review, mobile touch, locale/theme, host PWA/reconnect,
  Canvas/worker/WebGPU, PNG/SVG, source/cache/wrap/edit and whole-world example workflows.
- Exercise private JSON/binary/patch/worker/GPU/resource inputs, same-origin authority, generation recovery, rate/backpressure,
  cancellation, ownership and every detach/reattach/session/application failure boundary.
- Record named scene/query/control/binary/patch/transfer/paint/frame/input/memory/resource/cache/GPU ceilings and long-soak plateaus.
- Reconcile Java Javadocs, capability/design/roadmap/task docs, example/deployment instructions, dependency/license/offline/publication
  profile and exact exclusions: private JS, no direct remote fetch/PWA/pen/3D/WebGL2 claim.

## Out of scope

- Broadening the approved browser/AT/device/backend/network/2D boundaries during closeout or certifying host applications.

## Acceptance criteria

- Independent browser/accessibility/security review finds no unaccounted applicable capability or misleading support statement.
- Full supported workflows pass on named real products/devices, with transparent gaps rather than engine/emulation extrapolation.
- Performance/security/lifecycle evidence remains bounded and stable with Canvas-only and WebGPU-enabled configurations.

## Required tests

- All predecessor automation/human/device lanes from clean pinned inputs, plus cross-browser whole-world repeated navigation,
  editing, resource, reconnect, export and detach/session/application soak.
- Offline repository/publication/production frontend/boot example, dependency/license, API/Javadoc and hostile protocol corpus lanes.

## Validation

Run all Vaadin/example/browser/AT/performance/security lanes, qualityGate, applicable offline/publication checks, and `git diff --check`.

## Notes

HITL checkpoint: browser, accessibility and security reviewers approve the evidence, deviations and exact public wording.
