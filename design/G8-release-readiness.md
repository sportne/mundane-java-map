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
