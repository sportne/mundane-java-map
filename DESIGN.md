# Design

## Goals

- Provide a lightweight embeddable Java map component.
- Keep Level 1 production modules free of third-party runtime dependencies.
- Support explicit, deterministic APIs that work on the JVM and with GraalVM Native Image.
- Grow by tested vertical capabilities rather than speculative abstraction layers.
- Keep data formats behind format-neutral vector and raster source contracts as they are added.

## Non-goals

- A full desktop GIS.
- Runtime plugin discovery, classpath scanning, or reflection-based registration.
- Arbitrary CRS transformation, GeoTIFF, vector editing, or geometry overlay operations in the
  initial slice.
- Custom native libraries for performance without benchmark evidence.

## Dependency policy

Level 1 production modules use only the JDK and other `mundane-map` modules. Build and test tooling
may use JUnit, ArchUnit, JaCoCo, Checkstyle, SpotBugs, Error Prone, Spotless, and GraalVM Native
Build Tools. Future external integrations must live in optional adapter modules and must not leak
their types through `mundane-map-api`.

## Build and verification architecture

### Java baseline

Java 21 is the language, API, and bytecode baseline for every published module. The Java version
used to launch Gradle or tests is an execution environment, not a reason to change library bytecode.
Local and CI builds therefore compile with a Java 21 compiler toolchain and `--release 21`. Tests
default to a Java 21 launcher; a newer-JDK CI leg selects only the test launcher through a separate
`map.testJavaVersion` input so the same Java 21 classes are exercised on that runtime. The compile
baseline is not a matrix input: `map.javaRelease` is fixed and validated as 21, and any other value
fails configuration rather than raising the artifact baseline.

The root build owns the version and Java-baseline properties. Convention plugins apply the same
toolchain, encoding, lint, Error Prone, formatting, static-analysis, and coverage policy to every
checked Java project. Build and test dependencies stay off published runtime variants.

### Repository resolution

Repository selection is centralized in settings; individual projects do not add repositories.
There are two explicit modes:

- In normal mode, plugin resolution uses the Gradle Plugin Portal and Maven Central, while ordinary
  dependencies use Maven Central.
- When `map.offlineRepo` is set, its value must be an absolute normalized filesystem path or `file:`
  URI. The exact URI is propagated to the root and included `build-logic` settings, where that
  Maven-layout repository is the sole source for plugin and dependency resolution. Relative paths
  are rejected because the two settings directories have different bases. There is no fallback to
  a public repository.

The root and included build consume the same checked-in version catalog. An incomplete offline
repository fails through normal Gradle resolution with the missing coordinate and repository path;
the build does not obscure that evidence with a custom fallback or machine-specific cache lookup.
Offline verification uses a temporary, explicit Maven-layout fixture rather than a developer's
global Gradle cache. The verification harness creates an isolated temporary `GRADLE_USER_HOME`
containing only a checksum-verified, pre-provisioned Gradle wrapper distribution. It then runs with
Gradle offline mode, a locally installed Java 21 toolchain, and toolchain auto-download disabled.
Wrapper provisioning is an explicit harness precondition; it is not counted as Maven dependency
resolution and must complete before network isolation is asserted.

### Normal quality gate

The normal verification graph is intentionally small and mirrors ordinary CI:

```text
qualityGate
  +-- checkAll
  |     +-- each checked project: check
  |           +-- tests, Checkstyle, SpotBugs, Error Prone, and coverage verification
  +-- each checked project: spotlessCheck
  +-- each checked project: javadoc
```

Normal tests exclude slow, manual, and Native Image tags. Native, corpus, rendering-regression,
performance, and publication/consumer lanes remain independent so their environmental cost and
evidence are visible. A project is added to the checked-project list in the same change that adds
working behavior; there is no empty-module exemption from the gate.

CI runs the normal gate on Java 21 and at least one supported newer JDK, always targeting release
21. The two legs are compatibility evidence for one artifact baseline, not separate supported
language levels.

### Publication staging

`publicationDryRun` clears and recreates a Maven-layout repository under the root build directory.
For each public runtime module it stages the POM, Gradle module metadata, binary JAR, sources JAR,
and Javadoc JAR at the declared project version. The list of published projects is explicit; test,
native-smoke, architecture-test, performance, and example projects are never published.

Staging ends with a deterministic layout assertion generated from that explicit project list. For
the initial baseline it requires exactly the API, core, and AWT coordinates and their POM, module
metadata, binary, sources, and Javadoc artifacts; it rejects missing classifiers, stale versions,
and artifacts for internal projects. The assertion checks the staged repository structure and
metadata presence, while downstream dependency resolution and API use remain deferred to the
consumer smoke lane.

Staging performs no remote upload, signing, credential lookup, or package-registry access. It proves
artifact construction only; isolated downstream consumption is a separate release-hardening concern.
Future public modules join publication staging only when their first working vertical slice is
implemented and their artifact metadata is meaningful.

## Module boundaries

```text
mundane-map-api
      ^
      |
mundane-map-core
      ^
      |
mundane-map-awt
```

- `api` depends on `java.base` only.
- `core` depends on `api` and `java.base` only.
- `awt` owns `java.desktop`, Swing, Java2D, pointer wiring, and render caches.
- I/O modules may depend on `api` and `core`, but never on `awt`.
- Test, native, architecture, and example modules are not published.

## Geometry and features

The public geometry model is immutable. Coordinate sequences use packed primitive storage and make
defensive copies at API boundaries. Features combine geometry with a stable ID, display name,
attributes, and style. Geometry remains separate from future symbol/rendering extensions.

## Projection pipeline

```text
source coordinate -> map projection -> projected world coordinate -> viewport -> screen pixel
```

`Projection` owns forward and inverse projection. `MapViewport` owns only projected-world to screen
math. The initial concrete projection is Web Mercator; source coordinates are longitude/latitude in
degrees.

## Rendering and interaction

`MapView` is a Swing `JComponent`. It renders through Java2D, owns its viewport state, and follows
the Swing event-dispatch-thread rule. Pointer listeners receive both screen coordinates and inverse-
projected map coordinates. Render registration will remain explicit when custom graphics arrive.

## Native Image

Native-targeted code avoids reflection, runtime scanning, dynamic proxies, Java serialization,
`Unsafe`, internal JDK APIs, and implicit resource discovery. A real offscreen render is the first
native smoke path; metadata workarounds require a recorded design decision.

## Decisions

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-07-11 | Use `mundane-java-map` and `io.github.mundanej.map`. | Align with the existing MundaneJ family. |
| 2026-07-11 | Use Java 21 and Gradle 9.5.1 Groovy DSL. | Match the existing project baseline. |
| 2026-07-11 | Use BSD 3-Clause. | Match the existing project family. |
| 2026-07-11 | Use Swing/Java2D initially. | Smallest JDK-only desktop path. |
| 2026-07-11 | Keep Level 1 production modules JDK-only. | Preserve portability and native-image friendliness. |
| 2026-07-11 | Add format modules only with working behavior. | Avoid empty or speculative modules. |
| 2026-07-11 | Keep Native Image outside the default gate. | Native tooling is optional for normal development. |
| 2026-07-12 | Keep Java 21 bytecode fixed across CI launcher JDKs. | A newer build JDK should test compatibility, not silently raise the consumer baseline. |
| 2026-07-12 | Make an explicit offline repository the sole resolution source. | Offline evidence must not succeed through hidden public or machine-local fallback. |

## Task design traceability

Design status is independent of implementation task status. `Draft` is ready for review, `Reviewed`
has completed independent review, and `Approved` is the committed top- and mid-level design baseline.
Implementation tasks remain Proposed until their code, tests, and task-specific evidence are complete.

| Task | Design coverage | Status |
| --- | --- | --- |
| G0-001 | Java baseline, repository resolution, normal verification, and publication staging | Approved |
