# G3-001 — Tool Lifecycle and Navigation Routing

Status: Proposed
Depends on: G4-002
Gate: G3
Type: AFK

## Goal

Provide one deterministic active-tool lifecycle and pointer-routing model that lets interactive tools
capture drags without breaking the existing pan and cursor-centered zoom behavior.

## Context

`MapView` currently installs private mouse listeners that always start a pan on press, update it on
drag, and zoom on wheel input. Public `MapPointerEvent` exposes only moved and clicked events. A tool
contract must remain toolkit-neutral in `mundane-map-api`, while Swing event conversion, cursor
ownership, focus loss, and event-dispatch-thread enforcement remain in `mundane-map-awt`. G4-002
provides the exact map/display CRS definitions and optional map-coordinate conversion used by the
tool context and events.

## Scope

- Public tool, lifecycle, pointer-event, capture, and cursor-intent contracts in
  `mundane-map-api`.
- Toolkit-neutral routing state in `mundane-map-core` where reusable.
- Swing event conversion and active-tool management in `mundane-map-awt`.
- Unit and Swing interaction tests plus public Javadocs.

## Out of scope

- Hit testing, selection, hover, measurement calculations, editing, gestures, and touch input.
- Toolkit-specific cursor objects in the public API.

## Acceptance criteria

- A map view has at most one active tool; replacement cancels and deactivates the old tool before
  activating the new tool, with idempotent cleanup.
- Pointer press, drag, release, move, click, wheel, and cancel events carry finite screen coordinates,
  finite map coordinates when present, and relevant button/modifier data without AWT types.
- A tool may explicitly claim capture on press; captured drag/release events go to that tool and
  suppress navigation until release or cancellation.
- An uncaptured primary-button drag pans, and wheel zoom remains available unless the active tool
  explicitly consumes that event.
- Tool replacement, component disable/removal, and focus loss release capture and deliver one cancel;
  stale releases cannot affect a later tool.
- Cursor intent belongs to the active/capturing tool and returns to the documented default after
  deactivation or cancellation.
- Tool callbacks and view mutation execute on the Swing event-dispatch thread with stable event order.

## Required tests

- Core routing-state tests for activate/deactivate, capture, consumption, cancellation, and stale
  events.
- API/router tests cover both present and absent map coordinates without suppressing screen-space
  release, cancellation, or navigation behavior.
- Swing tests dispatching real mouse, wheel, focus, and component lifecycle events.
- Regression tests proving ordinary pan, zoom, click, and moved callbacks still work with no tool.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep routing explicit and synchronous; do not use global event listeners, reflection, dynamic proxies,
or background mutation of Swing state.
