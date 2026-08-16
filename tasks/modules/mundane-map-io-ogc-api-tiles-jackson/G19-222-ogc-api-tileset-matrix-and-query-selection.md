# G19-222 — OGC API tileset, matrix, and query selection

Status: Proposed
Depends on: G19-014, G19-220, G19-221
Gate: G19
Type: HITL

## Goal

Select dataset or geospatial-resource tilesets, matrix definitions, representations, collections,
and temporal constraints deterministically before requesting tiles.

## Context

An API can advertise dataset and collection-specific tilesets with different data types, CRSs, tile
matrix sets, formats, styles, limits, and time domains. Choosing the first link or best-looking
media type would be unstable and could change the data semantics silently.

## Scope

- Add explicit immutable selectors for dataset versus geospatial-resource origin, collection(s),
  tileset, data type, media/profile, tile matrix set, style/layers where declared, and representation.
- Implement the approved Core, Tileset, Tilesets List, Dataset Tilesets, GeoData Tilesets, Collections
  Selection, and DateTime client behaviors when the server declares the corresponding conformance.
- Resolve embedded/linked TMS 2.0 definitions, variable matrix widths, limits, corner-of-origin,
  ordered axes, scale/cell size, CRS, bbox, variable tile dimensions, and URI templates into G19 core.
- Validate datetime instants/intervals, subset semantics, actual/closest time response policy, and
  collection selection without unbounded Cartesian expansion.
- Produce a detached request plan with exact permitted URI variables/query parameters and no network
  handles, credentials, or mutable service state.
- Reject ambiguous/missing/incompatible resources, undeclared conformance, unsupported CRS/media/data
  type, and out-of-limit matrices before tile work.

## Out of scope

- Automatic “best tileset” heuristics, reprojection, arbitrary query parameters, OpenAPI-generated
  operations, tile retrieval, or payload decoding.

## Acceptance criteria

- Equivalent discovery orderings yield the same explicit plan, matrix coordinates, envelope, limits,
  collections, and temporal request semantics.
- Official TMS 2.0 examples and cross-provider tilesets match core calculations including variable
  matrix widths, axis order, corner, scale, and precision.
- No selector can cause undeclared query behavior, unbounded collection/time expansion, or silent
  representation fallback.

## Required tests

- Dataset/geodata origin, collection, tileset, data type, media, representation, linked/embedded TMS,
  limits, variable widths, corner, CRS/axis, datetime/subset, actual/closest time, and ordering tests.
- Ambiguous/missing/undeclared/unsupported selections, temporal/collection fan-out, template/query
  injection, arithmetic/precision overflow, immutable plan, and stable-diagnostic tests.

## Validation

Run the OGC API Tiles and core tile-matrix checks plus approved selection corpus, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the selected Tiles/Common/TMS conformance classes, explicit
selection/default rules, collections/time behavior, CRS/precision policy, and interoperability data.
