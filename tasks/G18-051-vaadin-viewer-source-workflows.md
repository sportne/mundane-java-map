# G18-051 — Vaadin viewer source workflows

Status: Proposed
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
