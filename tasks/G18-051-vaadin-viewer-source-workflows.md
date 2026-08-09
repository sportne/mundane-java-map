# G18-051 — Vaadin viewer source workflows

Status: Complete
Depends on: G18-050, G11-034
Gate: G18
Type: AFK

## Goal

Add representative bounded feature, raster/elevation, and workspace workflows to the Vaadin viewer
through existing public APIs.

## Scope

- Add checked-fixture and caller-selected server-local shapefile, GeoTIFF/elevation, and workspace
  opening paths with explicit registries, limits, ownership, cancellation, and diagnostics.
- Add layer order/visibility and compatible wrap controls for opened sources.
- Document server-local path trust, supported formats, decoder boundaries, and cleanup.

## Out of scope

Browser uploads, SVG downloads, remote basemaps, directory scanning, credentials, databases, or
new format-specific component APIs.

## Acceptance criteria

- Each representative source opens through its existing public boundary and renders without AWT or
  format-specific browser code.
- Failures expose stable source diagnostics without paths, source data, or stack traces.
- Replacement, route removal, detach, session close, and application stop close only owned sources
  exactly once.

## Required tests

Fixture/local/workspace opening, limits, cancellation, diagnostics, ownership, replacement, route,
detach, and session-close cases.

## Validation

```bash
./gradlew :examples:vaadin-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

Completion record (2026-08-09): the Vaadin viewer now opens checked-fixture or explicitly selected
server-local shapefiles, display GeoTIFFs, metre elevation GeoTIFFs, and feature workspaces through
their existing Java source and workspace boundaries. One cancellable route-owned opening lane
installs component-owned serialized leases while retaining exact source/workspace ownership, so
replacement, visibility/order changes, clear, detach, Vaadin session destruction, and Spring
application shutdown close each owner exactly once. Explicit tighter query, raster, and workspace
limits and a closed workspace opener/catalog registry bound all input and rendering work. The UI
reports stable codes without paths, source values, or stacks, and documentation records the trusted
server-local path boundary, supported decoders, absence of browser format code, and cleanup model.
Real checked fixtures plus cancellation, limits/diagnostics, replacement, ordering/visibility,
route/session, and application-stop tests cover the workflow.
