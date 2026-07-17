# G9-008 — Native Image DTED smoke

Status: Complete
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

Implementation evidence (2026-07-17, worktree based on `3a8ea27`): the existing executable now runs
one package-private DTED scenario after the unchanged Level 1 scenario. The shared JVM/native path
materializes the approved `gdal-zone-v-l0-complete` bytes as `s81.dt0`, derives the 8,761-byte
truncation, verifies exact metadata and nearest/bilinear queries, paints tolerant unshaded/default-
hillshade views, checks the stable malformed-length diagnostic, and closes/deletes owned resources.
The native support runtime now has exactly six explicit project dependencies and 13 literal resource
entries. The copied 8,762-byte BSD-3-Clause synthetic corpus resource has SHA-256
`9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802`; its producer remains the
approved GDAL 3.13.0 acquisition recipe, not a runtime dependency. G9-007 retained eager access.

The focused JVM/native-support and architecture checks passed locally. An explicit GraalVM CE
21.0.2+13.1 Linux amd64 run built the 46.32-MiB image successfully in 26.6 seconds, including the
DTED module and resource, but the historical Level 1 scenario failed before reaching DTED at
`SunFontManager.initIDs` with missing JNI class `sun/font/TrueTypeFont`. No metadata workaround was
added because that pre-existing Java2D/font issue is outside this task's approved design. At that
point status was Blocked until the required clean Ubuntu 24.04 Linux x86_64 Java 21 CI lane ran the
executable through the sentinel and the preapproved maintainer checkpoint could be recorded. No
Level 1/2, Windows, macOS, Linux AArch64, or general DTED Native Image claim is made.

### Completion checkpoint — 2026-07-17

- Candidate revision: `a5d10791d6cf811b438cb72504ff8b00b2ab8d75`.
- Authoritative evidence: [Native Image run](https://github.com/sportne/mundane-java-map/actions/runs/29578220777)
  and [native-smoke job](https://github.com/sportne/mundane-java-map/actions/runs/29578220777/job/87877476971).
- Runner and tools: GitHub-hosted Ubuntu 24.04.4 LTS x86-64, runner image `ubuntu-24.04`
  version `20260714.240`, Oracle GraalVM Java `21.0.11+9.1`, Native Image
  `native-image 21.0.11 2026-04-21`, and JVMCI `23.1-b92`.
- Command and result: `./gradlew nativeSmoke --console=plain` built and ran the no-fallback
  executable and printed `mundane-map native smoke: OK`.
- Access policy: G9-007 retained the eager source after its largest-standard-cell memory and read
  evidence; no windowed-source follow-up is required.
- Fixture authority: the literal
  `io/github/mundanej/map/nativeimage/dted/zone-v-l0-smoke.dt0` resource is the approved
  8,762-byte BSD-3-Clause synthetic GDAL 3.13.0 fixture with SHA-256
  `9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802`.
- The six-dependency, 13-resource executable passed exact DTED metadata, nearest/bilinear query,
  unshaded colorization/render, material default-hillshade change, malformed-length diagnostic, and
  deterministic cleanup assertions before the sentinel.
- Approval: the maintainer pre-approved every qualifying HITL task in this execution sequence. The
  exact-SHA evidence satisfies the named **G9 native DTED approval**.
- Approved support wording: a representative DTED Level 0 read, query, colorize, hillshade, render,
  and malformed-length path is verified with GraalVM Native Image Java 21 on the recorded Ubuntu
  24.04 Linux x86-64 lane. DTED Levels 1 and 2 are JVM/corpus-verified, not Native Image-verified.
  Windows, macOS, Linux AArch64, other environments, and general terrain workloads remain unclaimed.
