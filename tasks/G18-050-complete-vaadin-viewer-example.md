# G18-050 — Complete Vaadin viewer example

Status: Proposed
Depends on: G18-031, G18-041, G11-034
Gate: G18
Type: HITL

## Goal

Deliver and approve a runnable, offline-by-default Vaadin application that demonstrates the reusable
component across existing vector, raster, workspace, diagnostics, measurement, editing, and export
capabilities.

## Context

The adapter is useful only if an ordinary Java web application can compose it with existing project
modules. The example should be an application consumer, not the home of missing component behavior.

## Scope

- Add non-published `examples/vaadin-viewer` with the approved Spring Boot/Vaadin runtime and an
  explicit Gradle run task.
- Provide an in-memory landing map plus checked-fixture or caller-selected server-local
  shapefile/GeoTIFF/elevation/workspace workflows.
- Add responsive layout, toolbar, layer list/order/visibility, fit/zoom, coordinates, selection
  inspector, source diagnostics, measurement, point editing, undo/redo, wrap toggle where
  compatible, and SVG export/download.
- Add bounded multi-file upload staging with sanitized identities, exact sidecar grouping, guarded
  paths, cancellation, per-UI ownership, and deterministic cleanup.
- Document development, production build/run, Node/browser prerequisites, server-local versus
  uploaded files, security, session ownership, no-basemap behavior, and deployment limits.

## Out of scope

Remote basemaps, API keys, user accounts/authorization, database persistence, shared sessions,
collaborative edits, production upload quotas/virus scanning, cloud deployment, or live-track stress.

## Acceptance criteria

- A clean checkout can launch the example and display the in-memory map without network data,
  credentials, desktop graphics, or a commercial component.
- The example opens and renders representative feature, raster/elevation, and workspace paths
  through public project APIs and exposes stable diagnostics instead of stack traces.
- All controls remain usable at approved desktop/mobile viewport sizes and keyboard focus order;
  route navigation and detach/reattach do not leak sources, uploads, resources, or listeners.
- Upload names/paths cannot escape the staging root or trigger implicit directory/resource scanning;
  sidecars, size/count limits, cancellation, failure cleanup, and session expiry are explicit.
- SVG download uses existing export behavior and clearly reports content that is not representable.
- The README makes no production security, scalability, cross-browser pixel, or map-data claim not
  supported by G18 evidence.

## Required tests

Application/context startup; route and component wiring; fixture/local/workspace open; upload path,
sidecar, limit, cancellation, and cleanup cases; controls and diagnostics; export download; detach
and session close. Complete browser behavior is owned by G18-060.

## Validation

```bash
./gradlew :examples:vaadin-viewer:check --console=plain
./gradlew :examples:vaadin-viewer:bootRun
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **Vaadin viewer usability, responsive layout, keyboard access, visual map fidelity,
and no-commercial/no-network-default review**. Stop `bootRun` after the named review.
