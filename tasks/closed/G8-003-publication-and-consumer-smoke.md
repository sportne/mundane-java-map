# G8-003 — Publication and consumer smoke

Status: Complete
Depends on: G8-002
Gate: G8
Type: AFK

## Goal

Stage complete Level 1 artifacts and build/run a clean Java 21 downstream consumer using only those
Maven artifacts.

## Context

G0 introduced baseline `publicationDryRun` staging for API/core/AWT. This task repairs its actual
parallel clean/publish ordering, expands it to the two working format modules, validates release
metadata/content, and introduces `consumerSmoke` for downstream evidence.

## Scope

- Root/build-logic staging graph and pure Maven-layout/artifact verifier
- Publication configuration for API, core, AWT, shapefile, and image I/O
- Exact POM/module metadata, binary/source/Javadoc JAR, license, checksum, reproducibility, dependency,
  and prohibited-content assertions
- A checked-in standalone `consumer-smoke/` template copied beneath `build/` for one clean Java 21
  child build, plus the new root `consumerSmoke` lane
- `DESIGN.md`, G8 design/task/index/roadmap release-contract corrections

## Out of scope

- Release-version selection/go-no-go, remote upload, signing/credentials, tags, or releases
- Consuming source projects through composite/project dependencies
- Level 2 modules or examples/native/performance test publication
- A BOM/umbrella artifact, release archive, Maven CLI test, public resource loader, or publication of
  native/corpus/example/test assets

## Acceptance criteria

- One explicit five-entry release contract pins `io.github.mundanej` coordinates and exact API/runtime
  dependency sets: API none; core API; AWT API+core; each format API plus runtime core. No external,
  range, classifier, repository, project-path, or duplicate dependency is present.
- Every actual `publishMavenJavaPublicationToReleaseDryRunRepository` task depends directly on the
  staging clean and the five shared-repository write actions are serialized in inventory order before
  validation. Parallel artifact generation remains allowed; two consecutive runs cannot leave stale
  or partial authoritative output.
- `publicationDryRun` stages exactly five coordinates at the selected snapshot or release version with
  valid POM/module metadata, binary/sources/Javadocs, reproducible archives, and verified SHA-256/
  SHA-512 sidecars. Each unique-snapshot coordinate uses one internally coherent metadata-resolved
  timestamp/build across its five primary artifacts; different coordinates need not share it.
- Binary/source/Javadoc JARs contain exact root `META-INF/LICENSE`. Artifacts exclude native resources,
  corpus/test fixtures, examples/support classes, service descriptors, unsafe/duplicate ZIP entries,
  build paths, credentials, repositories, and unexpected packages/dependencies.
- The consumer is a separate build with repositories restricted to the staged local Maven directory;
  it has no project/composite substitution and succeeds with external repositories unavailable.
- The copied consumer directly depends only on AWT, shapefile, and image artifacts; resolution yields
  exactly the five staged external dependency components, no dependency project component, and no
  other external component. Its own root project is not misclassified as a dependency. It compiles/
  runs with Java 21 under a fresh Gradle home, offline/no-daemon/no-cache settings, and staged artifacts only.
- The consumer constructs explicit CRS/symbol/decoder registries, renders an in-memory vector symbol,
  generates and queries a tiny point shapefile, generates/reads PNG and JPEG, verifies one exact
  malformed-SHP diagnostic, and closes every view/cursor/source. No fixture is published or copied.
- Runtime assertions prove dependencies/resources are resolvable from artifacts, diagnostics are
  usable, and source/cursor/raster lifecycles close correctly.
- `consumerSmoke` depends on validated clean staging, first proves the fixed missing-repository
  diagnostic, proves a wrong existing repository with a second fresh home, then runs the valid
  consumer with a third fresh home, asserts one final success marker,
  and remains separate from `qualityGate`.
- Validator mutation controls fail with stable build tags for missing/extra artifacts, classifiers,
  dependencies, license, metadata, checksum, unsafe content, stale version, or repository.

## Required tests

- Pure verifier and task-graph functional tests for snapshot/release layouts, every published module,
  classifier/POM/module/checksum/license/prohibited-content mutation, parallel ordering, and repeat run.
- Consumer settings failure plus compile/run assertions using staged artifacts only on Java 21.
- An offline clean-cache run proving no undeclared repository or workspace dependency is required.
- Runtime valid/malformed vector/shapefile/PNG/JPEG/lifecycle assertions and cleanup.

## Validation

```bash
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The template is tiny, has no wrapper or external runtime dependency, and is not included in the main
settings/project inventory. `publicationDryRun consumerSmoke` names one combined release lane;
`consumerSmoke` is not part of the normal gate.

Implementation evidence covers both unique-snapshot and literal release layouts. The release
contract drives real-task ordering, POM/module/archive verification, mutation controls, the
reproducible artifact manifest, and exact downstream component assertions. The standalone consumer
uses three fresh homes and no workspace substitution or external repository. G8-001 remains Proposed:
this task completes the independently executable publication branch but does not claim the pending
Linux Native Image CI checkpoint or Level 1 release readiness.
