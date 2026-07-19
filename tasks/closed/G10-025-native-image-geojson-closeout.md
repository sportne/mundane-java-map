# G10-025 — Native Image GeoJSON closeout

Status: Complete
Depends on: G10-024
Gate: G10
Type: HITL

## Goal

Prove the exact bounded GeoJSON read/write path under Linux Native Image or retain an explicit
JVM-only adapter classification.

## Context

G10-024 completes JVM functionality, packaging, fixtures, and consumer evidence. Jackson's shaded
content and service descriptor require an executable native audit rather than an inferred claim.

## Scope

Extend the existing native executable with direct Jackson construction, valid read/query/render,
deterministic write/reopen, one malformed diagnostic, resource cleanup, and service/shaded-content
audit. Record the bounded Linux support outcome.

## Out of scope

Windows/macOS Native Image claims, automatic metadata discovery, reflection configuration generated
from execution, alternate parsers, or weakening architecture rules.

## Acceptance criteria

- `nativeSmoke` exercises the real parser, generator, source, writer, and renderer path.
- No service discovery, reflection metadata repository, or implicit resource scan is required.
- The review records either the exact Linux claim or a JVM-only disposition with evidence.

## Required tests

Native valid read/render, write/reopen, malformed diagnostic, cleanup, and architecture inventory
tests.

## Validation

```bash
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoJSON Native Image claim review**. The maintainer approved this checkpoint
in advance for the requested implementation sequence.
