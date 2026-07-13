# G11-004 — Optional adapter boundaries

Status: Proposed
Depends on: G10-003, G10-004, G10-007
Gate: G11
Type: HITL

## Goal

Decide whether and how JTS, PROJ, SQLite, and GDAL integrations can exist as isolated optional adapters
without weakening the core API, Level 1 runtime, or Native Image architecture.

## Context

G10-003 selects a strict pure-Java GeoTIFF profile and defers GDAL, G10-004 evaluates SQLite-backed
formats, and G10-007 evaluates projection needs. Optional geometry and native integrations may reduce
later complexity, but their dependencies, deployment, licenses, and type systems must remain outside
`mundane-map-api`.

## Scope

For each candidate, record the demonstrated use case, capability boundary, module name, dependency and
license policy, conversion ownership, explicit registry/factory, error/diagnostic translation,
resource lifecycle, supported platforms, version policy, and Native Image expectation. Decide accept,
defer, or reject independently and create separate evidence/implementation tasks only for accepted
adapters.

## Out of scope

Production adapter modules, mandatory external dependencies, public APIs exposing external types,
automatic classpath/plugin discovery, bundled native binaries without a packaging decision, and a
custom native performance library without benchmark evidence and a separate approval.

## Acceptance criteria

- A maintainer records an independent accept/defer/reject decision for JTS, PROJ, SQLite, and GDAL
  based on a concrete capability rather than general convenience.
- Accepted adapters depend inward on format-neutral contracts; conversion types and third-party
  exceptions never leak into `mundane-map-api`.
- Registration is explicit, diagnostics are translated to stable library codes, and ownership/cleanup
  is documented.
- Native/library packaging, licensing, platform support, security updates, and Native Image limitations
  are explicit before implementation tasks are approved.
- Each accepted adapter receives a working vertical-slice task; deferred adapters create no module.

## Required tests

No production tests. For accepted adapters, define later boundary/type-leak architecture tests,
conversion parity, missing-library/version, lifecycle, diagnostic, platform, and Native Image tests.

## Validation

```bash
./gradlew :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves each adapter decision, dependency/license terms, and supported
platform claim independently. JNI is permitted only inside an approved optional adapter, never Level 1.
