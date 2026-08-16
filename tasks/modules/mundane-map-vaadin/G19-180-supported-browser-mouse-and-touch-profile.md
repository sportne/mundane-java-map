# G19-180 — Supported browser, mouse, and touch profile

Status: Proposed
Depends on: G18-061
Gate: G19
Type: HITL

## Goal

Freeze and enforce the full pinned Vaadin 25 browser/platform profile and the supported mouse/touch input boundary.

## Context

G18 has strong Chromium/Firefox evidence but does not claim Vaadin's full desktop/mobile browser matrix. Pen is now a
deliberate exclusion, and branded-browser/device claims cannot be inferred from Playwright engines or emulation.

## Scope

- Generate a reviewed compatibility inventory from the pinned Vaadin support source and fail dependency upgrades on drift.
- Support evergreen Chrome and Chromium Edge, evergreen Firefox plus ESR, Safari 17+, Android Chrome, and iOS/iPadOS
  Safari; record exact tested browser, OS, device and engine versions.
- Define required Canvas, Pointer Events, resource, observer, worker and lifecycle feature detection with stable fallback/
  rejection diagnostics rather than user-agent sniffing.
- Support mouse and touch only. Ignore or safely cancel `pointerType=pen`; make no stylus, pressure, tilt, eraser, palm or
  digital-ink claim, and bound hostile/unknown pointer types and identifiers.
- Document host browser, secure-context, deployment, push/transport and embedding responsibilities.

## Out of scope

- Internet Explorer, EdgeHTML, mobile Firefox, embedded webviews, Electron, pen input, or products outside Vaadin's matrix.

## Acceptance criteria

- The generated support inventory and public wording name the exact products/minimums and distinguish automation from real
  product evidence.
- Unsupported capabilities fail before partial scene/input state; pen cannot route a tool event or mutate navigation.
- All claimed products complete load/paint, resource, input, resize, detach/reattach and cleanup smoke workflows.

## Required tests

- CI Playwright Chromium/Firefox/WebKit matrix plus version-stamped Chrome, Firefox ESR, Safari/macOS, Edge/Windows,
  Chrome/Android and Safari/iOS or iPadOS smoke evidence.
- Missing-feature, unknown/pen pointer, pointer-ID exhaustion, capture/loss, hostile synthetic event and lifecycle tests.

## Validation

Run the Vaadin frontend/component and real-browser compatibility lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve the upstream Vaadin support snapshot and version-stamped real-product evidence.
