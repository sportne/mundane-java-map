# G19-098 — Canonical SE 1.1 and SLD 1.1 writer

Status: Proposed
Depends on: G19-095, G19-096, G19-097
Gate: G19
Type: AFK

## Goal

Write deterministic standalone SE 1.1 styles and SLD 1.1 wrapper documents from the losslessly
representable neutral style profile.

## Context

The module is read-only. A canonical writer enables portable generated styles, stable review/versioning,
interchange with OGC tools, and read-modify-write workflows without turning the project into a WMS server.

## Scope

- Add an immutable writer/options builder selecting standalone SE feature/coverage output or an SLD
  named/user-layer wrapper, metadata policy, resource policy, encoding, and bounded output sink.
- Preflight the complete style/expression/symbol/resource graph for strict lossless representability;
  reject unsupported neutral behavior with stable path-specific diagnostics and no partial bytes.
- Emit deterministic namespaces/prefixes, versions/schema locations, IDs, order, numeric/text/color/UOM
  lexical forms, whitespace, character encoding, and resource identifiers.
- Serialize the approved FE 1.1 expressions/predicates and all completed SE symbolizers without vendor options.
- Support explicit inline/catalog-resource policies, transactional filesystem replacement, cancellation,
  rollback, cleanup, exact byte limits, and byte-identical repeated output.

## Out of scope

- WMS requests/deployment, remote uploads, vendor extensions, lossy approximation, arbitrary source-XML preservation,
  and preserving incidental input whitespace/prefix choices.

## Acceptance criteria

- Every representable neutral style emits valid deterministic SE/SLD and reads back to equal compiled semantics.
- Non-representable input fails during preflight without changing the target or leaking resource/source details.
- Cancellation/failure closes staged resources and preserves prior files; identical inputs/options are byte-identical.

## Required tests

- Golden SE/SLD documents for every expression/predicate/symbolizer/resource mode and deterministic bytes.
- Representability rejection, round trips, Unicode/numeric/resource limits, cancellation, cleanup, and atomic rollback.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, schema/round-trip/corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

The writer's value is portable, reviewable style interchange; it deliberately does not implement WMS style management.
