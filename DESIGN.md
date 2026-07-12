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
sentinel envelope for an empty layer. Layer order and feature order are rendering order. G1 changes
`FeatureStyle` only to fix a verified contract defect; the approved G2 migration is defined below.

## Symbols and vector graphics

### Symbol contracts and placement profile (G2-001)

Symbols are immutable, toolkit-neutral feature portrayals in `mundane-map-api`. They contain colors,
primitive measurements, vector or raster data, and stable renderer keys, but no Java2D classes,
viewport references, mutable builders, or renderer callbacks. The contract shape is deliberately
small:

```text
Symbol
  +-- role: MARKER | LINE | FILL | LEGACY_GEOMETRY
  +-- rendererKey: stable explicit key
  +-- opacity: 0..1
  +-- MarkerSymbol -> default MARKER role and MarkerPlacement
  +-- LineSymbol -> default LINE role
  +-- FillSymbol -> default FILL role
  +-- CompositeSymbol -> non-empty, role-homogeneous ordered children
```

`MarkerSymbol`, `LineSymbol`, and `FillSymbol` are open interfaces so a consumer can pair an immutable
custom value with an explicitly registered renderer. Their Javadocs require immutable state, stable
value equality, and a namespaced renderer key; built-in implementations are records or final value
classes with defensive collection and primitive-array copies. A renderer key is a validated string
value, not a `Class`, reflection token, visitor, or discovery hook. `CompositeSymbol` is a built-in
root `Symbol` whose role is inferred from its children and whose own renderer key selects explicit
composite rendering.

The three role interfaces provide their role as a default. A custom value must implement exactly one
of them and must not override that role. `CompositeSymbol` recursively rejects empty children, mixed
roles, and `LEGACY_GEOMETRY`, then stores the first child's role. `FeatureStyle` is the sole permitted
`LEGACY_GEOMETRY` value and is recognized by exact value type, not merely by a claimed role. The
`Feature` constructor recursively validates this shape and the geometry-role match; it rejects direct
root implementations, multi-role implementations, role overrides, and legacy impostors with
`SYMBOL_ROLE_MISMATCH`. Renderers repeat the inexpensive outer role/type check before casting so a
mutable or dishonest external implementation fails deterministically rather than producing a
`ClassCastException`.

A marker symbol couples one vector or raster graphic to `MarkerPlacement`. A Level 1 line symbol
describes centerline paint and a `SymbolLength` width; composition supplies casing, and G2-004 adds
optional marker endpoints to that same contract. A fill symbol describes the polygon interior and may
reuse one line symbol as its ring outline. Closed-ring rendering never applies that line symbol's
endpoint markers. Solid and hatch fills are implementations of `FillSymbol`, not another style tree.

Geometry and symbol roles are checked before rendering: points accept marker symbols, lines accept
line symbols, and polygons accept fill symbols. Later multipoint and multipart geometries retain the
role of their component geometry. A mismatch is not silently ignored or coerced; feature construction
reports `SYMBOL_ROLE_MISMATCH` with feature identifier, geometry kind, and symbol role.

#### Minimal Level 1 values and task staging

The final Level 1 built-in values are intentionally concrete:

- `VectorMarkerSymbol` owns a toolkit-neutral path, a finite positive-area view box, fill color,
  optional `SymbolStroke`, placement, and opacity. The path uses the sole Level 1 `EVEN_ODD` fill
  rule; open subpaths contribute to stroke but not fill.
- `SymbolStroke` owns a color and positive `SymbolLength`. Its Level 1 cap and join are fixed at round,
  preserving G1 output without public cap/join policy types. Absence means no stroke; transparent
  color paints nothing. Dashes and alternate caps/joins are deferred.
- `SolidLineSymbol` owns one `SymbolStroke`, opacity, and, after G2-004, optional start and end marker
  symbols. Composite line symbols provide casing by drawing complete child lines in order.
- `SolidFillSymbol` owns a fill color, optional line-symbol ring outline, and opacity. Polygon paths
  retain even-odd hole behavior. G2-004 adds hatch implementations of the same `FillSymbol` role.

G2-002 introduces the root/marker contracts, renderer key and symbol exception, vector path and view
box, and a fill-only vector marker value. Its centered, unrotated logical-screen-size static factory
owns a fill color and opacity but no stroke; all eight built-ins use closed filled silhouettes in this
first slice. G2-003 adds `MarkerPlacement`, size/length values, optional `SymbolStroke`, and composite
symbols. The G2-002 factory keeps the same source contract and delegates to centered screen placement
with no stroke. G2-004 then introduces solid line, solid fill, endpoints, and hatch values. This
ordering avoids an early dependency on placement/length types and converges on the single final value
shapes above.

The G2-002 convenience uses primitive screen measurements rather than a temporary record shape. The
final class can add its canonical placement and stroke representation in G2-003 while the factory
remains stable. No temporary public marker or parallel placement type is introduced.

#### Renderer identity, staging, and failures

`SymbolRendererKey` is an exact, case-sensitive ASCII value with at least two dot-separated segments;
each segment matches `[a-z][a-z0-9-]*`. Whitespace, uppercase, empty segments, and normalization are
rejected. Built-ins reserve `io.github.mundanej.map.symbol.*`; applications use a namespace they own.
Renderer lookup identity is the pair `(SymbolRole, SymbolRendererKey)`, so role validation precedes
lookup and a key cannot change a feature's role.

Until G2-005, `MapView` uses one package-private, closed built-in dispatcher with explicit branches
for only the symbol value classes delivered so far. It uses no public extension API, reflection, or
discovery; a custom key predictably reaches the unregistered failure. G2-005 replaces that dispatcher
with an immutable, instance-owned `SymbolRendererRegistry`. An instance-owned builder is created
either preloaded by a default factory with the same explicit built-in functions or empty for isolated
tests and applications; `build()` freezes its entries. Public builder registration cannot claim the
reserved built-in prefix and cannot replace an existing role/key pair. The resulting registry is
passed to `MapView`, whose convenience constructor uses the built-in registry; there is no static
mutable registry.

Each registry entry contains an explicit role/key pair and a renderer that validates its expected
value shape before use; no `Class` token is the lookup key. If a custom value claims a built-in key or
otherwise reaches a renderer with the wrong value shape, painting reports
`SYMBOL_RENDERER_VALUE_MISMATCH`. Missing lookup reports `SYMBOL_RENDERER_NOT_REGISTERED`; duplicate
registration is rejected while constructing the immutable registry. These symbol failures use a
narrow runtime exception exposing a stable code and immutable string context through accessors;
messages are human-readable but not a parsing contract. G4 may place the same codes and context into
its general diagnostic carrier without renaming them.

Composite and geometry-role structure fail at value or `Feature` construction, when all required
information is available. Registry availability and renderer-value compatibility fail fast when the
paint traversal reaches the feature: the current paint pass aborts, the child graphics context is
still disposed, and the feature is never silently skipped. There is no asynchronous diagnostic sink
before G4; any later continuation policy must be an explicit source/rendering decision rather than a
change in these error codes.

#### Placement values and units

`MarkerPlacement` is a value composed of a `SymbolSize`, anchor, x/y offset, rotation, and rotation
mode. `SymbolSize` has positive finite width and height, one unit, and a square-size convenience.
`SymbolUnit` has exactly two Level 1 values:

- `SCREEN_PIXEL` is one logical component coordinate before device/HiDPI scaling. It remains the same
  visual size as the map zoom changes.
- `MAP_UNIT` is one projected-world unit in the active viewport, not a geographic ground metre. Its
  visual size changes with `worldUnitsPerPixel`; geographic-distance sizing is out of scope.

Both offsets are finite and use the size's unit, avoiding mixed-unit transforms in one placement. In
screen units, positive x is right and positive y is down. In map units, positive x and y follow the
projected world axes and the viewport converts that vector to screen space. The feature's projected
position plus the converted offset is the marker anchor point; offset is not rotated with the marker.

The supported anchors are center plus the eight compass positions: north, north-east, east,
south-east, south, south-west, west, and north-west. An anchor selects the corresponding point on the
marker's nominal rectangle before stroke expansion. After unit conversion, that rectangle is
`[0, width] x [0, height]` in local coordinates, with x right and y down. A vector marker independently
maps its finite positive-area view-box x and y ranges onto the entire nominal rectangle. A raster
marker similarly maps its full pixel-cell rectangle onto it. Width and height are therefore explicit
and may stretch intrinsic aspect ratio; an aspect-preserving convenience computes one dimension but
does not change the transform. A custom marker renderer must use the same nominal rectangle contract.

Vector strokes are centered on the transformed path and may extend outside the nominal rectangle;
that expansion never changes size or anchor. Raster filtering also does not change it. A composite has
no group rectangle: each marker child computes and anchors its own nominal rectangle at the common
feature position. Rendering translates the chosen nominal anchor to the local origin, rotates the
graphic about the origin, and finally places the origin at the offset feature position. Renderers
cannot reorder those operations or derive anchoring from platform rasterization.

Rotation is a finite number of degrees canonicalized to `[0, 360)`, including canonical positive
zero. At zero degrees the marker's local positive x axis points right in a north-up view; positive
angles appear clockwise. Let `b` be the clockwise screen bearing of projected-world-positive x,
computed from the transformed screen delta between an anchor `p` and `p + (1, 0)` using screen-y-down
`atan2(deltaY, deltaX)`. For configured rotation `r`:

- `SCREEN_RELATIVE` produces screen bearing `normalize(r)`;
- `MAP_RELATIVE` produces `normalize(b + r)`.

The current unrotated viewport has `b = 0`, so both modes draw the same. Tests use a synthetic rotated
transform to preserve the sign and distinction without adding viewport rotation to G2. For G2-004
auto-oriented endpoints, the attachment replaces the ordinary zero bearing with the outward tangent's
screen bearing and then adds `r`; it does not add `b` a second time. The start outward tangent points
from the first following distinct coordinate toward the start, and the end outward tangent points from
the last preceding distinct coordinate toward the end. The same marker rendered away from an endpoint
uses its declared screen- or map-relative mode normally.

Reusable `SymbolLength` values pair one strictly positive finite magnitude with either unit and apply
to later line widths, endpoint sizes, and hatch measurements. Optional absence, not zero or a negative
sentinel, disables a stroke or endpoint. A marker graphic's intrinsic or normalized bounds do not
choose its unit or placement policy.

#### Opacity and composition

Every symbol opacity is finite and inclusive from zero through one. Effective paint alpha is the
product of inherited composite opacity, symbol opacity, and the paint or pixel alpha, with channel
rounding performed only at the AWT boundary. Zero effective opacity performs no painting.

A composite defensively copies at least one child, rejects null children and mixed roles, and paints
children from first/bottom to last/top. Nested composites are allowed and preserve their boundaries
for equality and diagnostics; they are not silently flattened. A composite adds opacity and ordering,
not a second placement transform. Each marker child evaluates its own placement against the same
feature position, while line and fill children evaluate the same geometry. Transforming a whole
marker group therefore uses equal explicit placements on its children rather than ambiguous nested
offset or rotation inheritance.

#### `FeatureStyle` migration

G2 uses the project's documented pre-1.0 freedom for one clean source break rather than maintaining
parallel portrayal state. In G2-002, `Feature` replaces its `FeatureStyle style` component with
`Symbol symbol`, and the accessor becomes `symbol()`. `FeatureStyle` temporarily implements `Symbol`
as a deprecated, geometry-dependent compatibility value. Existing constructor calls that pass a
`FeatureStyle` continue to compile, but callers of `style()` migrate to `symbol()`; binary
compatibility is not claimed for `0.x` snapshots.

The compatibility renderer preserves the G1 circle, solid line, solid polygon, and point-label output
and accepts `FeatureStyle` only directly on a feature. It cannot be nested in composites, named in a
catalog, or used as an endpoint. New point examples migrate in G2-002, and all remaining examples
migrate when solid line and fill symbols arrive in G2-004. `FeatureStyle` and its geometry-dependent
role remain deprecated through the Level 1 `0.x` release and are removed before `1.0.0`; G8 verifies
that the supported-version statement matches this policy.

Point labels are not part of a marker symbol. Through Level 1, `MapView` retains the G1 behavior of
drawing one non-blank point-feature name after the complete legacy, marker, or marker-composite symbol
and before the next feature; marker migration therefore does not silently remove labels. The label is
opaque G1 dark gray and does not inherit symbol or composite opacity. Its baseline is four logical
pixels right of and two logical pixels above the axis-aligned union of the final rotated nominal
marker rectangles; stroke expansion is ignored. This reduces exactly to the G1
`centerX + diameter / 2 + 4`, `centerY - diameter / 2 - 2` placement for a centered legacy circle.
Offsets, anchors, non-square sizes, rotations, and composite children are therefore deterministic.
General label symbols, font policy, placement, and collision handling remain Level 2 work.

Public value construction rejects nulls, non-finite measurements, non-positive sizes, out-of-range
opacity, blank keys, empty composites, and mixed-role composite children immediately with
`NullPointerException` or `IllegalArgumentException` naming the field. Built-in equality includes
canonical measurements, placement, ordered children, colors, and renderer keys. A well-formed
symbol paired with the wrong feature geometry, a dishonest custom role implementation, or a legacy
impostor uses `SYMBOL_ROLE_MISMATCH`; renderer lookup/value failures use their stable paint-time codes.

The G2-001 HITL checkpoint is maintainer approval of this interface boundary, the two units, nine
anchors, transform order, rotation modes, composition rules, and deliberate `FeatureStyle` migration
before production work begins in G2-002.

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
| 2026-07-12 | Replace geometry-dependent `FeatureStyle` with role-specific symbols during `0.x`. | One explicit toolkit-neutral portrayal model is simpler and more extensible than parallel legacy and symbol state. |
| 2026-07-12 | Use logical screen pixels or projected map units for Level 1 symbol measurements. | The two explicit units cover stable UI marks and zoom-scaled cartography without implying geographic distance. |

## Task design traceability

Design status is independent of implementation task status. `Draft` is ready for review, `Reviewed`
has completed independent review, and `Approved` is the committed top- and mid-level design baseline.
Implementation tasks remain Proposed until their code, tests, and task-specific evidence are complete.

| Task | Design coverage | Status |
| --- | --- | --- |
| G0-001 | Java baseline, repository resolution, normal verification, and publication staging | Approved |
| G0-002 | Module graph, architecture enforcement, prohibited mechanisms, and exception policy | Approved |
| G1-001 | First-slice geometry, viewport, rendering, interaction, example, and native verification | Approved |
| G2-001 | Symbol roles, renderer keys, placement units, transforms, composition, and style migration | Approved |
