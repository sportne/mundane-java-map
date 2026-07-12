# G10-004 — SQLite container adapter profiles

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Define separate, bounded read-only GeoPackage and MBTiles profiles plus an isolated SQLite dependency
boundary before either format is implemented.

## Context

Both formats use SQLite but expose different map semantics. The Level 1 runtime is JDK-only; a JDBC or
native SQLite dependency must live in an optional adapter and its types must not leak into map APIs.

## Scope

Decide supported GeoPackage feature/tile table and CRS behaviors, supported MBTiles schema/version,
XYZ/TMS row handling, image formats, metadata, limits, diagnostics, connection ownership, cancellation,
read-only enforcement, and Native Image expectations. Define module boundaries and separate vertical
implementation task sequences for GeoPackage and MBTiles.

## Out of scope

Production modules, database writing/migrations, arbitrary SQL, bundled native libraries, encrypted
databases, remote databases, pooling frameworks, and a shared public API exposing JDBC or driver types.

## Acceptance criteria

- A maintainer approves independent supported-profile matrices for GeoPackage and MBTiles.
- The adapter boundary uses existing format-neutral contracts, explicit construction/registration, and
  stable diagnostics while isolating all SQLite/JDBC implementation types.
- Driver/license/platform/Native Image tradeoffs and read-only security controls are recorded.
- Separate follow-up tasks deliver working format behavior before adding each `mundane-map-io-*`
  module; no generic empty SQLite module is planned.

## Required tests

No production tests. Define later fixtures for valid minimal databases, schema/version mismatches,
hostile sizes, malformed contents, query/render behavior, resource closure, and supported native paths.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves both format profiles, the SQLite driver strategy, licensing,
and module boundaries before implementation tasks start.
