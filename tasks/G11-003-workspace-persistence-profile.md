# G11-003 — Workspace persistence profile

Status: Proposed
Depends on: G8-004
Gate: G11
Type: HITL

## Goal

Define an explicit, versioned project/workspace persistence format that restores map configuration
without Java serialization or implicit resource discovery.

## Context

Level 1 has layers, sources, viewport, symbols, catalogs, CRS boundaries, and structured diagnostics.
Persistence must distinguish portable configuration from runtime resources and remain evolvable and
safe for untrusted input.

## Scope

Decide the first persisted state, schema/grammar and version marker, source reference rules, relative
path base, catalog/symbol references, unknown field/version behavior, migrations, missing resources,
atomic writes, limits, diagnostics, and explicit source/adapter registries. Define security and
portability expectations and decompose read, write, and round-trip slices after approval.

## Out of scope

Production persistence, Java serialization, serializing live cursors/windows/listeners, embedded
credentials, implicit adapter discovery, cloud synchronization, collaborative state, arbitrary object
graphs, and format-specific data duplication.

## Acceptance criteria

- A maintainer approves the versioned wire profile, first persisted fields, reference/path semantics,
  and compatibility policy.
- Unknown versions fail with a stable diagnostic; unknown optional fields and older supported versions
  follow documented deterministic behavior.
- Inputs have explicit byte/depth/count/string/allocation limits and cannot trigger implicit network,
  classpath, or credential access.
- Follow-up tasks provide tested read/write/round-trip behavior before any persistence module is added,
  including atomic-write failure and missing-source cases.

## Required tests

No production tests. Define later canonical round trips, version migration, unknown version/field,
path portability, missing adapter/resource, malformed/hostile input, atomic write, and Native Image
scenarios.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves the first persisted state, encoding, compatibility guarantees,
and source-reference security policy before implementation.
