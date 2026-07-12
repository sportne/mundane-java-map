# G9-008 — Native Image DTED smoke

Status: Proposed
Depends on: G9-002, G9-005, G9-007
Gate: G9
Type: HITL

## Goal

Prove that DTED reading, elevation querying, colorization, and offscreen rendering work in a real
GraalVM Native Image executable.

## Context

Native Image compatibility is an architectural requirement. Earlier G9 tasks establish the model,
reader, hardening, query policy, rendering, corpus, and evidence needed for a representative native
path.

## Scope

Extend the existing native-smoke application and Gradle wiring with an explicitly located small DTED
fixture. Open it, verify metadata and a known query, render a color-ramped elevation layer with optional
hillshade, verify output invariants, exercise one stable malformed-input diagnostic, and close all
resources.

## Out of scope

Embedding the full corpus, native benchmarks, Windows/macOS Native Image claims without evidence,
reflection configuration for implicit discovery, JNI, and custom native parsing.

## Acceptance criteria

- `nativeSmoke` builds and executes the DTED path on the supported Linux Native Image lane.
- The executable validates known metadata/query values and non-empty bounded offscreen render output.
- One malformed fixture produces the same stable diagnostic code on JVM and Native Image paths.
- Required resources are declared explicitly; no classpath scanning, reflection, or implicit plugin
  discovery is introduced.
- The maintainer reviews the Native Image log and resulting support claim before this task is Complete.

## Required tests

JVM parity test for the smoke scenario, Native Image success-path smoke, malformed-input diagnostic
smoke, explicit resource-presence check, and architecture checks for prohibited mechanisms.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: a maintainer must inspect the Linux Native Image build/run evidence and approve the
precise platform claim. Windows and macOS remain unclaimed until separately observed.
