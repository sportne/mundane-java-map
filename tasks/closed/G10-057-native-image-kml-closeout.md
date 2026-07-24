# G10-057 — Native Image KML closeout

Status: Complete
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

HITL checkpoint: **G10 GPX/KML Linux Native Image and security closeout**. The maintainer approved this
checkpoint through the advance HITL authorization for the selected execution sequence.

Completion evidence (2026-07-24): the exact 1,330-byte simplekml fixture at SHA-256
`32fc9de3e4cc1a09254f01a3b922a406b2237f79c3c6dc403ede3b5c7f37e2f2` is explicitly copied and
registered. The GraalVM CE 21.0.2 Linux executable opens, queries, and renders its three features,
retains `KML_ALTITUDE_IGNORED`, checks the exact `KML_XML_INVALID reason=syntax` malformed outcome,
and verifies deterministic cleanup. Architecture tests retain independent JDK-only/AWT-free GPX and
KML modules, literal resource inventories, and prohibited-mechanism checks. The native and ordinary
quality lanes pass; no Windows/macOS claim is made.
