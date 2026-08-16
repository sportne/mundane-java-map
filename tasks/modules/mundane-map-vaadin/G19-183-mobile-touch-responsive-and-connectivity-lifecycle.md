# G19-183 — Mobile touch, responsive, and connectivity lifecycle

Status: Proposed
Depends on: G19-180, G19-181
Gate: G19
Type: AFK

## Goal

Complete bounded translation/scale touch navigation, responsive mobile behavior and server-authoritative disconnect/reconnect.

## Context

G18 pointer handling lacks the frozen mobile gesture vocabulary, kinetic motion, full zoom/orientation/reflow evidence and a
documented host-owned PWA boundary.

## Scope

- Implement one-finger pan/tool taps, two-finger centroid pan plus pinch zoom and bounded double-tap zoom with fixed slop,
  timing, contact transition, capture, cancellation, tool priority and settled synchronization rules; never rotate the map.
- Add bounded ordinary-navigation inertia with fixed sampling/velocity/duration/distance/frame limits, reduced-motion disable,
  no tool/edit use, no zoom/bounce and immediate lifecycle/new-input cancellation.
- Preserve function across mobile browser chrome, safe areas, orientation, resize, high DPI, browser zoom/reflow and virtual
  keyboard changes without private viewport state.
- Define connection loss, push failure, reconnect, generation resynchronization, pending edit/query/resource/gesture cleanup,
  loading status and retry behavior.
- Document that service workers, manifests, cache/update scope and runtime offline data are host-owned; publish safe immutable-
  asset caching and session-resource exclusion rules plus optional host integration hooks.

## Out of scope

- Pen, rotation/bearing, inertial zoom, elastic overscroll, component-owned service workers, or standalone offline operation.

## Acceptance criteria

- Touch gestures and inertia never lose contacts, route post-cancel input, commit stale edits or leave client/server viewports
  divergent; reduced motion removes animation without removing navigation.
- Resize/orientation/reconnect preserves or explicitly resets state exactly once with stable visible status.
- Host PWA integration cannot cache/replay session-authorized resources or make stale local state authoritative.

## Required tests

- Real Android Chrome and iOS/iPadOS Safari touch workflows plus automated multi-pointer/contact-transition/rate/cancel tests.
- Inertia limits/cancellation/reduced-motion, orientation/safe-area/DPI/zoom/reflow, offline/reconnect/push failure, host service-
  worker policy, detach/reattach/session close and long gesture soak tests.

## Validation

Run Vaadin frontend/component mobile and lifecycle lanes, then qualityGate and `git diff --check`.

## Notes

None.
