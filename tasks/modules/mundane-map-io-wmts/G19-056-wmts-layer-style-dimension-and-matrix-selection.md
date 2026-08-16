# G19-056 — WMTS layer, style, dimension, and matrix selection

Status: Proposed
Depends on: G19-014, G19-055
Gate: G19
Type: HITL

## Goal

Select one WMTS layer/profile deterministically and translate its linked tile matrix set into the
neutral bounded tile-matrix model.

## Context

A capabilities document can offer many layers, styles, formats, dimensions, matrix sets, limits,
CRSs, and bindings. Choosing the first advertised value would be convenient but unstable and can
silently select an incompatible projection or media profile.

## Scope

- Add immutable explicit selectors for layer, style, format, dimensions, tile matrix set, info
  format, and binding preference, with carefully limited use of standard declared defaults.
- Resolve matrix-set links and limits, scale denominators, top-left corners, tile/matrix dimensions,
  identifiers, bounding boxes, well-known scale sets, and supported CRS into G19 core values.
- Implement OWS/WMTS axis-order, units, pixel-size, scale, and CRS compatibility rules without
  coordinate guessing.
- Validate dimension defaults/current/enumerated/interval values through a closed supported profile;
  reject ambiguous or unbounded dimension expansion.
- Produce a detached immutable request plan that contains no credentials and performs no network I/O.
- Report unsupported/ambiguous layers, defaults, media, matrices, CRSs, dimensions, and bindings with
  stable diagnostics before tile retrieval.

## Out of scope

- Reprojection, automatic “best layer” heuristics, arbitrary temporal/interval expansion, tile
  requests, GetFeatureInfo requests, and SOAP.

## Acceptance criteria

- Equivalent capabilities orderings produce the same explicitly selected request plan.
- Matrix envelopes/indices/limits and CRS axis/scale calculations match official examples and
  independent WMTS clients within exact declared rules.
- No selection path can exceed matrix/dimension/link limits or silently fall back to another profile.

## Required tests

- Multi-layer/style/format/dimension/matrix/binding selection, default, ambiguity, matrix-limit,
  axis-order, scale, CRS, bounding-box, well-known-scale-set, and reordered-document fixtures.
- Unsupported/duplicate/missing selection, dimension interval/fan-out, arithmetic/precision overflow,
  hostile identifiers, cancellation, immutable-plan, and stable-diagnostic tests.

## Validation

Run the WMTS and core tile-matrix checks and approved selection corpus, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the selection/default, dimension, CRS/axis, scale, precision,
and unsupported-profile policies before completion.
