# G10-057 — Native Image KML closeout

Status: Proposed
Depends on: G10-053, G10-056
Gate: G10
Type: HITL

## Goal

Prove the bounded static KML path on Linux Native Image and close the shared GPX/KML security evidence
without merging their modules.

## Context

G10-053 records GPX native evidence and G10-056 closes KML JVM behavior. G10-005 requires explicit
native resources, direct JDK StAX construction, stable success/warning/malformed outcomes, and one
combined review of the independent security boundaries.

## Scope

Extend the exact native executable/resource inventory with a tiny KML fixture. Exercise one supported
query/render path, one ignored-presentation or altitude warning, one stable malformed result, and
cleanup. Audit both modules' parser settings, external-access canaries, architecture boundaries, and
prohibited mechanisms, then record the bounded Linux claims.

## Out of scope

Windows/macOS claims, a general XML compatibility statement, shared GPX/KML parsing code, dynamic
resources, implicit Native Image metadata discovery, and performance claims.

## Acceptance criteria

- `nativeSmoke` opens, queries, and renders the explicitly registered KML fixture and retains one
  approved warning plus one exact malformed diagnostic.
- Both GPX and KML native paths use directly constructed hardened JDK StAX and explicit resources with
  no reflection, classpath/service scanning, internal API, or external-resource resolution.
- Source/cursor cleanup and native results match the JVM contracts.
- The review records exact Linux GPX/KML claims and confirms the two production modules remain
  independent.

## Required tests

Native KML valid query/render, warning, malformed diagnostic, cleanup, explicit-resource inventory,
combined GPX/KML security-canary, and architecture tests.

## Validation

```bash
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GPX/KML Linux Native Image and security closeout**. The maintainer must approve
the executable evidence, external-access canaries, and bounded platform statements.
