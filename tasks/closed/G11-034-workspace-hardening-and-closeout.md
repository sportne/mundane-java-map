# G11-034 — Workspace hardening and closeout

Status: Complete
Depends on: G11-033
Gate: G11
Type: HITL

## Goal

Close the workspace capability with exhaustive hostile-input evidence, public documentation,
staged-consumer verification, and a representative Linux Native Image read/write/open path.

## Context

G11-033 completes the local workflow. G11-003 requires strict no-leak diagnostics, bounded security
evidence, public lifecycle guidance, publication, and native verification before support claims.

## Scope

Complete malformed/hostile XML, allocation, filesystem mutation, cancellation, and cleanup tests;
finish public Javadocs and architecture rules; publish/stage `mundane-map-workspace`; extend the clean
offline consumer and shared native executable with a resource-controlled workspace scenario.

## Out of scope

New workspace versions, migration, network sources, credentials, Java serialization, reflection or
discovery, additional format dependencies, Windows/macOS Native Image claims, and release publication.

## Acceptance criteria

- Hostile bytes/XML/paths/counts and injected I/O/source failures produce only the exact bounded
  problems and never leak document text, paths, provider messages, credentials, or partial sessions.
- Javadocs cover grammar, limits, threat boundary, opener trust, ownership, cancellation, atomic write,
  and explicit exclusions.
- Publication and a clean offline Java 21 consumer read, write, reopen, and close a staged workspace
  using explicit fake or existing format openers only.
- The representative read/write/open/close success and one stable failure succeed in the required
  Linux Native Image lane before a workspace-native claim is recorded.

## Required tests

Deterministic hostile/mutation/fault tests, Javadoc/doclint, architecture, publication metadata,
offline consumer, JVM/native success/failure/cleanup, and support-statement review.

## Validation

```bash
./gradlew :modules:mundane-map-workspace:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 workspace security and Linux Native Image closeout**. The maintainer reviews
the threat-boundary evidence and exact Linux/support statement before the capability is closed.
