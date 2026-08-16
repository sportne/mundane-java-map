# G19-106 — GPX interoperability and capability closeout

Status: Proposed
Depends on: G19-105
Gate: G19
Type: HITL

## Goal

Close the GPX adapter with full standard-field evidence, extension round trips, independent application
interoperability, hostile-input proof, and reconciled public capability claims.

## Context

Complete code paths are not sufficient for externally recognizable format completeness. GPX reader/writer
behavior must be checked against the official schema and independent producers/consumers.

## Scope

- Pin a local official GPX 1.1 schema/hash for test-time validation and inventory every standard element,
  attribute, simple type, order/cardinality rule, extension point, and deliberate exclusion.
- Add official-schema-derived and independently produced files plus independent consumer observations for
  metadata, waypoints, routes, tracks, dimensions, quality fields, and unknown/typed extensions.
- Add deterministic read-write-read semantics, schema validation, malformed/mutated corpus, XML/extension bombs,
  cancellation, cleanup, source-query, native, publication, offline, and API compatibility evidence.
- Reconcile `CAPABILITIES.md`, package/module/root Javadocs, README/support tables, examples, diagnostics, limits,
  task outcomes, and the no-built-in-vendor-semantics boundary.
- Record any interoperability normalization or application quirk without weakening schema-valid canonical output.

## Out of scope

- GPX 1.0, vendor-specific semantics, live GPS/device protocols, routing/navigation, and general XML/XSD support.

## Acceptance criteria

- Every GPX 1.1 standard field/type/order/cardinality has passing reader and writer evidence.
- Multiple independent applications exchange generated and fixture documents without unexplained semantic loss.
- No undocumented accepted construct, discarded standard value, ambient dependency, or unbounded work path remains.

## Required tests

- Complete schema inventory, independent corpus/consumer, deterministic/schema/semantic round-trip, and fuzz/mutation tests.
- All parser/model/source/writer/extension limits, failure/cancellation/cleanup, native/publication/offline/API/docs inventories.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, approved schema/corpus/native/publication/
offline lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the schema/hash, corpus licensing, independent application observations,
normalization record, deliberate exclusions, and exact public support wording.
