# G19-125 — Tactical-graphic control-point and geometry engine

Status: Proposed
Depends on: G19-010, G19-011, G19-122
Gate: G19
Type: HITL

## Goal

Implement the exhaustive current tactical-graphic/control-measure inventory, immutable control-point/parameter model,
validation, and bounded geometry-construction algorithms.

## Context

The module has no tactical graphics. These are rule-driven geodesic/projected constructions, not extra point icons.

## Scope

- Generate exact per-code point/parameter cardinality, order, closed/open, geometry family, and edition applicability rules.
- Add immutable graphic definitions/instances/control points/typed parameters with deterministic validation and serialization.
- Implement all catalogued construction families: lines/areas, boundaries, axes/arrows, corridors/routes, sectors/arcs/range fans,
  obstacles, fire-support, maneuver, airspace, maritime/subsurface, CBRN, and remaining approved groups.
- Define CRS/projection/geodesic distance/azimuth, dateline/world-wrap/pole behavior, clipping, degeneracy, precision, and tolerance.
- Bound input points/parameters, generated vertices/segments/arcs, geodesic/topology work, temporary memory, and diagnostics prospectively.

## Out of scope

- Decorations/labels/edit UI, freehand approximation, tactical inference, and hidden vendor control-point conventions.

## Acceptance criteria

- Every current tactical entry validates its exact control-point/parameter contract and constructs deterministic base geometry.
- Invalid/degenerate/antipodal/over-budget input fails before any portrayal or edit state is published.
- Geometry agrees with reviewed official/independent references within per-family geodesic/projected tolerances.

## Required tests

- Exhaustive code/cardinality/order/parameter/family construction matrix and reference fixtures.
- CRS/dateline/pole/antipodal/degenerate/precision/segment/work limits, property tests, and generator provenance.

## Validation

Run the module check and topology/geodesic/corpus/performance lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves construction families, reference evidence, and numeric tolerances.
