# G18-010 — First Vaadin Canvas vector slice

Status: Proposed
Depends on: G18-001
Gate: G18
Type: AFK

## Goal

Publish the first working `MundaneMap` Vaadin component that displays an in-memory point, line, and
polygon map and provides smooth browser-local pan, zoom, resize, and Java-driven fit.

## Context

G18-001 approves a project-authored `<mundane-map-canvas>` rather than a commercial or third-party
map renderer. This slice must create a useful end-to-end component, not an empty adapter or frontend
scaffold.

## Scope

- Create `modules/mundane-map-vaadin` as a non-native optional adapter with the approved minimum Flow
  dependency.
- Add the Flow Java wrapper and packaged local JavaScript Canvas custom element.
- Implement the versioned full-scene path for immutable snapshot layers with point, line, polygon,
  holes, solid marker/line/fill portrayal, deterministic order, background, viewport, fit, resize,
  device-pixel ratio, and lifecycle.
- Add exact protocol/limit validation, browser viewport parity fixtures, public Javadocs, architecture
  rules, and a test-only route or harness that proves a real rendered scene.

## Out of scope

Feature sources, multipart geometry, labels, advanced symbols, hit testing, tools, raster/elevation,
world wrap, uploads, the complete example application, or publication closeout.

## Acceptance criteria

- A consumer can construct `MundaneMap`, set snapshot layers, fit them, and embed the component in an
  ordinary Vaadin layout without depending on AWT.
- Drag and wheel/pinch navigation repaint locally without a server round trip for each animation
  frame; a settled viewport event reports one finite canonical viewport.
- CSS resize and device-pixel-ratio changes preserve logical-pixel symbol sizes and viewport center
  while reallocating bounded Canvas backing storage.
- Scene replacement is atomic and rejects wrong versions, stale generations, non-finite values,
  duplicate IDs, over-limit content, and unsupported symbols with stable diagnostics.
- Detach, reattach, disable, and close release listeners, pointer capture, queued paints, and
  adapter-owned state predictably.
- Architecture evidence rejects AWT, Swing, Vaadin Map, TestBench, remote map libraries, and Vaadin
  dependencies in existing production modules.

## Required tests

Java component/configuration/lifecycle tests; protocol boundary and hostile-value tests; JavaScript
viewport/resize/input/draw-order tests; point/line/polygon/hole scene fixtures; attach/detach and
unsupported-symbol diagnostics.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the wire model private. Full-scene replacement is sufficient until G18-011 evidence justifies
stable-ID patches.

