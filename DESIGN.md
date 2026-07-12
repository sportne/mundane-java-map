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

## Design principles

- Prefer the smallest explicit model that supports the next observable vertical slice.
- Introduce an abstraction only for a demonstrated second consumer or a boundary that must remain
  independent, such as toolkit-neutral public contracts versus Java2D rendering.
- Give the common embedding path deterministic defaults and few moving parts; keep limits,
  registries, lifecycle, and advanced policy explicit rather than ambient.
- Assign every value, resource, cache, and registry one clear owner. Immutability is the default at
  public boundaries; mutable state remains local and lifecycle-bound.
- Reuse cross-cutting diagnostics, limits, and cancellation semantics instead of inventing a
  framework inside each format adapter.
- Delete or defer an abstraction when its owner, enforcement, or developer benefit cannot be stated
  plainly. The design should remain as simple as possible, but no simpler than its correctness and
  safety boundaries require.

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
mundane-map-core -> mundane-map-api
mundane-map-awt -> mundane-map-api, mundane-map-core
mundane-map-io-* -> mundane-map-api [, selected mundane-map-core algorithms]
```

- `api` depends on `java.base` only.
- `core` depends on `api` and `java.base` only.
- `awt` may depend on `api` and `core`; among production library modules, it alone owns
  `java.desktop`, Swing, Java2D, pointer wiring, and toolkit render caches. Support projects and
  consumer examples may use those APIs to exercise the AWT module, but cannot move them into another
  production boundary.
- An I/O module is named `mundane-map-io-*` and may depend on `api` and only the specific `core`
  algorithms it needs. It never depends on `awt` and never exposes toolkit types.
- An optional Level 2 external integration lives in a separately named adapter module. External
  dependencies and their types remain inside that adapter and do not enter `mundane-map-api`.
- Test, native, architecture, and example modules are not published.

The build has one authoritative project inventory with an entry for every included subproject. The
category describes the architectural dependency boundary, while separate properties record release
level, publication eligibility, and Native Image policy:

- **JDK-only runtime**: `api`, `core`, `awt`, and each `mundane-map-io-*` module after it provides
  working behavior and tests. An entry declares Level 1 or Level 2 independently of this category, so
  a dependency-free Level 2 format such as DTED remains a JDK-only runtime module without joining the
  Level 1 release. Every Level 1 entry is native-targeted with no per-module opt-out. A Level 2 entry
  explicitly says whether Native Image is targeted; its native-verification task can change that
  property without changing the module's category or release level.
- **Optional adapter**: a Level 2 integration that isolates an external dependency. Its publication
  and Native Image policies are explicit, and it cannot become part of the Level 1 runtime graph.
- **Support**: architecture tests, native smoke, performance evidence, examples, and consumer
  fixtures. Support projects are checked but never published or treated as production dependencies.

The checked-project, published-project, Level 1 release, runtime-dependency, architecture-test, and
native-target inputs are derived from that inventory rather than maintained as independent lists.
Settings and the inventory must contain the same included subprojects; configuration fails when a
project is absent, duplicated, or uncategorized. A production module is registered only with working
behavior and tests, so this rule does not justify creating empty future modules.

### Executable architecture rules

The normal quality gate enforces boundaries at complementary levels:

1. Resolved production runtime configurations from the project inventory must contain only JDK
   facilities and explicitly allowed `mundane-map` project artifacts for Level 1. Test and build-tool
   configurations are not mistaken for runtime dependencies.
2. Class-file rules enforce package direction, AWT confinement, public-signature purity, and native
   targeting. Public API types cannot mention core, AWT, format, or external-adapter types.
3. Direct mechanism checks inspect class access flags and symbolic member references. They reject
   `ACC_NATIVE` methods and calls to prohibited loading, discovery, serialization, reflection, or
   native-library APIs. Compiler-emitted `invokedynamic` bootstrap entries are not direct use of
   `java.lang.invoke` and do not fail the rule; explicit references to `MethodHandle`, `MethodHandles`,
   or `CallSite` do. `VarHandle` is outside the dynamic-invocation match but is disallowed by default
   until a task records a concrete concurrency or performance need and adds an exact rule decision.
4. Resource-tree inspection rejects service-provider descriptors and other declared discovery
   metadata. It does not reject an explicitly named application resource merely because it is in a
   JAR.
5. Positive fixtures demonstrate allowed dependencies. Negative fixtures live in a dedicated
   architecture-fixture source set whose output is never added to a production, publication, or
   native runtime. Each rule imports one deliberately violating fixture; forbidden dependency cases
   use a detached fixture-only configuration, so testing the rule cannot change a published module's
   dependency graph. A failure names the rule, module, class or dependency, and offending symbol.

Native-targeted Level 1 production code must not directly use:

- reflection APIs, explicit method-handle/call-site APIs, dynamic proxies, or dynamic class loading;
- `ServiceLoader`, service-provider descriptors, annotation/classpath scanning, or mutable global
  plugin registries;
- Java serialization streams or application persistence based on `Serializable`;
- JNI declarations, `System.load*`, `Runtime.load*`, `Unsafe`, `sun.*`, or `jdk.internal.*` APIs;
- resource enumeration or implicit discovery. Loading one explicitly named, registered resource is
  allowed and remains subject to Native Image resource declaration.

Explicit registration means the application or a documented default constructor supplies concrete
renderers, decoders, projections, or adapters by stable key. A registry is instance-owned and passed
to the component that uses it; it has no static registration entry point or mutable static holder.
The architecture test maintains an explicit list of registry contract types and rejects static fields
of those types plus static mutation methods on them. Registration contract tests cover ownership and
duplicate-key behavior. These mechanical checks do not claim to infer every indirect global-state
pattern, which remains part of design and code review. An immutable built-in catalog constant is not
a mutable registry. Registration never depends on what happens to be present on the classpath.

The Level 1 dependency direction, JDK-only runtime, API purity, AWT/I/O confinement, external-type
isolation, and prohibited native-targeted mechanisms are non-waivable. Matcher suppressions are
allowed only for an exact tool false positive that does not authorize direct use of the prohibited
mechanism. A suppression records the task, module, generated or inherited symbol, reason, and
narrowest scope, and adds a neighboring negative fixture proving that real direct use still fails.
Broad package suppressions and silent test exclusions are not permitted. For example, inherited JDK
behavior such as Swing's serialization ancestry may be excluded from an ancestry matcher, but
application serialization calls remain prohibited.

### G0 design closeout

The build baseline and architecture enforcement share the single project inventory described above;
they do not introduce a runtime framework or duplicate module lists. G0 therefore leaves the runtime
model unchanged while turning its existing boundaries into deterministic build evidence. Future
gates extend the inventory only when a vertical slice delivers working behavior and tests.

## Geometry and features

The first slice keeps its existing public model; G1 verifies it rather than introducing replacement
contracts ahead of the symbol and source gates. A `Coordinate` and every ordinate stored in an
`Envelope` or `CoordinateSequence` is finite. A coordinate sequence owns one packed `double[]`,
clones input and output arrays, addresses coordinates rather than raw ordinate offsets, and caches its
finite envelope. It contains at least one complete x/y pair.

Geometry adds the cardinality and topology constraints needed by its renderer:

- a point owns one non-null coordinate;
- a line string owns at least two coordinates;
- a polygon owns an exterior and a defensively copied ordered hole list; every ring has at least four
  coordinates and its final coordinate exactly equals its first coordinate.

G1 does not add ring repair, orientation rules, self-intersection analysis, multipart geometry, empty
geometry, or coordinate tolerances. Those belong to source-format and geometry-expansion tasks.
Constructor failures remain ordinary `NullPointerException`, `IllegalArgumentException`, or
`IndexOutOfBoundsException` failures with the violated role named; structured source diagnostics are
introduced at G4 and are not retrofitted onto programmer errors.

`Feature` owns a non-blank stable identifier, a display name that may be empty, one geometry, a
defensively copied immutable attribute map, and the existing `FeatureStyle`. Attribute values are not
deep-copied in G1; the format-neutral value profile is decided in G4. `InMemoryLayer` similarly owns
an immutable ordered feature snapshot and precomputes the union envelope, using absence rather than a
sentinel envelope for an empty layer. Layer order and feature order are rendering order. G2 decides
the migration from `FeatureStyle`; G1 changes it only to fix a verified contract defect.

## Projection pipeline

```text
source coordinate -> map projection -> projected world coordinate -> viewport -> screen pixel
```

`Projection` owns forward and inverse projection. The first concrete implementation accepts
longitude/latitude degrees, clamps latitude to the representable Web Mercator limit, and produces
projected meters. `MapViewport` never interprets a CRS; it owns only projected-world to screen math.
Screen x increases right, screen y increases down, the projected center maps to the screen center,
and `worldUnitsPerPixel` is finite and positive.

Viewport navigation returns new immutable values:

- resize preserves center and scale;
- a pixel drag moves the world in the same visual direction by applying the inverse delta to the
  center;
- zoom divides scale by a positive factor and adjusts the center so the projected coordinate beneath
  the supplied screen anchor is unchanged;
- fit centers a finite projected envelope and chooses the greater axis scale after subtracting
  non-negative padding on both sides. Usable width and height have a one-pixel floor, and a point or
  other zero-span envelope uses the existing positive `1e-9` projected-unit scale floor.

G1 tests round trips within numeric tolerance rather than demanding bit identity. `MapView.fitToData`
projects layer-envelope corners, unions all non-empty layers, uses the effective component size, and
is a no-op when all layers are empty. General non-monotonic envelope projection, longitude wrapping,
and CRS-domain diagnostics remain G4 concerns.

## Rendering and interaction

`MapView` is a Swing `JComponent` and is used under the normal Swing event-dispatch-thread contract.
It owns an immutable ordered snapshot of layer references and the current immutable viewport,
synchronizing only viewport dimensions to the effective component size. Each layer supplies its
current immutable feature snapshot for a paint pass; G1 does not deep-snapshot an arbitrary `Layer`
implementation. A paint pass creates and disposes a child `Graphics2D`, then traverses layers and
features in order. G1 preserves the direct geometry dispatch already in the component; explicit
symbol-renderer registration replaces it in G2.

The baseline renderer has these observable semantics:

- a point is a screen-sized circle centered on its projected coordinate, filled before it is stroked;
- a line is an open projected path drawn with its configured stroke;
- a polygon is one even-odd path containing the exterior and holes, filled before it is stroked, so
  a hole exposes the already-painted background or lower feature;
- zero-alpha fill/stroke skips that operation, and zero stroke width consistently means no stroke for
  points, lines, and polygons rather than Java2D's device-hairline convention; feature labels remain a
  point-only convenience of the first slice.

Automated rendering assertions use controlled offscreen images and inspect interiors, exteriors,
boundaries, and hole regions with bounded color/channel tolerance. They do not compare whole-image
goldens, font glyph pixels, antialiasing fringes, or platform-dependent raster identity. Each geometry
kind has an isolated fixture so one renderer cannot accidentally satisfy another renderer's test.

The installed baseline navigation is exercised by dispatching real Swing mouse events, not by calling
private handlers. A primary-button press/drag/release fixture proves cumulative panning; mouse-wheel
rotation proves cursor-anchored zoom. Tool/button arbitration is intentionally deferred to G3. A
component resize preserves center and scale while updating dimensions.

Pointer events cover `MOVED` and `CLICKED`. They retain the originating finite screen coordinates and
carry the coordinate produced by inverse-projecting the current viewport at that screen position.
Callbacks execute synchronously on the event-dispatch thread. Registration is ordered, duplicate
listener instances receive duplicate callbacks, and removal removes the first identical (`==`)
registration; an equal but distinct listener or an absent listener is a no-op. A callback iterates
over a snapshot, so additions or removals during a callback affect only later events. Tests dispatch
events on the event-dispatch thread and surface callback failures to the test thread.

The headless example test constructs `BasicViewer.createMapView()` on the event-dispatch thread
without opening a window, then inspects the public layer contents and proves that at least one point,
line, and polygon geometry reached the configured view. Layer or feature counts alone are not
sufficient evidence of the documented example slice.

## Native Image

Native-targeted code avoids reflection, runtime scanning, dynamic proxies, Java serialization,
`Unsafe`, internal JDK APIs, and implicit resource discovery. A real offscreen render is the first
native smoke path; metadata workarounds require a recorded design decision. G1 keeps one smoke
scenario shared by its JVM test and Native Image executable: construct a real point feature and
layer, fit a `MapView`, paint to an ARGB image, and fail unless a bounded center region contains the
expected non-background rendering. It does not substitute a class-loading-only smoke.

The complete smoke scenario, including component construction, layer mutation, fit, and paint, runs
on the Swing event-dispatch thread. A caller already on that thread executes it directly; any other
caller marshals it synchronously and rethrows the original failure on the calling thread. The JVM test
therefore verifies the same EDT-safe entrypoint used by the Native Image executable.

The native lane remains separate from `qualityGate`. Its absence is reported as unavailable rather
than treated as JVM success; when the G1 implementation task is accepted, the named HITL checkpoint
requires a maintainer with GraalVM to record the native command result.

### G1 design closeout

G1 adds verification depth, not a second map model. The packed geometry values, immutable viewport,
single AWT component, in-memory layer, and real native render remain the smallest end-to-end slice.
The maintainer checkpoint is explicitly: run the basic viewer on a desktop and confirm point, line,
polygon, fit, primary-drag pan, cursor-centered wheel zoom, and live pointer coordinates, then record
the available Native Image result. Symbols, generalized tools, hit testing, and source lifecycles stay
out of this gate.

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
| 2026-07-12 | Enforce Level 1 architecture with explicit allowlists in the normal gate. | Fast dependency, bytecode, and narrow source/resource checks prevent boundaries from becoming advisory. |
| 2026-07-12 | Verify the existing first map slice without replacing its contracts. | Deeper event, geometry, viewport, rendering, example, and native evidence should precede new abstractions. |

## Task design traceability

Design status is independent of implementation task status. `Draft` is ready for review, `Reviewed`
has completed independent review, and `Approved` is the committed top- and mid-level design baseline.
Implementation tasks remain Proposed until their code, tests, and task-specific evidence are complete.

| Task | Design coverage | Status |
| --- | --- | --- |
| G0-001 | Java baseline, repository resolution, normal verification, and publication staging | Approved |
| G0-002 | Module graph, architecture enforcement, prohibited mechanisms, and exception policy | Approved |
| G1-001 | First-slice geometry, viewport, rendering, interaction, example, and native verification | Approved |
