# G8-003 — Publication and consumer smoke

Status: Proposed
Depends on: G8-001, G8-002
Gate: G8
Type: AFK

## Goal

Stage complete Level 1 artifacts and build/run a clean Java 21 downstream consumer using only those
Maven artifacts.

## Context

The existing `publicationDryRun` stages API/core/AWT modules. Level 1 adds format modules and public
resources that must be validated outside Gradle project dependencies before release readiness.

## Scope

- Publication configuration for all Level 1 public modules
- POM, module metadata, binary/source/Javadoc JAR, checksum, and resource assertions
- A small independent Java 21 consumer fixture and new root `consumerSmoke` lane
- Root publication task ordering and offline staged-repository isolation

## Out of scope

- Uploading to a remote repository, signing with maintainer credentials, or publishing snapshots
- Consuming source projects through composite/project dependencies
- Level 2 modules or examples/native/performance test publication

## Acceptance criteria

- `publicationDryRun` stages API, core, AWT, shapefile, and image I/O artifacts at the intended
  coordinates/version with binary, sources, Javadocs, Gradle metadata, and valid POM dependency
  scopes.
- Published artifacts contain required explicit symbol/raster resources and exclude test fixtures,
  corpus data, examples, build paths, credentials, and duplicate dependencies.
- The consumer is a separate build with repositories restricted to the staged local Maven directory;
  it has no project/composite substitution and succeeds with external repositories unavailable.
- The consumer compiles and runs on Java 21, constructs explicit registries, renders an in-memory
  symbolized feature, and opens/queries representative shapefile and PNG/JPEG resources through
  public APIs.
- Runtime assertions prove dependencies/resources are resolvable from artifacts, diagnostics are
  usable, and source/cursor/raster lifecycles close correctly.
- The new `consumerSmoke` task stages artifacts first or fails clearly when staging is absent, uses a
  clean consumer Gradle user home/cache policy, and remains separate from `qualityGate`.
- Artifact/POM checks fail deterministically for a missing classifier, leaked project dependency,
  wrong version, absent resource, or unexpected repository.

## Required tests

- Publication structure tests for every published module, classifier, POM scope, coordinate, and
  prohibited content.
- Consumer compile/run assertions using staged artifacts only on Java 21.
- An offline clean-cache run proving no undeclared repository or workspace dependency is required.
- Task-ordering tests for clean staging followed by consumer execution.

## Validation

```bash
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The consumer should be tiny and checked in, with no external runtime dependency. Do not make
`consumerSmoke` part of the normal gate; it is the publication/consumer lane introduced here.
