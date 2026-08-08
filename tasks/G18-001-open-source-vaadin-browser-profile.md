# G18-001 — Open-source Vaadin browser profile decision

Status: Proposed
Depends on: G16-007, G17-005
Gate: G18
Type: HITL

## Goal

Approve the exact open-source dependency, architecture, protocol, supported behavior, limits,
verification, and task graph for a reusable Vaadin browser map component.

## Context

The project has a toolkit-neutral API/core and one AWT presentation adapter. A browser is now a
demonstrated second presentation consumer. The user has rejected commercial components, so G18 must
use Vaadin Flow's component-integration surface without Vaadin Map, TestBench, or another commercial
artifact.

## Scope

- Review `design/G18-vaadin-browser-frontend.md`.
- Resolve and inventory one exact Vaadin 25 BOM/Gradle plugin and the minimum Flow production graph.
- Approve the project-authored Canvas 2D web component, optional adapter boundary, private protocol,
  server/browser responsibilities, supported browsers, limits, diagnostics, lifecycle, security,
  offline build, publication, and evidence policies.
- Freeze G18-010 through G18-061 dependencies and ownership.

## Out of scope

Production code, a Vaadin or third-party map renderer, remote basemaps, credentials, JavaScript
projection/style engines, Native Image claims, or general web-application infrastructure.

## Acceptance criteria

- The resolved Maven/frontend inventory contains only approved open-source artifacts with exact
  versions, checksums, licenses, services, build tools, and runtime roles.
- Architecture checks are specified to reject `com.vaadin:vaadin-map-flow`, `@vaadin/map`,
  TestBench, other commercial artifacts, and Vaadin/browser leakage outside the optional adapter and
  example.
- The profile defines exact initial geometry, symbol, label, interaction, raster, elevation,
  workspace, upload, export, and world-wrap support and names every excluded behavior.
- Protocol framing, finite-value validation, counts/bytes/rates, stale-generation behavior,
  cancellation, ownership, detach/session cleanup, and stable diagnostic precedence are actionable.
- Browser automation uses open-source Playwright in a separate explicit lane and does not download
  browsers or Node packages during `qualityGate`.
- G18-010 through G18-061 remain reviewable one-to-five-day vertical slices.

## Required tests

No production tests. Review resolved dependency and license reports plus representative scene,
viewport, stale-event, hostile-client, raster-resource, upload, detach, and unsupported-symbol cases
against the design.

## Validation

```bash
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **open-source Vaadin dependency, browser component profile, private protocol,
supported surface, and task graph approval**.

