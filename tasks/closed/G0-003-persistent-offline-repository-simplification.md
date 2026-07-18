# G0-003 — Persistent offline repository simplification

Status: Complete
Depends on: G0-002
Gate: G0
Type: AFK

## Goal

Produce a reusable Maven-layout repository containing the build's resolved plugin and dependency
artifacts, then prove a clean Gradle home can run the complete quality gate from that repository
without the current double-quality-build provisioning cycle.

## Context

G0-001 established `map.offlineRepo` and an isolated offline proof. The current proof first executes
an online no-cache `qualityGate`, copies the entire Gradle artifact cache into a temporary Maven
repository with synthetic metadata, and then executes another quality gate. It is correct but slow,
ephemeral, and larger than the capability requires.

## Scope

Root/build-logic Gradle configuration, build-configuration tests, offline-repository CI, G0 design,
task index, and roadmap.

## Out of scope

Production code, runtime dependencies, remote artifact publication, repository credentials, and
changes to Java 21 or module boundaries.

## Acceptance criteria

- `assembleOfflineRepository` writes a persistent Maven-layout repository and SHA-256 manifest under
  `build/offline-repository`.
- Provisioning resolves the exact build configurations without executing the project's tests or a
  first `qualityGate`.
- `offlineRepositoryVerification` consumes that repository from copied sources and a clean Gradle
  home with public repositories disabled and `--offline`.
- Missing-coordinate and repository-policy diagnostics remain covered.
- The implementation does not walk unrelated cache coordinates or treat cache-path SHA-1 values as
  a project trust manifest.

## Required tests

Build-logic task validation and repository-policy tests; repository manifest assertions; one full
isolated offline quality build; configuration-cache-compatible normal checks.

## Validation

```bash
./gradlew assembleOfflineRepository --console=plain
./gradlew offlineRepositoryVerification --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The repository is an intentional reusable deliverable, not merely a temporary test fixture. Keep
the implementation JDK/Gradle-only and proportionate to the exact resolved graph.
