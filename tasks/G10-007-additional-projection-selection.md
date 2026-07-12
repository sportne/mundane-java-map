# G10-007 — Additional projection selection

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Select the next projection from demonstrated map use cases and decide whether it belongs in JDK-only
core or an isolated optional PROJ adapter.

## Context

Level 1 explicitly registers hardened EPSG:4326 and EPSG:3857 behavior. Projection breadth carries
datum, axis, parameter, domain, licensing, deployment, and Native Image costs that should not be added
speculatively.

## Scope

Collect concrete coordinate ranges, accuracy needs, CRS definitions, platform targets, and expected
data formats from at least one real use case. Compare a focused pure-Java implementation with an
isolated PROJ adapter, including conformance sources, error tolerances, diagnostics, registration,
licensing, native deployment, and Native Image behavior. Select one bounded next slice and decompose
its implementation and verification tasks.

## Out of scope

Implementing a projection, claiming general EPSG support, automatic CRS discovery, embedding a CRS
database in core, leaking PROJ types, choosing from popularity alone, or adding JNI for performance
without separate benchmark evidence.

## Acceptance criteria

- A maintainer approves a use-case-backed projection/CRS profile, accuracy tolerance, and supported
  coordinate domain.
- The decision records pure-Java versus optional-adapter tradeoffs and preserves explicit registration
  and stable unsupported-CRS diagnostics.
- Any PROJ integration is isolated outside Level 1 modules and has explicit packaging, licensing,
  resource, platform, and Native Image expectations.
- Follow-up tasks cover conformance vectors, boundary/error cases, feature/raster integration, and
  supported native lanes without creating an empty module.

## Required tests

No production tests. Identify authoritative conformance vectors and boundary cases, and map them to the
approved follow-up task set.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer selects the use case, projection profile, tolerance, and JDK-only or
isolated-PROJ approach before implementation work is scheduled.
