# G19-053 — TileJSON versioned model and parser

Status: Proposed
Depends on: G19-014, G19-052
Gate: G19
Type: HITL

## Goal

Add a fully supported optional Jackson Core adapter that parses common TileJSON 2.x and 3.0.0
documents into an immutable bounded metadata model.

## Context

The JDK has no JSON parser. Keeping parsing outside `mundane-map-io-http-tiles` preserves its JDK-only
transport contract and avoids maintaining a custom RFC 8259 implementation. The adapter remains
explicitly constructed and first-class; “optional” means only that direct HTTP tiles do not require
Jackson.

## Scope

- Add a non-empty `mundane-map-io-tilejson-jackson` module using the repository's exact pinned
  Jackson Core dependency and depending on the JDK-only HTTP/core APIs.
- Pin and implement version-specific rules for TileJSON 2.0.0, 2.0.1, 2.1.0, 2.2.0, and 3.0.0 over
  RFC 8259 JSON; reject unsupported major/version claims deterministically.
- Model and validate `tilejson`, `tiles`, `vector_layers`, `attribution`, `bounds`, `center`, `data`,
  `description`, `fillzoom`, `grids`, `legend`, `maxzoom`, `minzoom`, `name`, `scheme`, `template`,
  `version`, and version-applicable requirements/defaults.
- Expose bounded unknown members as required by the declared TileJSON profile without retaining an
  unbounded JSON tree or using Jackson Databind.
- Define duplicate member, invalid optional value, invalid required value, number, Unicode, null,
  ordering, endpoint, template, bounds, zoom, vector-layer, and attribution behavior exactly.
- Publish immutable limits/options/problems and complete public Javadocs/capability documentation.

## Out of scope

- TileJSON 1.0.0, TileJSON generation, HTTP retrieval, tile fetching, vector tile decoding, UTFGrid,
  GeoJSON overlay fetching, HTML evaluation, Jackson Databind, and parser discovery.

## Acceptance criteria

- Official examples and independent 2.x/3.0.0 producer documents yield the version-correct model.
- Unknown members are bounded and available while malformed/duplicate/invalid required data fails
  atomically with stable value-safe diagnostics.
- Dependency verification proves Jackson is absent from `mundane-map-io-http-tiles` and present only
  in the explicit optional adapter graph.

## Required tests

- Per-version required/optional/default/unknown-field matrices and official/cross-producer fixtures.
- Duplicate keys, wrong types, invalid optional-versus-required values, deep/wide/long JSON, hostile
  Unicode/numbers/URLs/templates/attribution, truncation, cancellation, allocation, and dependency-
  isolation tests.

## Validation

Run the new adapter's `check` and dependency-verification tasks, its TileJSON corpus lane, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact TileJSON revisions, unknown-member exposure,
Jackson dependency inventory, corpus provenance, and final supported-version wording.
