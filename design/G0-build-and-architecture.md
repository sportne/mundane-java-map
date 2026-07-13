# G0 — Build and architecture design

Project index: [DESIGN.md](../DESIGN.md).

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
- An I/O module is named `mundane-map-io-*` and normally depends on `api` and only the specific
  `core` algorithms it needs. A later container or transport may reuse a lower-level working format
  codec only through an explicitly inventoried, acyclic, task-approved project edge; architecture
  tests allowlist each concrete edge rather than opening a general I/O-to-I/O dependency category.
  It never depends on `awt` and never exposes toolkit types.
- An optional Level 2 external integration lives in a separately named adapter module. A format
  adapter still keeps the `mundane-map-io-*` prefix and names its implementation boundary explicitly,
  for example `mundane-map-io-geojson-jackson`. External dependencies and their types remain inside
  that adapter and do not enter `mundane-map-api`.
- Test, native, architecture, and example modules are not published.

The build has one authoritative project inventory with an entry for every included subproject. The
category describes the architectural dependency boundary, while separate properties record release
level, publication eligibility, and Native Image policy:

- **JDK-only runtime**: `api`, `core`, `awt`, and each dependency-free `mundane-map-io-*` module after
  it provides working behavior and tests. An entry declares Level 1 or Level 2 independently of this
  category, so a dependency-free Level 2 format such as DTED remains a JDK-only runtime module without
  joining the Level 1 release. An I/O-prefixed module with an approved external dependency is instead
  categorized as an Optional adapter. Every Level 1 entry is native-targeted with no per-module
  opt-out. A Level 2 entry explicitly says whether Native Image is targeted; its native-verification
  task can change that property without changing the module's category or release level.
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

The G6 requirement to use the JDK's standard PNG/JPEG `ImageIO` readers has one exact opaque-JDK
qualification. The public JDK exposes those readers only through the ImageIO registry; initializing
that registry may itself consult installed/application ImageIO providers. Application-level decoder
selection nevertheless remains explicit: only the AWT-owned decoder registered by
`AwtRasterDecoders.level1()` is selected, and it may retain/use only the two standard reader SPIs whose
provider classes belong to the named `java.desktop` module. Any discovered non-JDK provider is ignored
and cannot alter decoder choice or output. This qualification permits only the required ImageIO
registry lookup inside that exact AWT factory; it does not permit application/provider fallback,
`ImageIO.scanForPlugins`, registry mutation, `ServiceLoader` calls in project bytecode, service
descriptors, class/resource scanning, or any other discovery path. Architecture checks exact-allowlist
the factory calls and continue rejecting every direct prohibited mechanism elsewhere. This is an
acknowledged JDK codec initialization side effect, not a general registration exception.

The Level 1 dependency direction, JDK-only runtime, API purity, AWT/I/O confinement, external-type
isolation, and prohibited native-targeted mechanisms are non-waivable. Matcher suppressions are
allowed only for an exact tool false positive that does not authorize direct use of the prohibited
mechanism. A suppression records the task, module, generated or inherited symbol, reason, and
narrowest scope, and adds a neighboring negative fixture proving that real direct use still fails.
Broad package suppressions and silent test exclusions are not permitted. For example, inherited JDK
behavior such as Swing's serialization ancestry may be excluded from an ancestry matcher, but
application serialization calls remain prohibited.

An approved Optional adapter may depend on a third-party JAR that physically contains its own service
descriptor or shaded implementation classes even when the adapter uses neither. That fact is recorded
in the dependency/license/mechanism inventory; the descriptor is not copied into a MundaneJ artifact,
project resource tree, explicit native resource configuration, or application registry. Project
bytecode still constructs the approved concrete implementation directly and may not call discovery.
A Native Image claim additionally proves that no service registration or metadata-repository fallback
was used, inventories any statically reachable shaded classes, and proves disabled paths were not
executed. If the external library requires discovery or an unapproved native mechanism for the
approved capability, the adapter remains JVM-only or the task rejects the dependency; this is not a
general discovery exception for project code or behavior.

G10-004 approves the first future format-composition edges:
`mundane-map-io-geopackage-xerial -> mundane-map-io-image` and
`mundane-map-io-mbtiles-xerial -> mundane-map-io-image`, solely for bounded embedded PNG/JPEG decode.
G10-006 adds `mundane-map-io-http-tiles -> mundane-map-io-image` for the same explicit encoded-tile
decode boundary. None of these edges exists until its source module lands with working behavior and
tests. Reverse edges, cycles, AWT leakage, generic codec discovery, and arbitrary dependencies between
peer format modules remain forbidden. The authoritative project inventory and architecture-test
allowlist own the exact edges when those tasks implement them.

### G0 design closeout

The build baseline and architecture enforcement share the single project inventory described above;
they do not introduce a runtime framework or duplicate module lists. G0 therefore leaves the runtime
model unchanged while turning its existing boundaries into deterministic build evidence. Future
gates extend the inventory only when a vertical slice delivers working behavior and tests.
