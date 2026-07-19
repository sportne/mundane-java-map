# G11-024 — Styling and label closeout

Status: Proposed
Depends on: G11-023
Gate: G11
Type: HITL

## Goal

Close the portrayal and point-label capability with public documentation, measured simplicity,
staged-consumer evidence, and a representative Linux Native Image path.

## Context

G11-023 delivers the complete runnable JVM behavior. G11-002 requires profiling before any placement
index/cache and prohibits broad styling or cross-platform font claims without evidence.

## Scope

Complete public Javadocs and architecture rules; add representative portrayal/placement scenarios to
`performanceEvidence`, the shared native executable, publication staging, and the clean offline Java
21 consumer; document accepted performance and platform boundaries.

## Out of scope

Placement indexes or caches without demonstrated need, Windows/macOS Native Image claims, font or
glyph identity claims, stylesheet/expression features, additional label geometry, and release
publication.

## Acceptance criteria

- Javadocs describe selector equality/fallback, label omission, limits, failures, ownership, and
  interaction behavior without promising a styling language.
- Repeatable evidence records bounded real-stack placement cost and either retains the linear
  algorithm or creates a separate evidence-backed optimization decision.
- Staged API/core/AWT artifacts support one clean offline consumer portrayal/placement scenario.
- The representative resource-free portrayal and placement path succeeds under the required Linux
  Native Image lane before a native claim is recorded.

## Required tests

Javadoc/doclint, architecture, performance evidence, publication metadata, clean offline consumer,
JVM/native success and diagnostic scenarios, and support-statement review.

## Validation

```bash
./gradlew performanceEvidence --console=plain
./gradlew nativeSmoke --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 styling and label performance/native closeout**. The maintainer approves the
measured linear-placement disposition and exact Linux Native Image/support statement.
