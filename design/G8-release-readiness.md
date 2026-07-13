# G8 — Native and release readiness design

Project index: [DESIGN.md](../DESIGN.md).

## Level 1 Native Image and CI hardening (G8-001)

### One final aggregate executable

G8-001 retains `modules/mundane-map-native-tests`, `NativeSmokeMain.runSmoke()`, and the final exact
sentinel `mundane-map native smoke: OK` as the only assertion-bearing JVM/native entry point. After
its dependencies are implemented, its direct compile-time sequence is:

```text
run approved G2 symbol/catalog/resource scenario
open one G5 shapefile workspace; run approved G5 scenario; close workspace
open one G6 raster workspace; run approved G6 scenario; close workspace
run NativeLevel1SmokeScenario
print final sentinel
```

G2 failure creates no workspace; G5 failure completes G5 cleanup and prevents G6/G8; G6 failure
completes G6 cleanup and prevents G8. The two workspaces are fully closed before the G8 scenario.
There is no scenario registry, name dispatch, reflection, native JUnit binary, second application,
worker, or shared mutable scenario state. Package-private direct calls and one existing synchronous
EDT bridge are sufficient.

`NativeLevel1SmokeScenario` covers only final gaps: duplicate explicit registration, distance
strategies, real measurement routing, the final public G7 render default, and aggregate cleanup. G2
already proves vector/composite/icon catalog rendering, G5 proves shapefile query/render, and G6
proves PNG/JPEG decode/affine/cache/render; G8 does not create weaker duplicate versions of them.
Registry/numeric assertions run on the calling thread. Swing mutation, event dispatch, painting, tool
deactivation, and view close run synchronously on the EDT, propagating the original failure.

### Exact final scenario

The registration probe starts with `SymbolRendererRegistry.builder()`, installs one named final inert
renderer at role `MARKER` and application key
`io.github.mundanej.map.native-smoke.marker`, then repeats that registration. The second call must
produce stable code `SYMBOL_RENDERER_DUPLICATE` and exact ordered context
`{role=MARKER, key=io.github.mundanej.map.native-smoke.marker}`. The renderer is never invoked; it is
not a lambda requiring serialization, dynamic proxy, discovered class, or production fallback.
Message/cause text is not an oracle.

The numeric probe resolves canonical EPSG:3857 and EPSG:4326 definitions from one
`CrsRegistry.level1()` and creates the approved strategies explicitly. It requires:

- planar `(0,0)` to `(3000,4000)` equals exactly `5000.0` metres and retains EPSG:3857; and
- geographic `(179,0)` to `(-179,0)` equals `222390.1604670658` metres within G3's exact
  `max(1e-6, abs(expected)*1e-12)` tolerance and retains EPSG:4326.

This proves antimeridian normalization and fixed-radius great-circle behavior without inventing an
ellipsoidal/native geodesic check.

The real interaction/render probe constructs a public 192-by-160 EPSG:3857 identity `MapView` with
center `(0,0)` and exactly 100 map metres per logical pixel. It installs one `MeasurementTool` using
the planar strategy and dispatches ordinary Swing events on the EDT:

```text
count-one primary click (96,80)       -> (0,0)
move                    (126,40)      -> preview (3000,4000)
count-one primary click (126,40)      -> commit (3000,4000)
count-one primary click (156,80)      -> commit (6000,0)
count-two primary click (156,80)      -> complete without another vertex
```

After the move it requires phase `MEASURING`, one committed vertex, the exact preview, and displayed
distance 5,000 metres. After completion it requires phase `COMPLETE`, the three exact vertices in
order, no preview, and committed/displayed distance 10,000 metres. These are public state assertions,
not calls to the router or a fabricated toolkit-neutral event.

The same view installs exactly one core
`InMemoryLayer("level1-native", "Level 1 Native", List.of(line, marker))`; an arbitrary `Layer` is
not sufficient because G7-004 deliberately permits retained screen plans only for the concrete
identity-stable in-memory implementation. Both blank-named features, their immutable geometries, the
line symbol, marker symbol, and marker path are constructed once and reused by both paints.

The line feature `level1-line` has this one map-coordinate part, which maps to the shown exact screen
points under the fixed viewport:

```text
(-12000,-5000) -> (-24,130)  (outside left)
(-8000, -5000) -> ( 16,130)
(-6500, -3500) -> ( 31,115)
(-5000, -5000) -> ( 46,130)
(-3500, -3500) -> ( 61,115)
(-2000, -5000) -> ( 76,130)
```

Its `SolidLineSymbol` has no endpoints, opacity `1.0`, and one round screen-pixel stroke of width
`4.0` in opaque teal `(24,112,96,255)`. It therefore reaches G7 clipping/simplification, including a
real device-edge clip, with one exact geometry identity and clip margin on both paints.

The marker feature `level1-marker` is at map `(-6600,1500)`, screen `(30,65)`. It uses the one
immutable toolkit-neutral path returned for built-in `STAR`, centered 24-by-24 screen pixels with no
offset/rotation, screen-relative rotation, opacity `1.0`, opaque purple fill `(145,72,196,255)`, and a
round two-screen-pixel stroke `(32,24,48,255)`. Reusing that exact `VectorPath` identity reaches the
G7 vector-template default if retained.

Two paints with unchanged content, viewport, tool, CRS, and registry use fresh 192-by-160
`TYPE_INT_ARGB` buffers initialized to opaque white. The complete packed pixels must be exactly equal
to each other within that same JVM/native invocation. Independently, each 3-by-3 feature probe needs
a strict majority within 20 per RGBA channel of its target: clipped line at `(1,130)`, line interior
at `(46,130)`, and marker fill at `(30,65)`. The first committed measurement segment is probed at its
exact midpoint `(111,60)`; a strict 3-by-3 majority must be opaque crimson-dominant
`r>=128 && r-g>=40 && r-b>=40`, avoiding font glyphs and the current-segment label. A 5-by-5 probe
centered at `(184,148)` remains exact white. Every nonwhite pixel lies inside inclusive screen bounds
`[0,0,180,136]`, and the nonwhite count is in `[200,10000]`. These broad invariant bounds admit the
approved G3 casing/text antialiasing while rejecting missing, displaced, or flooded rendering.

This uses only public `MapView` construction, `InMemoryLayer`, and symbol/tool contracts: no
performance bridge, optimizer or cache mode, cache counter, or package-private state.

If G7-004 retained a screen-plan or vector-template partition, the second paint naturally reaches its
warm default. If either or both were rejected, the same scenario proves the final uncached behavior.
The smoke records no hit, speed, or cache-retention claim. Before closing the first view, it creates a
second compatible identity view and proves installing that same `MeasurementTool` instance fails with
the approved ownership lifecycle error before the second view or tool changes. Closing the first view
must deactivate the tool and leave its state `EMPTY`. Installing that exact tool instance into the
second view must then succeed with `EMPTY` state, directly proving claim release; closing the second
view deactivates and clears it again. Running the entire JVM `runSmoke()` twice in one test remains an
additional proof that no mutable static registry, workspace, or scenario state survives.

Failures use only bounded invariant tokens such as `registration-diagnostic`, `measurement-planar`,
`measurement-geographic`, `measurement-preview`, `measurement-state`, `measurement-render`,
`final-render-repeat`, and `level1-cleanup`. Exact public diagnostic/state values carry the contract;
incidental messages and absolute paths do not.

### Diagnostics and fixed resource boundary

The final aggregate includes these release-relevant diagnostic paths:

- `SYMBOL_RENDERER_DUPLICATE` from the G8 registration probe;
- `SHAPEFILE_RECORD_LENGTH_INVALID` with G5's exact component/record/offset/context;
- `IMAGE_CONTAINER_INVALID` at PNG byte 54 with exact
  `{format=PNG, reason=chunkCrc}` from G6; and
- G2's existing `SYMBOL_CATALOG_MISSING` as additional symbol coverage.

G8 adds no resource. The one Java 21
`META-INF/native-image/io.github.mundanej/mundane-map-native-tests/resource-config.json` remains
exactly 12 individually quoted literal entries: one G2 raw RGBA icon, six G5 shapefile files, and five
G6 raster/world-file files. JVM tests compare the normalized exact JSON and complete processed main-
resource tree. No wildcard, directory, bundle, service, reflection/proxy/JNI/serialization metadata,
tracing-agent output, metadata-repository entry, provider registration, or class-initialization flag
is permitted.

If explicit Java 21 construction cannot build/run on the selected GraalVM, the implementation task
is Blocked rather than widened to a prohibited mechanism. A necessary compatibility correction stays
inside the owning Level 1 module, preserves approved JVM semantics, and adds a focused JVM regression;
it cannot add a public workaround or broad reachability flag.

### Gradle and specialized-lane ownership

Root `nativeSmoke` continues to depend on exactly
`:modules:mundane-map-native-tests:nativeRun`. The support project has explicit project dependencies
only on API, core, AWT, shapefile, and image I/O and never on examples, corpus, render-regression,
performance, consumer, or publication support. Its one `main` image uses Java 21 with
`metadataRepository.enabled=false`, `fallback=false`, and `--no-fallback`. G8 does not invoke
`nativeTest`, upgrade the Native Build Tools plugin opportunistically, or build a second binary.

Build-graph tests prove `qualityGate` excludes every native task and `nativeSmoke` excludes all other
specialized root lanes. Ordinary compilation prerequisites do not merge those lanes. Architecture
tests scan every Level 1 production output plus native support for reflection/classpath scanning,
dynamic proxies, Java serialization, JNI, `Unsafe`, internal JDK APIs, service/resource discovery,
and unapproved metadata. The sole literal `Class.getResourceAsStream` support lookup remains the
approved exception; enumeration is forbidden.

### One pinned Linux release lane

`.github/workflows/native-image.yml` retains one `native-smoke` job with:

```text
runs-on: ubuntu-24.04
timeout-minutes: 30
graalvm/setup-graalvm: Java 21, distribution graalvm, native-image job reports enabled
exact Gradle invocation: ./gradlew nativeSmoke --console=plain
```

Before the Gradle invocation, a shell step writes `java -version`, `native-image --version`, OS, and
architecture output to `build/native-evidence/toolchain.txt`. The build step enables `pipefail` and
tees combined output to `build/native-evidence/native-smoke.log`, so a failed native build/run still
fails the job. `actions/upload-artifact@v4` runs with `if: always()`, uploads only that bounded
directory for 14 days, and does not upload the executable or build tree. The setup action's job report
and retained text evidence are complementary.

A focused repository-policy test pins the runner, timeout, Java/distribution/job-report settings,
exactly one Gradle invocation equal to `nativeSmoke`, `pipefail`, and always-run bounded upload. It
rejects `nativeTest`, `qualityGate`, corpus, rendering, performance, publication, and consumer commands
in this workflow. No workflow secret except the setup action's existing read-scoped GitHub token is
consumed; no credential is printed or archived.

This lane supports only the statement:

> GraalVM Native Image is verified with Java 21 on Linux x86_64 using the recorded Ubuntu 24.04 CI
> environment. Windows, macOS, Linux AArch64, other distributions, and cross-platform Native Image
> compatibility are unverified.

One Ubuntu run is not an all-Linux promise. Supplemental results do not widen the wording without a
maintained build-and-run lane of their own. Native evidence blocks G8-004 through task status; it does
not make optional tooling part of `qualityGate`.

### Named checkpoint and verification

The HITL checkpoint is **G8 Linux Native Image release-lane approval**. A successful clean CI run on
the reviewed commit is required; a local run is supplemental. Task Notes record commit and workflow
URL/job, reviewer/date, runner OS/architecture, GraalVM distribution, full Java/native-image versions,
exact command/sentinel, reviewed 12-resource inventory, the three required diagnostic outcomes, final
G7 retained partitions including `none`, repeat-render semantic result, exact support wording, and
approval or blocker. Missing tooling/infrastructure is no evidence, not a waiver.

JVM tests exercise every final scenario branch, real EDT events, fixed carrier/geometry/symbol/probes,
same-tool cross-view ownership before/after close, mutated expected state/probe/diagnostic controls,
repeated invocation, exact resources, and cleanup. Workflow/build-graph and
architecture checks run with the support tests. The actual `nativeSmoke` remains a separate required
lane, followed independently by `qualityGate` and whitespace; corpus, render-regression, performance,
publication, and consumer lanes do not run here.

One executable, one literal resource configuration, one direct final scenario, and one retained Linux
CI job are sufficient. There is no native test framework, scenario/plugin registry, second fixture
corpus, broad metadata, private cache instrumentation, native benchmark, or platform matrix. This is
the smallest final Native Image boundary that proves all Level 1 dependencies coexist and leaves a
reviewable release claim.

## Public API, Javadocs, and examples review (G8-002)

### One bounded release-review inventory

G8-002 reviews every project-declared public/protected declaration in the five published Level 1
modules and the consumer-facing entry points/headless factories of five examples. G0's authoritative
project inventory supplies module/release classification; G8 neither copies it into another Gradle
list nor creates an API-manifest subsystem. At execution time, each module's generated
`allclasses-index.html` and member index are the exhaustive declaration checklist:

| Published module | Public package and contract families |
| --- | --- |
| `mundane-map-api` | Geometry/features; symbols/vector paths/catalogs/icons; tools/selection; CRS/projection; feature/raster sources; diagnostics/cancellation/limits; distance/measurement; encoded-raster decoder boundary |
| `mundane-map-core` | Snapshots/sources; viewport/CRS/projections; symbol transforms/layout; tool/hit/distance algorithms; accounting/resampling; explicit spatial index; screen optimizer |
| `mundane-map-awt` | `MapView`/bindings; explicit symbol registry/contexts/results; measurement tool; raster decoder factory and render options |
| `mundane-map-io-shapefile` | `Shapefiles`, open options/limits, and DBF encoding profile |
| `mundane-map-io-image` | `RasterImages`, open options/placement/limits/cache policy |

The examples are `BasicViewer`, `SymbolGallery`, `MeasurementViewer`, `ShapefileViewer`, and
`RasterViewer`. The task records one disposition per module/package and example in its Notes; it does
not check in `javap` dumps, generated HTML, an ABI baseline, or a general review database. There is no
released/tagged API baseline yet, so G8 cannot honestly claim compatibility with development
snapshots or justify a binary-compatibility plugin.

### Compatibility and coherence disposition

`Layer` and `InMemoryLayer` remain supported, non-deprecated small-snapshot APIs. They are the
ergonomic choice for already available embedded data; source bindings provide bounded/lazy behavior
without replacing them. Deprecated `FeatureStyle` remains supported through the first Level 1 `0.x`
release with documented migration to role-specific symbols and intended removal before `1.0.0`, as
G2 approved. G8 does not reopen the approved G2 symbol migration, G4 source/CRS contracts, geometry
roles, format profiles, or G7 evidence choices.

Documentation fixes, missing validation, defensive-copy defects, and source-compatible consistency
corrections stay in the owning module with focused regression tests. A source- or binary-incompatible
correction requires an individually recorded maintainer decision at the named checkpoint, including
the defect, affected declaration/callers, migration, and why deferral is worse. Naming cleanup,
package moves, speculative overloads, and convenience types are deferred. G8-004 selects the release
version, so this task invents no `@since` version or final long-term support promise.

The review checks that canonical overloads own validation and conveniences delegate with identical
defaults; non-null versus `Optional` use is consistent; values are deeply immutable with defensive
array/collection copies; units and coordinate spaces are explicit; borrowed/owned/close/cancel/EDT
rules agree across contracts; exception versus stable diagnostic categories are coherent; registries
are explicit/instance-owned; and public signatures preserve module purity. Architecture tests—not
manual taste—verify API/core have no AWT/external types, format APIs do not leak parser/provider types,
and public data structures expose no mutable storage.

### Strict useful Javadocs

The existing Java 21 Javadoc tasks gain exact offline settings: UTF-8 input/output, all doclint groups,
`-Werror`, and `-notimestamp`. No remote `-link`, network lookup, doclet, aggregate site, or new plugin
is introduced. Local `{@link}`, `{@linkplain}`, and `@see` references must resolve.

The universal `config/checkstyle/checkstyle.xml` and its ordinary `checkstyleMain`/`checkstyleTest`
tasks remain unchanged and continue to enforce style in all production, test, example, and support
projects. The existing `mundane-map.publishing-conventions` marker—already applied only to G0's exact
published-project inventory—registers one additional `checkstylePublicApi` task over
`sourceSets.main.allJava` and attaches it to that project's `check`. It uses a second lean
`config/checkstyle/checkstyle-public-api.xml` containing exactly:

```text
JavadocPackage
MissingJavadocType(scope=protected)
MissingJavadocMethod(scope=protected, allowedAnnotations=Override)
JavadocVariable(accessModifiers=public,protected; tokens=VARIABLE_DEF,ENUM_CONSTANT_DEF)
```

Thus public and protected top-level/nested types, constructors, non-override methods, fields, and enum
constants in the five published main source sets require documentation; package-info is mandatory.
Record components are handled by type-level `@param` under doclint. A method annotated `@Override` may
omit a repeated comment only when the inherited behavior is unchanged; the release review still
requires explicit Javadoc when an override strengthens validation, lifecycle, exceptions, or units.
There are no generated Level 1 main sources or blanket suppression. This second task does not run on
tests/examples/support projects and does not disable their existing Checkstyle tasks.

Every package exporting public declarations gets substantive `package-info.java`. Documentation is
specific by family:

- immutable values state equality/canonicalization, defensive copying, bounds, and units;
- CRS/geometry state x/y convention and source/map/display/logical-screen coordinate space/domains;
- sources/cursors/views state ownership, external serialization or EDT confinement, one-live-cursor,
  close/cancel arbitration, and which immutable results survive close;
- registries state instance ownership, exact key matching, duplicate behavior, and absence of
  discovery/global mutation;
- diagnostics state stable code/severity/location/ordered-context versus non-contractual message/cause;
- limits/formats state defaults, equality/plus-one behavior, supported profile, sidecars, encoding,
  and CRS boundaries; and
- deprecated declarations state the supported migration and removal-before-1.0 intent without a
  guessed release number.

Record accessors are documented by type-level `@param` tags. An `Object`/interface override may inherit
documentation only when it strengthens no semantics; otherwise its exact behavior is documented.
Compiler/Javadoc warnings are errors in this gate. There is no JPMS descriptor, nullability annotation
dependency, documentation hierarchy, or source generator.

### Consumer-oriented README

`README.md` is rewritten from observed final source, not from the roadmap. It names exactly the five
published modules and five examples, Java 21 consumer baseline, JDK-only Level 1 runtime, Swing/Java2D
boundary, explicit CRS/symbol/decoder registry construction, ownership/close behavior, and a small
compiling example using the final symbol/source contracts. It summarizes the bounded shapefile and
PNG/JPEG/world-file profiles, exact EPSG:4326/EPSG:3857 boundary, independent verification lanes, and
major Level 2 exclusions.

Pre-1.0 wording says compatibility is managed and intentional: documented `0.x` migrations may occur
with release notes, while deprecated `FeatureStyle` has the disposition above. It does not say APIs
may change "freely," claim unreleased compatibility, advertise empty/future modules as current, or
make an evidence claim that depends on G8-001 finishing first. Because G8-001 and G8-002 are deliberate
parallel branches, this task writes the exact provisional Native Image statement:

> Release verification targets GraalVM Java 21 on Ubuntu 24.04 Linux x86_64; Native Image
> compatibility remains unverified until the G8 Linux Native Image release-lane checkpoint is
> approved. Windows, macOS, Linux AArch64, other distributions, and cross-platform compatibility are
> unverified.

G8-004, which runs only after G8-003 has joined both branches, is the sole owner that replaces this
status with G8-001's recorded evidence and final approved support wording. Architecture rationale
stays in `DESIGN.md`; README remains a concise consumer entry point and no `docs/` tree is created.

### Five real examples, no example framework

The examples remain independent small applications. They share production values/renderers/parsers,
not a new example framework or copied test utility. Every Swing construction/mutation occurs on the
EDT. Each project exposes a package-private or public-as-already-required headless factory used by its
tests; automated tests never open a frame or require a display.

Automated evidence is exact by example:

- basic viewer: final point/line/polygon roles, fit, pan/zoom navigation, pointer observation, and one
  invariant offscreen render;
- symbol gallery: the committed G2 case matrix, explicit catalog/registry, selection of every tab,
  and existing portable render scenarios;
- measurement viewer: planar and geographic strategies/tabs, preview/complete/undo/cancel state,
  navigation coexistence, formatting, and offscreen overlay;
- shapefile viewer: argument/override parsing, temporary supported fixtures, bounded preview,
  opening/latest warning and terminal diagnostic presentation, and cleanup; and
- raster viewer: normalized and world-file modes, nearest/bilinear/opacity controls, explicit decoder
  registry, structured failures, affine render, and cleanup.

Examples call production parsers/renderers only. They do not embed shapefile/image parsing, copy a
CRS registry, manufacture a second diagnostic model, or retain test/corpus logic in main sources.
Generic diagnostic presentation displays stable code, severity, the viewer's fixed source identity,
bounded ordered context, and component/record/part/field/byte location when present. The shapefile
loader always supplies `SourceIdentity("shapefile-viewer", "Shapefile")`; the raster loader always
supplies `SourceIdentity("raster-viewer", "Raster image")`. Neither copies the argument, filename, or
parent path into metadata/status/diagnostic presentation. It never parses messages or shows raw bytes,
provider text, retained PRJ, credentials, or absolute paths. Tests open valid and failing fixtures
beneath a directory containing a unique sensitive sentinel and assert that the absolute path,
filename, and sentinel are absent from every presentation-model string while the fixed identity and
structured location remain. Opening and latest operation reports remain visibly distinct.

The first three applications are deterministic no-argument demonstrations. The two format viewers
remain honest file consumers. Their repository-relative review inputs are fixed and their `run` tasks
use the root project as working directory:

```text
modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/
  data/generated-polygon-hole-windows1252-3857/
  generated-polygon-hole-windows1252-3857.shp

modules/mundane-map-io-image/src/test/resources/io/github/mundanej/map/io/image/
  rotated-sheared.png
  rotated-sheared.pgw
```

The first is G5-009's licensed corpus polygon/hole, Windows-1252, EPSG:3857 case and uses retained PRJ
without an override. The second is G6-002's checked repository-authored rotated/sheared PNG/PGW and is
opened with explicit `--world-file EPSG:3857`; it is not a G6-005 native resource. G6's otherwise
unnamed fixture adopts these stable filenames during its implementation without changing its approved
content/profile. Automated example tests continue to use tiny temporary fixtures rather than making
normal checks execute the corpus lane.

### Manual checkpoint and verification

The HITL checkpoint is **G8 Level 1 API, Javadoc, and example approval**. The maintainer manually runs
and closes each application before the next:

- basic: point/line/polygon, fit, pan, zoom, pointer coordinates;
- gallery: every tab, pan/zoom, and G2's visual checklist;
- measurement: both CRS tabs, preview/add/Backspace/Escape, units, pan, and wheel;
- shapefile: polygon/hole, non-ASCII attribute preview, metadata/diagnostics, and clean close; and
- raster: rotated/sheared placement, nearest/bilinear, opacity, status, and clean close.

Task Notes record reviewed commit, reviewer/date, Java version, OS/architecture/window system/display
scale, exact commands, per-example result, module/package/API/Javadoc dispositions, `Layer` and
`FeatureStyle` decisions, any approved incompatible correction, and blocker. A missing capability or
architectural contradiction is returned to its owning gate; it does not authorize G8 to add a new
feature.

Focused module/example/architecture checks and explicit Javadocs run first, followed by the separate
`renderRegression`, normal `qualityGate`, and whitespace. Native, corpus, performance, publication,
and consumer lanes remain owned by G8-001/G8-003/G8-004 and are not folded into this review. Five
module indexes, strict project-owned documentation rules, five headless tests, and one visual approval
are sufficient; there is no ABI service, aggregate docs site, common example runtime, or release
governance framework.

## Publication and downstream consumer smoke (G8-003)

### One exact five-artifact contract

G8-003 hardens G0's existing local Maven staging and adds one downstream consumer. G0's authoritative
project inventory still decides which projects exist and are published. One release-contract table
then pins the artifact coordinates and dependency metadata that a consumer must receive; it is a
release assertion, not a second module inventory:

| Artifact | POM compile / Gradle `apiElements` | POM runtime-only / extra `runtimeElements` |
| --- | --- | --- |
| `mundane-map-api` | none | none |
| `mundane-map-core` | `mundane-map-api` | none |
| `mundane-map-awt` | `mundane-map-api`, `mundane-map-core` | none |
| `mundane-map-io-shapefile` | `mundane-map-api` | `mundane-map-core` |
| `mundane-map-io-image` | `mundane-map-api` | `mundane-map-core` |

Every coordinate is `io.github.mundanej:<artifact>:${map.version}`. The verifier rejects a missing or
extra coordinate; external, version-range, classifier, platform, or duplicate dependency; a Gradle
project path; and any POM `repositories` or `distributionManagement`. It checks the `.module`
component identity, Java 21 `apiElements`/`runtimeElements`, exact dependency variants, and sources/
Javadocs variants against the same row. There is no BOM or umbrella artifact for five modules.

POM metadata has exact project URL `https://github.com/sportne/mundane-java-map`, BSD 3-Clause name/
URL with repository distribution, developer ID `sportne`, SCM URL, and connection
`scm:git:https://github.com/sportne/mundane-java-map.git`; name/description come from the declared
project. It contains no credential, local path, repository, unresolved property, snapshot other than
the selected version, or guessed organization/person field.

### Repair ordering at the real write tasks

The current root graph orders only `publishAllPublicationsToReleaseDryRunRepository` aggregates after
`cleanReleaseDryRun`. Their individual `publishMavenJavaPublicationToReleaseDryRunRepository`
dependencies can still race the delete when `org.gradle.parallel=true`; the observed partial staging
tree is consistent with that defect. G8 fixes the real graph:

```text
cleanReleaseDryRun
  -> five actual mavenJava releaseDryRun publish tasks
       (shared-repository write actions serialized in inventory order)
  -> stageReleaseDryRun
  -> validateReleaseDryRun
  -> publicationDryRun
  -> consumerSmoke
```

Each actual publish task depends directly on `cleanReleaseDryRun`. The five actual tasks form a
`mustRunAfter` chain in the release-contract order above, so artifact compilation/JAR generation may
remain parallel while repository writes cannot overlap. `stageReleaseDryRun` depends directly on
those actual tasks, never the ineffective `publishAll...` aggregates. Validation follows staging;
`publicationDryRun` is the public lifecycle aggregate. If root `clean` is also selected,
`cleanReleaseDryRun` and every actual write are ordered after it; ordinary staging does not depend on
the whole root clean.

A failure may leave a partial non-authoritative build directory, but validation/consumer cannot run
and the next invocation deletes it. One shared repository plus deterministic ordering is enough; no
transactional swap, per-module repository, staging archive, daemon lock protocol, or remote target is
added. Two consecutive parallel-enabled functional runs start with a sentinel file and prove it is
removed while all five complete coordinates remain.

### Artifact and layout verifier

Build logic owns one small pure Maven-tree verifier and a typed non-cacheable
`VerifyReleaseDryRunRepository` task with explicit directory/version/contract inputs. It is not a
production module or generic artifact framework. Stable build failures use a bracketed invariant plus
coordinate and normalized repository-relative path, for example `MISSING_ARTIFACT`,
`UNEXPECTED_DEPENDENCY`, `VERSION_MISMATCH`, `CHECKSUM_MISMATCH`, `FORBIDDEN_ENTRY`, and
`UNEXPECTED_REPOSITORY`; production `SourceDiagnostic` is not reused.

The repository is cleared before staging. For an exact non-snapshot version, filenames contain that
version. For Gradle's unique Maven snapshot layout, the verifier securely reads each coordinate's
`maven-metadata.xml` and requires one timestamp/build value internally coherent across that
coordinate's POM, module, binary, sources, and Javadocs; independently published coordinates need not
share a timestamp/build. It never guesses timestamped names and rejects another group/artifact/version
directory or stale primary file.

Every artifact has a valid POM, Gradle module metadata file, binary JAR, sources JAR, and Javadoc JAR.
SHA-256 and SHA-512 sidecars are mandatory and independently recomputed for each primary; emitted
MD5/SHA-1 files, when present, are also verified but are not called the release guarantee. `.module`
file size/checksum declarations must match their archives. Archive tasks disable file timestamps and
use reproducible entry order.

The exact root BSD text is added as `META-INF/LICENSE` to binary, sources, and Javadocs JARs. This is
the sole required non-code publication resource. G2/G5/G6 native resources belong to the non-published
native support project; there is no production symbol/image resource loader. The verifier rejects
`io/github/mundanej/map/nativeimage/**`, corpus/test/example/support classes or fixtures,
`META-INF/services/**`, absolute/backslash/dot-segment/duplicate ZIP entries, and unexpected package
roots. It scans textual metadata/manifests for workspace paths, credentials, unresolved project
notation, and repository declarations.

Binary roots are exact module packages plus manifest/license. Sources contain matching main Java/
package docs and no test path. Javadocs are valid archives containing index/package pages and
representative public-type pages; JDK-owned stylesheet/script/legal assets are not byte-whitelisted.
Pure-verifier tests construct positive snapshot/release trees and mutate every required file,
dependency/scope/version, license, digest, repository, coordinate, and archive entry. Build-logic
functional tests exercise the real parallel task graph. The publication lane depends on those tests
and actual validation rather than trusting a successful Maven Publish task.

### One standalone clean Java 21 consumer

`consumer-smoke/` is a tiny checked-in Gradle template with its own settings, build file, and Java
main source. It is not included from root settings, listed in the project inventory, published, or
given a wrapper. Root copies only that template to `build/consumer-smoke/project` before execution;
the checked-in tree never accumulates output.

Consumer settings require absolute existing properties `map.consumerRepository` and
`map.consumerVersion`, use `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, and declare exactly that local
Maven directory. They contain no plugin/dependency repository, `mavenCentral`, `mavenLocal`,
`includeBuild`, version catalog, project substitution, or network fallback. Only Gradle's core `java`
and `application` plugins are used. Direct dependencies are exactly AWT, shapefile, and image at the
supplied version; API/core imports must resolve transitively. A resolution assertion rejects every
dependency whose identifier is a `ProjectComponentIdentifier` and requires exactly the five
mundane-map external-module dependency components and no other library component. The standalone
consumer's root result component is necessarily a project identifier and is explicitly excluded from
that dependency-only assertion; no workspace project may appear below it.

Root resolves the current Gradle installation executable and a Java 21 toolchain, deletes a fresh
consumer `GRADLE_USER_HOME`, sets `JAVA_HOME`, disables toolchain auto-download, and launches the
child with:

```text
--offline --no-daemon --no-build-cache --no-configuration-cache --console=plain
```

The consumer compiles with `--release 21` and asserts runtime feature 21. It has a bounded timeout,
waits for exit, and captures output; success prints only the checked final sentinel, while failure
prints the bounded captured output. No wrapper download or nested persistent daemon is possible. The
root task is always-run/non-cacheable because it proves isolation and process behavior.

Before the valid run, a separate copied project and newly empty Gradle home omit the repository
property and must fail before resolution with exact settings invariant
`map.consumerRepository must name an absolute existing directory`. A second fresh home performs the
real run. The child never consumes a developer cache, workspace project, included build, public
repository, or prior negative-run state.

### Real public consumer assertions

The consumer builds `CrsRegistry.level1()`, `SymbolRendererRegistry.builderWithBuiltIns().build()`,
and `AwtRasterDecoders.level1()` explicitly. On the EDT it renders one in-memory built-in vector
symbol through public `MapView`, asserts portable bounds/region/color invariants, and closes the view.
This proves the staged AWT artifact and transitive API/core classes, not pixel identity.

Within one owned temporary directory, small literal fixture builders create:

- a valid point SHP/SHX sufficient for public `Shapefiles` open/query/cursor ID, geometry, order, and
  close assertions;
- a 99-byte truncated SHP that must produce exact `SHAPEFILE_HEADER_INVALID`, component `shp`, byte
  zero, and context `{field=headerSize, expectedBytes=100, actualBytes=99}`; and
- tiny PNG and baseline JPEG files through JDK `ImageIO`, opened through public `RasterImages` with the
  explicit decoder registry and asserted by exact PNG/tolerant JPEG samples.

Builders are consumer fixtures, not shared parsers, a corpus, or published resources. The consumer
proves returned immutable feature/raster values remain usable after cursor/source close, repeated
close is safe, every temp path deletes, and structured diagnostics are usable without message text.
Only after all assertions does it print `mundane-map consumer smoke: OK`.

### Verification and simplicity

`publicationDryRun` includes verifier unit/functional tests, clean staging, and exact artifact
validation. `consumerSmoke` depends on it and adds the two isolated child invocations. Naming both in
one Gradle command performs the work once. The lane remains outside `qualityGate`; native, corpus,
render-regression, and performance support are neither published nor executed.

Tests cover positive snapshot/release layouts; missing main/source/Javadocs/POM/module/license;
metadata/dependency/version/checksum mutations; forbidden/unsafe/duplicate entries; stale sentinel;
parallel/repeated ordering; missing/wrong repository; exact five-component resolution; Java 21;
valid/malformed consumer semantics; and cleanup. One five-row contract, one verifier, one local Maven
repository, and one standalone consumer are sufficient. There is no BOM, umbrella, release ZIP,
Maven CLI, new support module, fixture publication, runtime publication API, or remote/credential path.

As a release-evidence output, successful validation also writes
`build/release-evidence/artifact-manifest.tsv`: UTF-8/LF rows sorted by normalized path relative to the
staged Maven root, with exact path, decimal byte length, and lowercase SHA-256 for every primary and
checksum file belonging to the POM, module file, and three JARs. Coordinate-level
`maven-metadata.xml` and its checksum sidecars are validated but excluded because their repository
`lastUpdated` value is intentionally staging-time state, not artifact reproducibility. The output
directory is cleared in the same staging lifecycle; the manifest is outside the Maven repository and
is never published. Its own independently computed SHA-256, row count, and total primary bytes enter
G8-004's checkpoint, avoiding a self-referential manifest row.

### Post-Level 1 published-module extension rule

The five-row matrix above is the immutable Level 1 release-candidate contract and remains the meaning
of recorded G8 evidence. After G8-004, a task that adds a working Published module must extend the
current authoritative project inventory, release-contract table/verifier, staged manifest, and
standalone consumer in that same change. It cannot leave the verifier expecting the historical set,
publish an unconsumed artifact, or retroactively claim that the new module belonged to Level 1.

G9-003 is the first such extension. The current post-Level-1 contract becomes six coordinates by
adding `mundane-map-io-dted`, whose POM/API variants expose `mundane-map-api` at compile scope and
`mundane-map-core` only at runtime. `publicationDryRun consumerSmoke` stages/verifies all six and the
consumer opens a tiny DTED fixture. G8-004's historical candidate record, support statement, five-
coordinate manifest, and go/no-go result remain unchanged; a later Level 2 release task will reconcile
fresh evidence against the then-current inventory.

## Level 1 release readiness and G8 closeout (G8-004)

### One immutable release candidate

G8-004 is evidence reconciliation, not another production capability or release automation system.
The maintainer selects one strict ASCII semantic version matching
`(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)`; `0.1.0` is the recommended first Level 1
candidate. Pre-release/build metadata, `SNAPSHOT` in any case, whitespace, sign, path/URI character,
or normalization is rejected. The version is supplied only as `-Pmap.version=<candidate>` to G8-003;
`gradle.properties` remains `0.1.0-SNAPSHOT` for continued development.

Before evidence begins, every production, test, workflow, release metadata, README, license,
provenance, and release-note input is committed. `git status --porcelain=v1 --untracked-files=all` is
empty, and the candidate is one full lowercase 40-hex commit ID. Every lane/report records that exact
ID; an abbreviated SHA, dirty tree, missing revision, or result from another commit is no evidence.
Infrastructure failure is `NO EVIDENCE`, not a pass or waiver, and is rerun on the same SHA.

The authoritative G8-001/native and normal compatibility workflows require that commit to exist on
GitHub. After creating the clean local candidate, this task pauses at a named external prerequisite:
a maintainer must push or otherwise expose that exact SHA to the configured workflows and provide the
resulting run/job URLs. The task/agent performs no push, workflow dispatch, credential access, or
remote mutation. The record distinguishes external actor/date/ref/URL from task actions. Until the
exact-SHA evidence is supplied the task is `Blocked`; a run for a parent, amended, or evidence-record
commit cannot substitute. A completed exact-SHA run that fails is `FAIL`/`NO-GO`, not an external
blocker.

The candidate includes one root `CHANGELOG.md` entry with the version, Level 1 capabilities,
migrations/deprecations, limits, and known non-claims. Before evidence, G8-004 replaces G8-002's
parallel-work provisional text with the complete final proposed support wording below. This is an
unreleased candidate claim: failure prevents release rather than causing a post-test README rewrite.
The tested candidate and any later source tag therefore carry the same user-facing contract.

### Six independent lane records

The fixed procedural order is not a merged Gradle graph:

| Order | Lane and exact command | Required provenance | Blocking rule |
| ---: | --- | --- | --- |
| 1 | `./gradlew nativeSmoke --console=plain` | Candidate SHA; G8-001 CI URL/job; Ubuntu/architecture; GraalVM distribution; full Java/native-image versions; no-fallback build, execution, sentinel | Missing/fallback/build-only/skipped/semantic failure blocks; the clean G8-001 Linux CI run is authoritative and a local run is supplemental |
| 2 | `./gradlew shapefileCorpus --console=plain` | SHA; Java/OS; corpus manifest SHA; dataset/component counts; provenance/license approval; success summary | Checksum, expectation, parser, or cleanup failure blocks |
| 3 | `./gradlew renderRegression --console=plain` | SHA; Java/OS/headless state; scenario count; invariant/tolerance result and report SHA when produced | Any semantic/render invariant failure blocks |
| 4 | `./gradlew -PperformanceRevision=<candidate-sha> performanceEvidence --console=plain` | SHA; canonical configuration/environment; evidence SHA; final G7 cache decisions/limits | Configuration, semantic, counter, cleanup, or report failure blocks; duration alone never blocks |
| 5 | Two fresh `publicationDryRun` stagings at `-Pmap.version=<candidate>`, followed by `consumerSmoke` on the second and exact immutable-manifest comparison | SHA/version; both manifest SHAs/counts/bytes; five coordinates; clean offline consumer sentinel | Snapshot/path/credential leakage, metadata/checksum/artifact, manifest disagreement, or consumer failure blocks |
| 6 | `./gradlew qualityGate --console=plain` | SHA; Java 21 local result and matching Java 21/newer-JDK CI matrix run IDs | Any failure blocks |

`git diff --check` and task/index/roadmap link/dependency validation follow. Each specialized root task
must exclude every other specialized root from its graph; the one deliberate combined lane is
`consumerSmoke` depending on G8-003's validated staging. Reused compilation outputs do not merge
outcomes. No command is hidden inside a release aggregate and no failure can be waived because a
different lane passed.

The candidate SHA/version are validated against the exact forms above and HEAD/tree cleanliness is
checked both before the first lane and after the last. All six rows must refer to that same candidate.
A change to code, build logic, workflow, metadata,
README/CHANGELOG content, fixture, license/provenance, or task/design contract before the decision
invalidates the complete matrix and requires all lanes again. A duration fluctuation is recorded;
G7's hard semantics/counters and one-time accepted cache decisions remain authoritative. Windows/
macOS/other-architecture absence does not block the exact Linux x86_64 claim, but any broader wording
is removed.

### Artifact, license, and support reconciliation

The candidate artifacts are exactly API, core, AWT, shapefile, and image I/O. G8 stages them twice
from fresh repositories, preserves the first immutable-artifact manifest outside the second staging
cleanup, and requires a byte-identical second manifest. This compares every primary POM/module/JAR
SHA-256 and deterministic checksum sidecar while deliberately excluding validated repository
`lastUpdated` metadata. The consumer runs in the second staging invocation; it is accepted only after
the comparison succeeds. G8 records both independently computed manifest SHA-256 values, row counts,
and primary byte totals. Coordinates, candidate version, dependency scopes, Java 21 variants, reproducible archives,
POM/SCM/license metadata, sources/Javadocs, `META-INF/LICENSE`, and the clean consumer result must
agree. No default snapshot, absolute path, workspace coordinate, repository, credential, example,
test, corpus, native/performance support resource, or stale version is accepted.

The license audit accounts for root BSD-3-Clause production/source material, every G5 corpus license/
redistribution decision, G2/G5/G6 repository-authored native fixtures, Javadoc tool legal assets
already carried in their archives, and the Apache-2.0 Gradle wrapper in the source checkout. The
wrapper and corpus are not Maven runtime contents. No empty `NOTICE` is added; a notice exists only if
material actually redistributed requires attribution beyond its carried license. Checksums are
recomputed rather than copied from the first successful build, and rebuild disagreement is diagnosed
rather than selecting a preferred result.

The final support statement is deliberately narrow:

- published artifacts require Java 21 and are verified on Java 21 plus the named newer-JDK
  compatibility lane, not every future JDK;
- Level 1 production modules are JDK-only, with Swing/Java2D as the supported toolkit boundary;
- Native Image is verified only for the recorded Ubuntu 24.04 Linux x86_64 GraalVM Java 21 lane;
  Windows, macOS, Linux AArch64, other distributions, and cross-platform compatibility are unverified;
- Level 1 comprises the bounded G2 symbol/vector, G3 interaction/measurement, G4 source/CRS, G5
  shapefile, G6 PNG/JPEG/world-file, and G7 performance profiles; and
- editing/export, arbitrary SVG, GeoJSON, GeoTIFF, tiles, terrain/elevation, general CRS
  transformation, raster reprojection/warping, and portable performance guarantees are not claimed.

G8-004 puts this final proposed wording into the candidate before any lane. The G8-001 checkpoint and
every row then decide whether that candidate may be released; no user-facing support contract changes
afterward. ROADMAP/task index distinguish implemented-and-verified Level 1 from still-Proposed Level 2
and name G8-004 as the completion point only on `GO`.

### Durable HITL record without evidence self-reference

The checkpoint is **Level 1 release readiness approval**. Its durable record uses the G8-004 Task
Notes backed by this fixed shape:

```text
candidateVersion
candidateRevision
decision = GO | NO-GO
reviewer / reviewDate
externalCiPrerequisite = actor / date / ref / native URL / normal-CI URL
supportStatementApproved
licenseProvenanceAudit = PASS | FAIL + summary
artifactManifest = relative path / sha256 / row count / primary bytes
lanes[] = command / PASS|FAIL|NO_EVIDENCE / environment / CI-or-local reference / report SHA
knownLimitations[]
blockingFindings[]
taskRemoteActionsPerformed = none
```

`GO` requires the exact-candidate external CI prerequisite, every lane `PASS`, and maintainer approval
of version, support wording, licenses,
checksums, artifacts, migrations, and limitations. `NO-GO` is a valid recorded checkpoint result but
does not make G8-004 or Level 1 Complete. An internal defect leaves the task Proposed while its owning
gate is fixed. `Blocked` is reserved for a real external requirement—unavailable required Linux CI/
tooling, unresolved redistribution rights, or absent maintainer approval—not a failing test.

A record cannot contain its own commit SHA and measured report hashes before it exists. After all
candidate evidence and the decision, one evidence-record commit may change only G8-004 Task Notes/
status, task index/ROADMAP completion state, and the G8 decision/traceability record. It names the
immutable tested candidate SHA and is explicitly not the artifact revision. README/CHANGELOG already
belong to the tested candidate. Any production/build/workflow/fixture/license or other documentation change is outside this
exception and invalidates every lane. The record-only diff reruns `qualityGate`, task/index/DAG/link
validation, and whitespace before its local commit; it does not relabel those checks or the record
commit as six-lane candidate evidence. A later release action must publish/tag the named candidate
content or rerun the matrix on any different chosen revision.

No push, workflow dispatch, remote publication, signing, tag, GitHub Release, credential lookup, or
release API call is performed here. The separately recorded maintainer push/CI trigger is a HITL input,
not an action authorized to this task.

### Holistic Level 1 and G8 simplicity closeout

The completed top/mid-level design has one clear runtime path:

```text
mundane-map-api
  +-- mundane-map-core -> API
  +-- mundane-map-awt -> API + core
  +-- mundane-map-io-shapefile -> API + selected core algorithms
  +-- mundane-map-io-image -> API + selected core algorithms
```

API owns immutable toolkit-neutral geometry, portrayal, interaction, CRS/source/raster, limits, and
diagnostic contracts. Core owns JDK-only projection/viewport, packed geometry/index, clipping/
simplification, measurement, hit testing, and resampling. AWT is the sole Swing/Java2D boundary and
owns MapView state, explicit renderer/decoder composition, and only evidence-retained private render
cache. Shapefile and image remain independent AWT-free static format facades returning shared source
contracts. Examples, architecture, native, corpus, rendering, performance, and consumer projects are
non-published support with separate verification lanes.

Developer composition remains explicit but small: create the few immutable registries once at the
application boundary, open a format through its static facade, bind the owned/borrowed source to
`MapView`, and close the view/source owner. Snapshot `Layer` remains the easy embedded-data path. No
omnibus runtime context, plugin discovery/SPI, general topology engine, generic cache/index framework,
background worker system, release plugin, BOM, JPMS layer, public performance knob, or native helper
library has earned a place. If examples show repetitive setup, examples improve before a new public
abstraction is considered.

One candidate record, six independent lanes, one five-artifact manifest, and one maintainer decision
are sufficient release governance. G8 therefore keeps the library as simple as possible but no
simpler than its lifecycle, hostile-input, Native Image, interoperability, and downstream-consumer
evidence require.
