# G18-050 — Vaadin viewer application shell

Status: Complete
Depends on: G18-031, G18-041
Gate: G18
Type: HITL

## Goal

Deliver and approve a runnable, offline-by-default Vaadin application shell that demonstrates the
reusable component with an in-memory map and its primary controls and tools.

## Context

The adapter is useful only if an ordinary Java web application can compose it with existing project
modules. The example should be an application consumer, not the home of missing component behavior.

## Scope

- Add non-published `examples/vaadin-viewer` with the approved Spring Boot/Vaadin runtime and an
  explicit Gradle run task.
- Provide an in-memory landing map that requires no network data.
- Add responsive layout, toolbar, layer list/order/visibility, fit/zoom, coordinates, selection
  inspector, source diagnostics, measurement, point editing, undo/redo, wrap toggle where
  compatible.
- Document development launch, Node/browser prerequisites, session ownership, and no-basemap
  behavior.

## Out of scope

Format/workspace opening, uploads, SVG downloads, remote basemaps, API keys, user accounts,
database persistence, shared sessions, collaborative edits, cloud deployment, or live-track stress.

## Acceptance criteria

- A clean checkout can launch the example and display the in-memory map without network data,
  credentials, desktop graphics, or a commercial component.
- All controls remain usable at approved desktop/mobile viewport sizes and keyboard focus order;
  route navigation and detach/reattach do not leak sources, resources, or listeners.
- The README makes no production security, scalability, cross-browser pixel, or map-data claim not
  supported by G18 evidence.

## Required tests

Application/context startup; route and component wiring; in-memory scene; controls, tools, and
diagnostics; detach and session close. Complete browser behavior is owned by G18-060.

## Validation

```bash
./gradlew :examples:vaadin-viewer:check --console=plain
./gradlew :examples:vaadin-viewer:bootRun
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **Vaadin viewer shell usability, responsive layout, keyboard access, visual map
fidelity, and no-commercial/no-network-default review**. Stop `bootRun` after the named review.

Completion record (2026-08-09): added the non-published Spring Boot 4.1.0 and Flow 25.2.4
application with the approved Flow-only dependency surface and a mechanically checked frozen npm
resolution. Each route owns an in-memory vector map and fixed-lane editable points plus responsive
native HTML controls for ordering/visibility, fit/zoom, coordinates, selection, diagnostics,
measurement, point create/move, undo/redo, and compatible horizontal wrap. Context, route, control,
tool, diagnostic, edit, and idempotent cleanup tests run without browser or Node work in the normal
gate. A development launch with the pinned Node 24.14.0/npm 11.9.0 pair built the local frontend,
served the root route over loopback with HTTP 200, disabled usage statistics, and was stopped after
review. The example README records prerequisites, per-session ownership, no-basemap behavior, and
the intentionally unsupported production claims.
