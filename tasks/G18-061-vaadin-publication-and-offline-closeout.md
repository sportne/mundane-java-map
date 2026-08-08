# G18-061 — Vaadin publication and offline closeout

Status: Proposed
Depends on: G18-060
Gate: G18
Type: HITL

## Goal

Publish and consume the Vaadin adapter from staged artifacts, prove reproducible offline Maven and
frontend builds, finish documentation/support wording, and close G18 without weakening existing
JDK-only or Native Image claims.

## Context

The component JAR contains Java classes and bundled frontend source consumed by Vaadin's build. The
ordinary publication and offline lanes must verify both surfaces and the exact open dependency
inventory before downstream usability can be claimed.

## Scope

- Add the adapter to publication staging with exact POM/module metadata, sources, Javadocs, license,
  checksums, public-surface, and frontend-resource inventories.
- Extend the isolated offline repository/build process for approved Maven and frontend inputs with
  no network fallback.
- Add a standalone Java 21 Vaadin consumer that resolves only staged mundane-map artifacts, builds
  the frontend, starts on loopback, and completes a minimal component/browser smoke.
- Complete README/DESIGN/ROADMAP/task traceability, dependency/license notices, compatibility and
  support statements, upgrade policy, and G18 simplicity review.

## Out of scope

Remote release publication, a BOM, npm publication, Vaadin Directory listing, Native Image support,
Windows/macOS server certification, cloud/container orchestration, commercial support, or broad
browser compatibility beyond recorded evidence.

## Acceptance criteria

- The staged adapter contains only approved public classes/resources and records the exact minimum
  Vaadin dependency graph without commercial artifacts or undeclared frontend packages.
- Sources/Javadocs match the binary surface; public Vaadin-facing APIs have complete Javadocs and no
  AWT, format-specific, Spring, private protocol, or browser implementation leakage.
- A clean isolated build resolves Maven and frontend inputs only from the prepared offline sources,
  builds the example/consumer production frontend, and detects any network fallback or inventory
  drift.
- The standalone consumer embeds `MundaneMap`, displays a feature, receives a settled viewport and
  selection event, and closes cleanly using staged project artifacts only.
- Documentation distinguishes the reusable adapter from the Spring example and states exact Java,
  Vaadin, browser, Node/build, platform, offline, security, map-data, commercial-exclusion, and
  non-native support boundaries.
- The closeout review finds no need for a public scene protocol, generic web renderer SPI,
  third-party browser map engine, server image renderer, or change to existing API/core/I/O/AWT
  dependency direction.

## Required tests

Artifact/POM/resource/license inventories; staged standalone consumer and minimal browser smoke;
isolated offline Maven/frontend build; public Javadocs/signature checks; architecture/dependency
scans; documentation consistency; complete normal and specialized G18 lanes.

## Validation

```bash
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew offlineRepositoryVerification --console=plain
./gradlew vaadinBrowserTest --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **Vaadin adapter dependency/license, staged consumer, offline frontend, browser
support wording, and G18 simplicity closeout approval**.

