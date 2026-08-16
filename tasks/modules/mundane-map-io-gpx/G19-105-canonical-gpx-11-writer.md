# G19-105 — Canonical GPX 1.1 writer

Status: Proposed
Depends on: G19-103, G19-104
Gate: G19
Type: AFK

## Goal

Write deterministic schema-valid GPX 1.1 documents from the complete domain model with strict
representability, bounded output, and transactional failure behavior.

## Context

The module is read-only. Writing is valuable for creating, editing, merging, simplifying, and exchanging
waypoints/routes/tracks across GPS applications, but it must not silently discard domain or extension data.

## Scope

- Add an immutable writer/options builder requiring creator/document and configuring encoding, schema-location,
  extension registry/policy, cancellation, limits, and file/stream/byte output.
- Preflight all standard values, ordering, namespaces, extension infosets/codecs, representability, and output estimates;
  report path-specific stable diagnostics before producing committed bytes.
- Emit deterministic GPX 1.1 schema order, namespace declarations/prefixes, attributes, XML escaping, UTF-8,
  line endings, numeric/date-time/URI lexical forms, and bounded canonical whitespace.
- Support complete metadata, waypoints, routes, tracks, segments, standard point data, root/scoped extensions, and
  byte-identical output for identical values/options.
- Implement transactional bounded sinks, atomic filesystem replacement, cancellation, rollback, suppressed cleanup
  failures, and exact output/resource ownership.

## Out of scope

- GPX 1.0 writing, vendor-specific generated semantics, lossy downgrade, device transfer, and preservation of source formatting.

## Acceptance criteria

- Every complete supported domain value writes valid GPX 1.1 and reads back to equal semantic domain/extension data.
- Non-representable/invalid/over-budget input fails before target mutation; cancellation/failure preserves prior files.
- Repeated output is byte-identical and accepted by multiple independent GPX consumers.

## Required tests

- Golden full-document/field/ordering/numeric/time/Unicode/namespace/opaque-and-typed-extension output.
- Schema validation, deterministic and read-write-read tests, representability/output limits, cancellation, cleanup, and rollback.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, schema/corpus/publication tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
