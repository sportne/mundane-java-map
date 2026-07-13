# G10-007 — Additional projection selection

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Approve an evidence gate for projection expansion and record `DEFER` while the repository has no
concrete use case that justifies another built-in CRS operation or an optional PROJ adapter.

## Context

Level 1 explicitly registers hardened EPSG:4326 and EPSG:3857 behavior, and its registry already
accepts explicitly supplied immutable operations. Current examples and approved Level 2 profiles use
only those two CRSs. Projection breadth carries datum, axis, parameter, domain, licensing, deployment,
raster-warp, and Native Image costs that must not be added from popularity alone.

## Scope

Define the mandatory future evidence packet, the `DEFER`, `CORE_DIRECT`, and `PROJ_REQUIRED` outcomes,
their selection rules, and the architectural obligations of either implementation route. Inventory
the current formats/examples, record why they do not select another projection, preserve explicit
registration/diagnostics, and define how a later evidenced profile is decomposed without reserving an
empty module or speculative implementation card now.

## Out of scope

Production code or modules, a selected third CRS, general EPSG/WKT support, automatic CRS discovery,
an embedded CRS database, raster reprojection/warping, datum/grid/time operations, leaking PROJ types,
choosing from popularity alone, or adding JNI for performance without separate benchmark evidence.

## Acceptance criteria

- **G10 additional-projection evidence decision** approves the three-outcome rubric and records the
  current result as `DEFER`, with the repository evidence supporting that result.
- A later request must identify the workflow, exact CRS pair/direction, axis and unit convention,
  coordinate domain, datum/epoch needs, accuracy tolerance, formats, platform/native targets,
  authoritative conformance vectors, and legally usable fixtures before selection is reopened.
- `CORE_DIRECT` is available only for one bounded deterministic direct pair with a defensible JDK-only
  formula and conservative envelope rule; `PROJ_REQUIRED` remains an isolated optional adapter only
  for database/concatenation/horizontal-grid needs that still fit G4's fixed-epoch reversible 2D model.
- Vertical/compound coordinates, height correction, per-coordinate time/dynamic epoch, extra
  ordinates, and non-reversible operations require a separate public-capability design and therefore
  retain `DEFER` here rather than being forced through the existing `Projection` contract.
- Both future routes return only MundaneJ CRS/projection values, use explicit registry construction,
  preserve stable unsupported/mismatch/domain diagnostics, and create modules/tasks only with working
  behavior and verification.
- No definition, alias, projection, dependency, module, implementation task, Native Image claim, or
  raster-warp promise is added by this design decision.

## Required tests

No production tests. Define the evidence and later conformance, boundary, feature-query/render,
format-recognition, performance, packaging, consumer, and native checks that a selected profile must
map into reviewable vertical slices.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 additional-projection evidence decision**. Approval records `DEFER`; a future
maintainer may reopen selection only with the complete evidence packet. `DEFER` is a completed design
decision, not a blocker and not a claim that EPSG:4326/EPSG:3857 cover every application.
