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

Append one package-private DTED scenario to the existing native executable. Copy the approved
G9-006 zone-V Level 0 cell into one literal native resource, materialize a bounded fixed workspace,
open/query it through public APIs, render unshaded and default-hillshade elevation views, derive one
truncated diagnostic file, and close/delete everything deterministically.

## Out of scope

Embedding the full corpus or its manifest/licenses, Level 1/2 native coverage claims, native
benchmarks, Windows/macOS claims without evidence, another executable/framework, reflection or broad
resource configuration, implicit discovery, JNI, and custom native parsing.

## Acceptance criteria

- `nativeSmoke` retains one executable/sentinel and executes the DTED scenario after the historical
  Level 1 scenarios on the required Linux x86_64 Java 21 Native Image lane.
- One 8,762-byte literal resource has the approved corpus SHA/provenance; exact metadata, center and
  bilinear queries, bounded tolerant unshaded rendering, and material default-hillshade change pass on
  the shared JVM/native assertion path.
- A derived 8,761-byte file produces the exact `DTED_FILE_LENGTH_MISMATCH` report on JVM and Native
  Image paths, and all owned sources/views/files/directories close or delete in fixed order.
- Resource configuration has exactly 13 literal entries and the support project has one explicit DTED
  dependency; no discovery, broad metadata, external type, or prohibited native mechanism appears.
- **G9 native DTED approval** records the authoritative CI evidence, G9-007 access decision, fixture
  authority, exact outcomes, cleanup, and representative-Level-0-only support wording.

## Required tests

Repeated JVM whole-smoke parity and negative controls; exact resource/config/hash inventory; workspace
length/hash/derivative/cleanup failures; metadata/query/render/hillshade/diagnostic assertions and EDT
ownership; Linux Native Image success; architecture/workflow checks for dependencies and every
prohibited mechanism.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G9 native DTED approval**. The maintainer must inspect the successful clean Linux
Native Image build/run and approve the exact representative Level 0 claim. Level 1/2, Windows, macOS,
Linux AArch64, and other environments remain unclaimed until separately observed. If G9-007 creates a
windowed-source task, reopen/amend/re-review this design and closeout, add that task as a dependency,
and smoke the accepted path before executing this task.
The 13-resource and six-dependency counts are the task-required G8-baseline subset. If another
approved append-only Level 2 native scenario lands first, retain its entries and update the one
complete authoritative manifest; shared native files make those tasks dependency-parallel but not
path-safe.
