# G19-001 — Ordinate-aware geometry and heterogeneous collections

Status: Completed
Depends on: G18-061
Gate: G19
Type: AFK

## Goal

Extend the standards-neutral geometry contract so adapters can preserve empty geometries, Z and M
ordinates, and heterogeneous geometry collections without private side channels.

## Context

The sealed API currently models six non-empty homogeneous 2D families. That prevents faithful support
for valid Shapefile Z/M records, RFC 7946 collections, KML multi-geometry, and GeoPackage dimensional
or collection values.

## Scope

- Freeze dimensionality, emptiness, equality, envelope, iteration, and packed-storage semantics.
- Add immutable Z, M, and ZM coordinate storage and heterogeneous collections.
- Define deterministic down-projection policies for consumers that support XY only.
- Extend geometry limits, validation, diagnostics, and public Javadocs.
- Migrate built-in algorithms and adapters without weakening existing 2D behavior.

## Out of scope

- General solid/volume topology or an unbounded arbitrary-coordinate object graph.
- Silent loss of unsupported ordinates.

## Acceptance criteria

- Every supported dimensional model round-trips without loss through the public API.
- Empty and nested collection behavior is explicit, bounded, immutable, and exhaustively visited.
- Existing XY source and binary compatibility follows the project compatibility policy.
- Unsupported consumer conversions produce stable diagnostics rather than truncation.

## Required tests

- Construction, equality, envelope, visitor, limit, and hostile-depth tests for every new family.
- Cross-module fixtures for an empty, Z, M, ZM, and mixed collection value.
- Architecture tests proving packed primitive storage and forbidden-API rules.

## Validation

Run `./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

Freeze the model before format adapters implement their dependent cards.

Completed with additive packed dimensional sequences, typed empty values, bounded heterogeneous
collections, deterministic traversal, stable geometry diagnostics, and named x/y down-projection.
