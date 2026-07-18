# G8-005 — Lean publication and consumer verification

Status: Complete
Depends on: G8-003
Gate: G8
Type: AFK

## Goal

Keep a usable staged Maven repository and clean downstream consumer while replacing the custom Maven/
Gradle metadata reimplementation and verifier-self-test framework with targeted project invariants.

## Context

G8-003 requires staged POM/module/binary/sources/Javadoc artifacts, reproducibility evidence, and a
Java 21 consumer. The root build currently spends more than 1,500 lines parsing every generated
metadata detail, generating dozens of hostile repository mutations, running repeated publication
child builds, and executing two negative consumer builds on every smoke.

## Scope

Root/build-logic publication tasks, consumer smoke, G8 design, task index, and roadmap.

## Out of scope

Remote publication, signing, credentials, changing coordinates or POM semantics, and weakening the
published module inventory.

## Acceptance criteria

- `publicationDryRun` recreates the local Maven repository and verifies exact coordinates, primary
  artifacts, SHA-256 checksums, license/package roots, and project dependency scopes.
- Generated Gradle metadata is validated through successful downstream resolution rather than an
  independent schema reimplementation.
- `consumerSmoke` performs one fresh Java 21 offline build/run using staged artifacts only.
- Verifier mutation and repeated functional child-build frameworks are removed from normal Gradle
  execution; deterministic manifest comparison remains available at release readiness.
- Surviving custom task implementations live in `build-logic`; the root build stays declarative.

## Required tests

Build-logic task validation, publication dry run, clean positive consumer, and exact artifact
manifest assertions.

## Validation

```bash
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The staged Maven repository remains an explicit deliverable. The consumer is the authoritative
resolution test for generated POM and Gradle module metadata.
