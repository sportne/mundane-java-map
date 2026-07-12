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

### Toolkit-neutral vector paths and built-in markers (G2-002)

#### Packed path value

`VectorPath` is an immutable sequence of commands with two packed arrays: one `byte[]` opcode per
command and one `double[]` containing all operands in command order. The public `VectorPathCommand`
enum fixes the encoding and arity:

| Command | Operands | Meaning |
| --- | ---: | --- |
| `MOVE_TO` | 2 | Start a subpath at x, y. |
| `LINE_TO` | 2 | Add a straight segment to x, y. |
| `QUADRATIC_TO` | 4 | Add control x/y and end x/y. |
| `CUBIC_TO` | 6 | Add first control, second control, and end x/y. |
| `CLOSE` | 0 | Close the current subpath to its move point. |

The value clones both factory inputs. Its minimal read surface is `commandCount()`,
`commandAt(commandIndex)`, `ordinateCount()`, and `ordinateAt(globalOrdinateIndex)`, plus
`toCommandArray()` and `toOrdinateArray()` defensive copies. Ordinates use one global flat index;
sequential consumers keep a cursor and advance it by `commandAt(i).arity()`. Raw opcode bytes and
mutable storage are never exposed. Equality, hash code, and string form are content-based.
`coordinateEnvelope()` is the conservative finite envelope of every move/end/control coordinate, not
the exact extrema of Bezier curves. Counts and that envelope are cached. There is no object per
segment, linked node, Java2D type, or implicit path parser.

`VectorPath.of(VectorPathCommand[], double...)` is the exact packed factory and validates command
arity before reading operands. A small `VectorPath.Builder` supplies fluent `moveTo`, `lineTo`,
`quadraticTo`, `cubicTo`, and `close` methods, then delegates to the same validation. The builder is a
single-owner construction aid, not a symbol value: `build()` makes defensive packed copies and closes
the builder only after validation succeeds. Later mutation or a second successful build fails with
`IllegalStateException`; a failed build leaves the builder usable so the caller can add a missing
segment. A rejected mutation validates before appending and leaves the prior builder state unchanged.
`VectorPathCommand.arity()` is public, while the private byte encoding uses an explicit command/code
switch rather than enum ordinal and is not a persistence format.

Path validation is a deterministic state machine:

- all operands are finite and the first command is `MOVE_TO`;
- a move starts one active subpath; another move is allowed only after the prior subpath contains at
  least one drawing segment, whether open or closed;
- line, quadratic, and cubic commands require an active, not-yet-closed subpath;
- close requires at least one drawing segment, may occur once, and leaves only a later move legal;
- build requires at least one drawing segment and consumes exactly the arity sum—neither missing nor
  trailing ordinates are accepted.

Open subpaths are valid because cross-format paths and later SVG profiles may need strokes. Under the
fixed Level 1 even-odd fill rule, an open subpath is strokable but contributes no fill until explicitly
closed. Programmer input failures use field-naming `NullPointerException`, `IllegalArgumentException`,
or `IllegalStateException`; they are not source-data diagnostics.

#### Vector marker value and normalized built-ins

`VectorMarkerSymbol.filledScreen(VectorPath path, Envelope viewBox, Rgba fill, double sizePixels,
double opacity)` is the exact G2-002 factory. `Envelope` is reused as the toolkit-neutral view-box
value; the symbol exposes its path, view box, fill, opacity, and a transitional
`screenSizePixels()` read accessor so the separate AWT module can render this slice. G2-003 replaces
that accessor with the canonical public `placement()` value in the same pre-1.0 source-migration
window; callers migrate to `placement().size()`, while the `filledScreen` factory signature remains
stable. The factory creates centered, square, screen-relative, zero-offset, zero-rotation output.
Every path coordinate and control point must fall inside the finite positive-area view box, and every
subpath must be closed, making the nominal marker rectangle a real bound and avoiding Java2D's
implicit closure of open fill paths. General `VectorPath` values may remain open for later strokes.
G2-003 keeps this factory while adding the general placement and optional stroke representation
approved above; its fill pass includes only closed subpaths.

The built-in vector-marker renderer slot is `(MARKER,
io.github.mundanej.map.symbol.vector-marker)`, exposed by the immutable
`VectorMarkerSymbol.RENDERER_KEY`. The closed dispatcher resolves that exact role/key pair and then
requires the exact final `VectorMarkerSymbol` value class; a different value claiming the key reports
`SYMBOL_RENDERER_VALUE_MISMATCH` before any cast or paint.

`BuiltInMarker` in `mundane-map-api` is the stable enum `CIRCLE`, `SQUARE`, `TRIANGLE`, `DIAMOND`,
`CROSS`, `X`, `STAR`, and `ARROW`. `BuiltInMarkers` in `mundane-map-core` has the exact public surface
`viewBox()`, `path(BuiltInMarker)`, and
`filledScreen(BuiltInMarker, Rgba, double sizePixels, double opacity)`. `path` returns the reusable
immutable value and `filledScreen` delegates to `VectorMarkerSymbol.filledScreen`; neither exposes
cached arrays. Every shape uses the common view box
`[-0.5, -0.5, 0.5, 0.5]`, local x right, local y down, and a closed filled silhouette:

Every built-in has one contour, starts at its north-most vertex (west-most on a tie), proceeds
clockwise as seen in y-down local space, and ends with one `CLOSE`. Polygonal markers do not repeat the
start coordinate before close. The circle's fourth cubic necessarily ends at its move coordinate to
complete the final curved quarter, then uses `CLOSE` to record explicit contour closure. Generated
trigonometric values use `StrictMath`; results within `1e-15` of zero are stored as positive zero. The
exact construction is:

- circle starts `(0, -0.5)` and uses four clockwise cubic arcs through east, south, west, and north.
  With `c = 0.5 * 4 * (sqrt(2) - 1) / 3`, the first controls are `(c, -0.5)` and `(0.5, -c)`; the
  remaining controls are their quarter-turns. Its commands are move, four cubics, close.
- square visits north-west, north-east, south-east, south-west. Triangle visits `(0, -0.5)`,
  `(0.5, 0.5)`, `(-0.5, 0.5)`. Diamond visits north, east, south, west.
- cross has arm half-width `h = 1/6` and visits `(-h,-0.5)`, `(h,-0.5)`, `(h,-h)`, `(0.5,-h)`,
  `(0.5,h)`, `(h,h)`, `(h,0.5)`, `(-h,0.5)`, `(-h,h)`, `(-0.5,h)`, `(-0.5,-h)`, `(-h,-h)`.
- X applies the y-down clockwise transform `x' = s * (x - y) / sqrt(2)`,
  `y' = s * (x + y) / sqrt(2)`, with `s = 3 / (2 * sqrt(2))`, to the cross vertices, then cyclically
  starts the unchanged clockwise sequence at its north-most/west-most transformed vertex.
- star emits ten vertices at angles `-90 + 36 * i` degrees, alternating radius `0.5` for even `i` and
  `0.2` for odd `i`.
- arrow visits `(0.1,-0.4)`, `(0.5,0)`, `(0.1,0.4)`, `(0.1,0.15)`, `(-0.5,0.15)`,
  `(-0.5,-0.15)`, `(0.1,-0.15)`.

Shape constants are defined once in core and tested for closure, view-box containment, symmetry,
orientation, and stable command structure. Consumers select a named enum rather than depending on raw
coordinate constants. These names are marker identities only; the immutable named symbol catalog is
introduced in G2-005.

#### Java2D conversion and rendering slice

`mundane-map-awt` owns one package-private converter from `VectorPath` to a package-private pair of
fresh `Path2D.Double` values with `WIND_EVEN_ODD`: `strokePath` contains every subpath, while
`fillPath` contains only subpaths terminated by an explicit `CLOSE`. The converter records each
subpath's command range and materializes it into the fill path only after seeing close; Java2D therefore
cannot implicitly fill an open contour. It maps move/line/quadratic/cubic/close directly, preserves
subpath order, and performs no coordinate-axis flip because marker-local y already points down. It
does not cache Java2D values or expose them outside AWT; G7 decides caching from evidence. A mixed
open/closed path test proves stroke includes both contours and fill includes only the closed one.

The closed G2 dispatcher has the exact legacy slot `(LEGACY_GEOMETRY,
io.github.mundanej.map.symbol.legacy-feature-style)` exposed by `FeatureStyle.RENDERER_KEY`, and gains
the vector-marker slot defined above. For a point vector marker it projects the feature coordinate, maps the marker
view box to the centered nominal screen square, fills through the converted even-odd path using color
alpha multiplied by symbol opacity, and returns the nominal rectangle for the existing point-label
step. Line and polygon features continue through the compatibility branch until G2-004. Unknown keys,
wrong roles, and wrong value shapes use the G2-001 fail-fast codes; there is still no extensible
registry in this task.

The basic viewer remains on the compatibility value where that is required to preserve its outlined
G1 point appearance. New vector-marker test fixtures use `Feature.symbol()` and the fill-only factory.
Offscreen tests render every built-in through a real `Feature`, `InMemoryLayer`, and `MapView` with a
blank feature name; they assert tolerant actual paint extents and shape-specific inside/outside regions,
not that a silhouette occupies every edge of its nominal rectangle. A separate package-private layout
test asserts the numeric nominal rectangle and label-baseline calculation without depending on font
rasterization.

Rendering tests also cover color-alpha times symbol-opacity blending over a known background, zero
opacity leaving the background untouched, the legacy `FeatureStyle` point retaining both fill and
outline, `SYMBOL_RENDERER_NOT_REGISTERED` for an unknown custom key, and
`SYMBOL_RENDERER_VALUE_MISMATCH` for a custom marker claiming the built-in vector key. Converter tests
inspect `PathIterator` command kinds, operands, conservative bounds, mixed subpaths, close segments,
and even-odd winding independently of map rendering. API tests cover global index access, defensive
copies, content equality, every state-machine rejection, failed-build recovery, and successful-builder
consumption.

G2-002 adds no SVG parser, general path boolean operation, public Java2D adapter, renderer registry,
composition, placement transform, or cache. Its observable result is one toolkit-neutral packed path
model and all eight built-ins rendering through the same real map path.

### Symbol placement and composition (G2-003)

#### Final placement values

G2-003 adds the final immutable values in `mundane-map-api`:

- `SymbolUnit`: `SCREEN_PIXEL` or `MAP_UNIT`;
- `SymbolSize(width, height, unit)`, with positive finite dimensions and `square(...)` conveniences;
- `SymbolLength(value, unit)`, with one positive finite magnitude;
- `SymbolAnchor`: `CENTER`, `NORTH`, `NORTH_EAST`, `EAST`, `SOUTH_EAST`, `SOUTH`, `SOUTH_WEST`,
  `WEST`, or `NORTH_WEST`;
- `SymbolRotationMode`: `SCREEN_RELATIVE` or `MAP_RELATIVE`;
- `MarkerPlacement(size, anchor, offsetX, offsetY, rotationDegrees, rotationMode)`.

Offsets are finite and use `size.unit()`. Rotation is reduced modulo 360 into `[0, 360)`, and both
negative and positive zero become positive zero, so equivalent rotations have equal values. Offsets
and opacity similarly canonicalize negative zero. `MarkerPlacement.centeredScreen(sizePixels)` is the
migration target for G2-002's factory. Nulls, non-positive dimensions/lengths, and non-finite numbers
fail during value construction.

`SymbolStroke(color, width)` is the one reusable Level 1 stroke value. Absence, represented by
`Optional.empty()`, means no stroke; a present width is always positive. Stroke unit is independent of
marker size unit, allowing a map-sized marker with a stable screen-pixel outline or the converse
without changing placement semantics. Cap and join remain fixed round as approved in G2-001.

`VectorMarkerSymbol.of(path, viewBox, fill, Optional<SymbolStroke>, placement, opacity)` becomes the
canonical factory and exposes all six values. The existing `filledScreen(...)` factory delegates to
it, and the transitional `screenSizePixels()` accessor is removed; this is the planned pre-1.0 source
migration to `placement()`. Fill uses only explicitly closed subpaths, stroke uses every subpath, and
view-box containment still applies to all endpoints and controls.

#### Toolkit-neutral placement math

`mundane-map-core` exposes only the cross-module implementation values needed by AWT and later G3
hit testing:

```text
MapScreenBasis.of(Coordinate xUnitScreenDelta, Coordinate yUnitScreenDelta)
MarkerTransform: read-only coefficients plus nominalScreenBounds
SymbolTransforms.marker(Envelope viewBox, MarkerPlacement placement,
                        Coordinate featureScreen, MapScreenBasis basis) -> MarkerTransform
SymbolTransforms.screenLength(SymbolLength length, MapScreenBasis basis) -> double
```

`MapScreenBasis` is a final immutable class with a validating public `of` factory and read access to
the two vectors, determinant, uniform scale, and x-axis screen bearing. The vectors must be finite and
non-zero. Their determinant must be finite and negative, preserving projected y-up to screen-y-down
orientation. After normalizing each vector, their dot product must be within `1e-12` of zero and their
lengths must differ by no more than `1e-12` relative. G2 therefore supports the rotation plus uniform
scale with screen-axis reversal that `MapViewport` actually supplies and rejects positive-determinant
orientation, anisotropic scale, and shear with a field-naming `IllegalArgumentException`; raster affine
transforms remain a separate G6 concern.

The scalar screen pixels per map unit is `sqrt(abs(determinant))`. Map-unit offsets still use the full
linear combination `offsetX * xBasis + offsetY * yBasis`; map-unit width, height, and stroke width use
that one scalar. Under map-relative rotation, local y-down is perpendicular clockwise from the x-basis
bearing. Screen-unit measurements use their values directly in logical pixels. The same scalar policy
therefore remains valid for G2-004 line strokes because a similarity transform scales every line
normal equally.

`MarkerTransform` maps vector view-box coordinates directly to final screen coordinates:

```text
screenX = m00 * localX + m01 * localY + m02
screenY = m10 * localX + m11 * localY + m12
```

Anchor fractions `(ax, ay)` are exactly: north-west `(0,0)`, north `(0.5,0)`, north-east `(1,0)`, west
`(0,0.5)`, center `(0.5,0.5)`, east `(1,0.5)`, south-west `(0,1)`, south `(0.5,1)`, and south-east
`(1,1)`. Let the view box be `(vx, vy, vw, vh)`, converted nominal dimensions be `(w, h)`, and the
converted feature-plus-offset anchor be `(px, py)`. For a path coordinate `(x, y)`:

```text
u = (x - vx) * w / vw - ax * w
v = (y - vy) * h / vh - ay * h
screenX = px + cos(theta) * u - sin(theta) * v
screenY = py + sin(theta) * u + cos(theta) * v
```

This is the clockwise matrix in y-down screen coordinates. `theta` is the normalized screen bearing
converted to radians: configured rotation for screen-relative placement or the x-basis
`atan2(deltaY, deltaX)` plus configured rotation for map-relative placement. `StrictMath` supplies the
trigonometric operations. With `sx = w / vw`, `sy = h / vh`,
`tx = -vx * sx - ax * w`, and `ty = -vy * sy - ay * h`, the stored coefficients are:

```text
m00 = cos(theta) * sx          m01 = -sin(theta) * sy
m10 = sin(theta) * sx          m11 =  cos(theta) * sy
m02 = px + cos(theta) * tx - sin(theta) * ty
m12 = py + sin(theta) * tx + cos(theta) * ty
```

AWT constructs `AffineTransform(m00, m10, m01, m11, m02, m12)` in that Java constructor order. The
axis-aligned nominal screen bounds are derived inside core from the four transformed view-box corners
and exclude stroke.

`MapScreenBasis.of` checks determinant, vector normalization, relative scale, and derived basis scale;
any zero, non-finite, overflowed, or unsupported result is a field-naming `IllegalArgumentException`,
even when the two input vectors were individually finite. After a valid basis exists, every converted
dimension, offset product/sum, angle result, coefficient, transformed corner, and screen stroke width
is checked before constructing a public coordinate, envelope, AWT transform, or stroke. Overflow at
that stage throws the symbol exception code `SYMBOL_TRANSFORM_NON_FINITE` with the failed quantity in
context. A screen stroke width must also remain positive and finite after conversion to Java2D's
`float`; a value above `Float.MAX_VALUE` uses the same stable failure instead of reaching
`BasicStroke`. No incidental Java2D exception defines this contract.

`MarkerTransform` is a final immutable public core class because AWT and G3 need to read the same
coefficients and nominal bounds. Its constructor is package-private; only `SymbolTransforms` can
create the coefficient/bounds pair, so callers cannot manufacture inconsistent state.

`MapView` derives the basis without a CRS assumption: project the feature once, transform that point
and points one projected unit along x and y through `MapViewport`, then subtract the anchor screen
coordinate. The current viewport produces `(1 / worldUnitsPerPixel, 0)` and
`(0, -1 / worldUnitsPerPixel)`; core tests also inject a valid rotated basis. Singular,
positive-determinant, anisotropic, sheared, or non-finite bases are programmer/configuration errors in
this gate, not source diagnostics.

#### AWT marker painting

The AWT vector renderer converts the core coefficients to an `AffineTransform` and creates a
screen-coordinate `Shape` from each converted fill/stroke path. It does not install the marker scale
on the caller's `Graphics2D`, so an independently converted screen- or map-unit `SymbolStroke` width
is not scaled twice. Stroke width in screen coordinates is its logical-pixel value or its map-unit
value converted by `SymbolTransforms.screenLength`, using the same uniform basis scale as marker
dimensions and later line strokes.

Each renderer operates on a child graphics context and disposes it in `finally`. It sets round
`BasicStroke`, color, and `AlphaComposite.SRC_OVER` only on that child. Effective composite/symbol
opacity remains a floating-point multiplier until Java2D combines it with color alpha; the renderer
does not pre-round an alpha channel. Fill precedes stroke. Zero effective opacity skips painting but
still computes nominal bounds for the independent point-label layout.

#### Ordered composites

`CompositeSymbol.of(List<? extends Symbol> children, double opacity)` defensively copies a non-empty
list, recursively rejects null, legacy, roleless, multi-role, and mixed-role children, and stores the
inferred role. Its renderer key is the reserved
`io.github.mundanej.map.symbol.composite`. Equality preserves nested structure and declared order.

The closed dispatcher adds the composite role/key branch and renders children first-to-last against
the same feature and geometry. It passes `parentEffectiveOpacity * composite.opacity()` into each
child, which then multiplies its own opacity. It applies no group placement or flattening. Marker-child
nominal bounds are unioned for the one feature label even when a child or parent has zero opacity;
opacity affects paint, not layout. A nested composite therefore changes only grouping, equality,
diagnostic context, and opacity multiplication.

This slice exercises marker composites because solid line and fill symbols arrive in G2-004. The
contract already permits those roles, but the closed dispatcher reports their renderer keys as
unregistered until the matching working behavior exists. `FeatureStyle` remains valid only directly
on a feature and is rejected from every composite.

#### Verification boundary

API tests cover canonical rotation, both unit values, nine anchors, finite offsets, optional stroke,
opacity, equality, list copying, nested order, and every invalid value/composite. Core tests assert the
six transform coefficients and nominal bounds for every anchor, both units, offsets, non-square view
boxes, screen/map rotations, a valid rotated similarity basis, and zoom changes. Negative tests cover
zero, positive-determinant, anisotropic, sheared, and non-finite bases plus overflow in every derived
transform or stroke-width stage, asserting `SYMBOL_TRANSFORM_NON_FINITE` where public inputs were
individually valid and the basis had already passed validation. A separate boundary case proves that
finite basis vectors whose determinant overflows fail `MapScreenBasis.of` with
`IllegalArgumentException`, not the post-basis symbol code.

Offscreen AWT tests prove screen-sized markers remain constant while map-sized markers scale with
zoom, screen-relative marks remain display-fixed while map-relative marks follow a synthetic basis,
fill/stroke ordering, independent stroke units, overlapping composite draw order, nested alpha
multiplication, transparent layout, and the unchanged `filledScreen` factory. A graphics-state test
seeds transform, clip, composite, paint, and stroke on the parent and proves they are unchanged after
painting. Basic compatibility features continue to render through the legacy branch. Hit testing,
line endpoints, raster icons, catalogs, registry extension, and caching remain later tasks.

### Line endpoints and hatch fills (G2-004)

#### Solid line and fill values

G2-004 completes the three Level 1 geometry roles with these immutable API values:

- `SolidLineSymbol.of(SymbolStroke stroke, Optional<Symbol> startMarker,
  Optional<Symbol> endMarker, double opacity)`; marker options must have `MARKER` role, including a
  marker composite, and cannot be legacy values;
- `SolidFillSymbol.of(Rgba fill, Optional<Symbol> outline, double opacity)`; an outline must have
  `LINE` role, including a line composite;
- `HatchFillSymbol.of(HatchPattern pattern, SymbolStroke stroke, SymbolLength spacing,
  SymbolRotationMode rotationMode, Optional<Symbol> outline, double opacity, int maxSegments)`.

The renderer slots are respectively `io.github.mundanej.map.symbol.solid-line`,
`io.github.mundanej.map.symbol.solid-fill`, and `io.github.mundanej.map.symbol.hatch-fill`, exposed by
each final class's `RENDERER_KEY`. Convenience factories omit endpoints/outline and use the hatch
default limit of 8,192 generated segments per feature. The explicit limit must be positive. Hatch
spacing and stroke width are independently unit-bearing and positive; overlap is allowed when stroke
is wider than spacing. A hatch has a transparent background, so a solid background is expressed by a
role-homogeneous fill composite with `SolidFillSymbol` first rather than another hatch field.

Line opacity applies to its centerline and endpoint markers; each endpoint symbol then applies its own
and any nested composite opacity. Fill opacity applies to its interior/hatches and owned outline; the
outline's own opacity follows. Solid polygon fill remains even-odd. A fill outline reuses the same line
symbol renderer on each closed ring, suppressing endpoint markers at every nesting level.

The closed dispatcher adds the three exact role/key/type branches. A line composite draws each
complete child line—including its endpoints—before the next child, so casing order is explicit. A fill
composite similarly draws each complete fill and outline in child order. Wrong roles, keys, or value
types retain the existing fail-fast symbol codes.

#### Centerlines and endpoint orientation

The AWT line renderer projects each coordinate once, creates one open screen-coordinate path per line
part, and draws it with round cap/join after converting `SymbolStroke` through the validated viewport
basis. Its internal input is an ordered list of parts even while `LineStringGeometry` supplies one;
future multipart geometry can reuse the renderer without changing public symbol contracts. Endpoint
markers are evaluated independently for every non-empty part. A solid line is part-major: for each
part in geometry order it paints centerline, start marker, then end marker before advancing, so
arrowheads cover that part's caps and later parts overlay earlier parts. A line composite is
child-major: its first child processes every part before the next child begins, preserving declared
casing order across multipart overlaps.

`mundane-map-core` provides
`LineTangents.outwardScreenBearings(CoordinateSequence screenCoordinates, String featureId,
int partIndex)` returning the immutable
`LineEndpointBearings(OptionalDouble startBearingDegrees, OptionalDouble endBearingDegrees)` value.
It compares projected screen coordinate pairs with primitive `==`, so signed zeros are equal and NaN
is impossible by construction, then skips repeated coordinates. The start bearing points from the
first following distinct coordinate toward the first coordinate; the end bearing points from the last
preceding distinct coordinate toward the last. If all coordinates coincide, both optionals are empty
and configured endpoints and the zero-length centerline part are skipped without attempting a
transform or relying on platform hairline behavior. This structural case is not a renderer failure or
source diagnostic in G2. Any overflow while subtracting otherwise finite projected coordinates uses
`SYMBOL_TRANSFORM_NON_FINITE` before `atan2`, with fixed `featureId`, decimal `partIndex`, `endpoint`,
and `quantity=line-tangent-delta` context.

The tangent method rejects a blank feature ID or negative part index as programmer input; part indexes
follow the geometry's declared order from zero.

Core adds `SymbolTransforms.markerAtScreenBearing(Envelope viewBox, MarkerPlacement placement,
Coordinate endpointScreen, MapScreenBasis basis, double outwardBearingDegrees)`, identical to normal
marker placement except that the supplied tangent bearing replaces the usual screen/map rotation base
and the placement's configured clockwise degrees are added once. Size, anchor, unit conversion,
offset, finite checks, and opacity are otherwise unchanged; the stored rotation mode does not add a
second base while the marker is auto-oriented. This override flows through marker composites. The
built-in east-pointing arrow's nominal path tip lands exactly on an endpoint when its marker placement
uses `EAST` anchor and zero offset. With a configured offset it lands at the endpoint plus the converted
offset, and a centered marker stroke may paint beyond the nominal tip. Screen/map offsets remain in
their documented coordinate axes rather than tangent-local axes.

#### Bounded hatch layout

`HatchPattern` has exactly `FORWARD_DIAGONAL`, `BACKWARD_DIAGONAL`, and `CROSS_DIAGONAL`. In y-down
screen coordinates, forward diagonal `/` has base bearing 315 degrees, backward diagonal `\` has 45
degrees, and cross emits both. Screen-relative patterns use that display bearing and a lattice anchored
at screen origin. Map-relative patterns add the x-basis bearing and anchor the lattice at projected
world origin transformed through the viewport, so orientation and phase follow map navigation.
Spacing independently follows its declared unit: screen-pixel spacing stays visually constant, while
map-unit spacing changes with zoom.

All four combinations are supported deliberately:

| Rotation mode | Spacing unit | Observable lattice behavior |
| --- | --- | --- |
| Screen-relative | Screen pixel | Screen angle, screen-origin phase, and pixel spacing stay fixed while geometry pans/zooms below. |
| Screen-relative | Map unit | Screen angle and screen-origin phase stay fixed; pixel spacing expands/contracts with zoom. |
| Map-relative | Screen pixel | Angle and transformed-world-origin phase follow map navigation; separation remains constant in pixels. |
| Map-relative | Map unit | Angle, transformed-world-origin phase, and separation all follow map navigation and scale. |

Tests cover each combination rather than treating rotation mode and spacing unit as coupled enums.

AWT intersects the polygon's transformed screen bounds with the viewport rectangle and any inherited
graphics clip before asking core for work. An empty intersection produces no segments.
`HatchLayouts.cover(HatchPattern pattern, Envelope bounds, Coordinate latticeOrigin,
double orientationBaseBearing, double spacingPixels, int maxSegments, String featureId)` receives that
finite rectangle and all work policy in one call. Core adds 315 degrees for forward, 45 degrees for
backward, or emits both for cross. The result is immutable `HatchSegments`
backed by packed doubles `x1,y1,x2,y2` per segment, with `segmentCount()`, `x1(segmentIndex)`,
`y1(...)`, `x2(...)`, `y2(...)`, and a defensive `toArray()` copy.

For unit direction `d = (cos(angle), sin(angle))` and normal `n = (-d.y, d.x)`, core projects the four
rectangle corners relative to the lattice origin onto `n`. Integer lattice indices run from
`ceil(minProjection / spacing)` through `floor(maxProjection / spacing)`. Before allocation, the
algorithm computes both orientation counts and checks finite arithmetic, an exactly representable
non-negative total, `4 * total` array size, and the shared feature budget. Only after that preflight
does it allocate once. Each infinite lattice line is intersected with the rectangle by a parametric
slab calculation; corner-only zero-length intersections are omitted but still count conservatively
toward the preflight budget. Cross diagonal emits forward first and backward second.

If the required count cannot be represented or exceeds `maxSegments`, layout allocates nothing and
throws the stable symbol code `HATCH_SEGMENT_LIMIT_EXCEEDED`. Its context always has exactly
`featureId`, `pattern`, `requiredSegments`, `maxSegments`, and `countKind=candidate`. The required value
is the conservative total candidate lattice-line count across both cross orientations before
corner-only omissions, encoded as a decimal signed-long value or the stable string `overflow` when it
cannot be represented. `pattern` is the enum name and `maxSegments` is decimal. A total whose packed
`4 * count` length exceeds a Java array also uses that limit code and the same keys. Any other derived
non-finite coordinate or transform uses `SYMBOL_TRANSFORM_NON_FINITE`. There is no truncation because
partial patterns would vary with iteration order and hide unsafe input.

#### Clipped hatch and outline painting

The AWT hatch renderer builds one even-odd screen path containing polygon exterior and holes, creates
a child graphics context, intersects its existing clip with that path, and draws only the bounded core
segments. The child clip makes holes and the outer boundary transparent without constructing polygon
boolean operations in API/core. Pattern stroke uses the same round `SymbolStroke`, alpha, unit
conversion, and graphics-state isolation as vector markers.

After disposing the clipped hatch context, an optional outline renders on a fresh child without the
interior clip; otherwise half of a centered boundary stroke would be lost. Solid fill similarly paints
the even-odd interior first and its outline second. Closed-ring context suppresses all endpoint markers
but preserves line/composite order and opacity. Rings are passed as one ordered closed-part list:
exterior first, then holes in declared order. A solid outline processes rings in that order; a
composite outline is child-major, so each child processes every ring before the next child starts.
Invalid polygon topology is neither repaired nor reinterpreted here.

#### Verification and migration

Core tests cover outward start/end bearings, signed-zero and leading/trailing repeats, all-coincident
lines, fixed overflow context, endpoint bearing overrides, all four hatch mode/unit combinations,
exact lattice indices, corner tangencies, packed copies, finite/overflow checks, shared cross limits,
fixed diagnostic keys/sentinel, and just-below/at/above-limit cases. API tests cover role validation,
defensive option/list ownership, units, opacity, defaults, and invalid limits.

Offscreen tests cover screen- and map-unit line widths, composite casing order, independently optional
start/end markers, arrow-tip anchoring, repeated points, endpoint offsets/rotation/opacity, all three
hatches, every rotation/spacing combination across pan and zoom, translucent composites, clipping,
polygon holes, part-major leaf order, child-major composite/outline order, and stable over-limit
failure without allocation. Assertions use interior samples, tolerant bounds, and geometry invariants
rather than whole images.

The basic viewer migrates points to stroked vector-circle markers, routes to `SolidLineSymbol`, and
regions to `SolidFillSymbol` with a line outline, preserving G1 appearance and labels while exercising
the new feature `symbol()` accessor. Separate compatibility tests retain deprecated `FeatureStyle`
through the Level 1 `0.x` policy. Dashes, textures, gradients, polygon repair, raster icons, hit
testing, and render caches remain out of scope.

### Raster icons, catalogs, and renderer registration (G2-005)

#### Bounded raster icon value

`RasterIconSymbol` is a final immutable `MarkerSymbol` in `mundane-map-api` with canonical factory:

```text
of(int width, int height, int[] rgbaPixels, MarkerPlacement placement,
   RasterInterpolation interpolation, double opacity)
```

Pixels are row-major from top-left, x right and y down. Each unpremultiplied packed int is exactly
`0xRRGGBBAA`; no AWT color model or byte order leaks into the value. The public read surface is
`width()`, `height()`, `rgbaAt(x,y)`, `toRgbaArray()` defensive copy, `placement()`,
`interpolation()`, and `opacity()`. Equality and hash code include dimensions, pixel contents,
placement, interpolation, and opacity.

Dimensions are positive and at most `MAX_DIMENSION = 4096`; `width * height` is checked in `long`
before any clone and cannot exceed `MAX_PIXELS = 4_194_304` (16 MiB of packed pixel payload). The input
length must equal that product exactly. Validation checks dimensions and the `long` product before the
pixel reference and length, then validates the remaining fields, so hostile dimensions do not require
a matching allocation to test. The fixed icon limits are public constants and intentionally smaller
than later raster-source limits.

`RasterInterpolation` is the toolkit-neutral `NEAREST` or `BILINEAR` enum and is reused by the G6
raster renderer rather than duplicated. The canonical placement may stretch the intrinsic aspect ratio
because width and height are explicit. Two conveniences keep common icon construction explicit:

```text
nativeScreenSize(int width, int height, int[] rgbaPixels,
                 RasterInterpolation interpolation, double opacity)
screenWidth(int width, int height, int[] rgbaPixels, double widthPixels,
            RasterInterpolation interpolation, double opacity)
```

Both use centered, zero-offset, zero-degree, screen-relative `SCREEN_PIXEL` placement.
`nativeScreenSize` uses the intrinsic dimensions as logical pixels. `screenWidth` requires a positive
finite width and computes `widthPixels * ((double) height / width)` in that order; the positive finite
intrinsic ratio is formed before multiplication so an intermediate cannot overflow when the final
proportional height is representable. The resulting height must also be positive and finite before
delegation. The renderer slot is
`(MARKER, io.github.mundanej.map.symbol.raster-icon)`, exposed by
`RasterIconSymbol.RENDERER_KEY` and guarded by exact value type.

This task accepts decoded pixels only. It adds no encoded PNG/JPEG decoder, file path, URL, resource
name, or discovery behavior. G2-007 may use a test-module loader for one explicitly named native
resource; production encoded-image sources begin in G6.

#### Java2D icon rendering

For non-zero effective opacity, the AWT renderer creates a fresh `BufferedImage.TYPE_INT_ARGB` for the
paint call and converts each toolkit pixel with
`argb = (rgba << 24) | ((rgba >>> 8) & 0x00ff_ffff)`; it does not premultiply. Zero effective opacity
still computes and returns nominal bounds but skips image allocation and conversion. The lack of a
conversion cache is deliberate until G7 evidence. The icon pixel-cell view box is
`(0, 0, width, height)`, so the existing `SymbolTransforms.marker` path applies every anchor, unit,
offset, rotation, and nominal-bound rule without icon-specific placement math.

On a disposable child graphics context, nearest interpolation selects
`VALUE_INTERPOLATION_NEAREST_NEIGHBOR` and bilinear selects `VALUE_INTERPOLATION_BILINEAR`. The renderer
uses the core coefficients as the image-to-screen `AffineTransform`, applies effective opacity through
`SRC_OVER`, draws once, and returns nominal marker bounds for the point-label union. Pixel alpha,
symbol/composite opacity, and any caller background combine at paint time. No platform-default
interpolation is observable.

#### Immutable named catalogs

`NamedSymbol(name, symbol)` and `NamedSymbolCatalog` live in `mundane-map-api`. A name must be non-blank
and equal to its own `strip()` result; interior whitespace and Unicode are retained, comparisons are
exact and case-sensitive, and no Unicode/case normalization occurs. The symbol must satisfy ordinary
role validation and cannot be `LEGACY_GEOMETRY`.

`NamedSymbolCatalog.of(List<NamedSymbol>)` defensively copies entries in declaration order, detects an
exact duplicate before building its lookup map, and stores both an immutable ordered list and lookup.
An empty catalog is valid. The catalog implements `Iterable<NamedSymbol>`; equality, `entries()`, and
iteration preserve entry order. Its remaining minimal surface is `size()`, `find(name)`, and
`require(name)`: `find` returns `Optional.empty()` for a valid absent name, while `require` throws
stable code `SYMBOL_CATALOG_MISSING`. A malformed lookup name is an immediate field-naming argument
error, not a missing entry.

Duplicate construction throws `SYMBOL_CATALOG_DUPLICATE`. Its fixed context keys are `name`,
`firstIndex`, and `duplicateIndex`, with decimal zero-based indexes. Missing context has exactly
`name`. No catalog lookup consults a resource, classpath, parent catalog, default catalog, or renderer
registry. Applications explicitly choose and pass a catalog; immutable built-in catalogs in later
examples are ordinary values, not mutable global registries.

#### Explicit AWT renderer registry

G2-005 deletes the closed dispatcher and replaces it with final immutable
`SymbolRendererRegistry` in `mundane-map-awt`. It is owned by each `MapView`; there is no setter or
static mutable holder. `MapView(Projection)` delegates to a constructor that also accepts a registry
and uses `SymbolRendererRegistry.builtIn()` by default.

The implementation prerequisite is G2-004, not merely G2-003: the final registry consumes the line,
fill, and hatch values and replaces their closed-dispatch branches. The existing G2-005 task-card edge
is stale and must be changed to `Depends on: G2-004` before production implementation starts. G2-004
and G2-005 are not implementation-parallel because they both change the AWT dispatcher and `MapView`;
G2-005 owns the final integration and removal. This design-only sequence leaves task metadata untouched
until planning metadata is next maintained.

Registration identity is the approved `(SymbolRole, SymbolRendererKey)` pair. A single-use
instance-owned builder has two entry points: `builder()` is empty and `builderWithBuiltIns()` is
preloaded through private built-in registration. `register(role, key, renderer)` accepts only
application namespaces and the three public roles, rejects `LEGACY_GEOMETRY`, and rejects the reserved
`io.github.mundanej.map.symbol.*` prefix. A reserved-key attempt reports
`SYMBOL_RENDERER_RESERVED_KEY`; an existing pair reports `SYMBOL_RENDERER_DUPLICATE`. Both carry
exactly `role` as the enum name and `key` as the exact validated key string. Private built-in
registration is the only path that can install legacy or reserved pairs. `build()` defensively copies
entries and consumes the builder; any later builder operation fails with a field-naming state error,
and registries and previously built instances never observe later builder state.

`builtIn()` installs an explicit source-listed set: legacy style; vector and raster markers; composite
under marker, line, and fill roles; solid line; solid fill; and hatch fill. Nothing scans packages,
classes, annotations, services, resources, or module paths. An empty/custom registry has no hidden
fallback. Missing lookup and false value support retain `SYMBOL_RENDERER_NOT_REGISTERED` and
`SYMBOL_RENDERER_VALUE_MISMATCH` respectively.

#### Renderer extension surface

`AwtSymbolRenderer` is the one public extension interface in the AWT module:

```text
boolean supports(Symbol value)
SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context)
```

The registry calls `supports` after role/key lookup and before `render`; false produces the stable
value-mismatch code without casting. Built-ins require their exact final value class. A consumer
renderer may use language-level `instanceof` but receives no `Class` lookup token and performs no
discovery. `supports` must be deterministic, side-effect-free, and non-throwing for every non-null
`Symbol`; a renderer exception is not translated into a misleading registry diagnostic.

`AwtSymbolRenderContext` is a paint-call-scoped facade constructed only by `MapView`. It exposes the
looked-up `SymbolRole`, feature ID, original `featureGeometry`, current `renderGeometry`, projection
and viewport snapshots, inherited opacity, closed-ring and optional endpoint-bearing context,
source-to-screen conversion, validated `MapScreenBasis`, and an optional marker anchor in screen
coordinates. Root dispatch uses the feature geometry for both geometry accessors. An endpoint marker
uses the original line as `featureGeometry`, a `PointGeometry` for its source endpoint as
`renderGeometry`, and the already-projected endpoint as its marker anchor. A fill outline uses the
original polygon as `featureGeometry`, a `LineStringGeometry` over the selected immutable ring as
`renderGeometry`, and `closedRing = true`. The marker anchor is present for a point marker and
line-endpoint marker and absent for line/fill rendering.

The only public recursive operation is exact same-role dispatch:

```text
SymbolRenderResult renderChild(Symbol child, double opacityMultiplier)
```

It requires `child.role()` to equal the current role, retains both current geometries plus marker,
bearing, and closed-ring context, and returns the child's result so marker composites can union bounds.
A role mismatch uses `SYMBOL_ROLE_MISMATCH` before lookup. The multiplier must be finite in `[0,1]`;
the child context inherits `current inherited opacity * multiplier`, and the child renderer still
applies its own symbol opacity. Built-in composites pass their composite opacity. Public custom
renderers cannot request a role transition; they either use same-role composition or paint their own
toolkit behavior.

Two package-private context operations implement the validated built-in role transitions. Endpoint
dispatch accepts a marker, the owning-line opacity multiplier, a source `PointGeometry`, an already
projected screen anchor, and finite outward bearing; it derives a `MARKER` context with the bearing
override and returns marker bounds, which the line renderer deliberately discards. Closed-ring dispatch
accepts a line symbol, owning-fill opacity multiplier, and one closed `CoordinateSequence`; it derives
a `LINE` context over a `LineStringGeometry` sharing that immutable sequence, suppresses endpoints at
every same-role recursion level, and requires a none result. No cross-role operation is exposed to a
consumer renderer. This keeps the public extension surface small while allowing the built-in line and
fill contracts to retain their G2-004 behavior.

`createGraphics()` returns a disposable child. The facade does not expose `MapView`, mutable layer
state, registry mutation, the package-private derived-dispatch operations, or the parent graphics
object. A renderer must not retain the context or returned graphics beyond the call and must dispose
every created child.

`SymbolRenderResult` is an immutable AWT value containing `Optional<Envelope>
nominalMarkerBounds`. `none()` represents line/fill output; `markerBounds(...)` and `union(...)`
support markers and composites. MapView uses the final point result once for label placement. Bounds
are layout, not a painted-pixel promise, and remain present for zero-opacity markers. Registry dispatch
requires a non-null result: `MARKER` must return bounds; `LINE` and `FILL` must return none; and the
legacy renderer returns bounds only for point geometry. A wrong result reports
`SYMBOL_RENDERER_INVALID_RESULT` with exactly `role` and `key` rather than leaking a null or incidental
geometry failure.

The context is intentionally one facade rather than public marker/line/fill renderer hierarchies. A
custom renderer can inspect the already-validated role and geometry, use AWT only inside the AWT
module, and recurse explicitly, while MapView retains lifecycle, diagnostics, and EDT ownership.

#### Verification boundary

API tests cover pixel format/indexing, row order, defensive copies, dimension/product/length limits,
native/proportional factories, both interpolation values, catalog name rules, order/equality,
duplicates, missing versus malformed lookup, composites, and legacy rejection. Limit tests exercise
oversized dimensions before allocating pixel arrays; proportional tests include a finite near-overflow
screen width whose intrinsic aspect ratio is one.

Registry tests cover the complete built-in set, empty and preloaded builders, custom explicit marker
rendering through `MapView`, marker-anchor context, returned composite-bound unions, same-role recursive
opacity, derived endpoint and closed-ring contexts, public cross-role rejection, duplicate/reserved/
unregistered/value-mismatch/invalid-result failures, application rejection of legacy-role
registration, builder consumption, and isolation among two registries and two views. Architecture tests
prove that API/core have no AWT signatures, registries have no mutable static state or class-token
lookup, and no discovery API or service descriptor is introduced.

Offscreen tests use a non-square multi-color icon to prove row orientation, alpha, every anchor,
rotation, both size units, nearest versus bilinear sampling, opacity, nominal bounds, point-label
integration, and composite order. Parent graphics state remains unchanged. The implementation adds no
resource scanning, encoded-image support, mutable catalog, cache, or global registry.

### Symbol gallery and render regression (G2-006)

#### Runnable gallery document

G2-006 adds the working `examples:symbol-gallery` application; it is included in settings and in the
normal checked-project list in the same change, never as an empty example module. `SymbolGallery.main`
opens its window on the Swing event-dispatch thread, while `createGalleryPanel()` constructs the same
content without a top-level window so tests can run headlessly. The example adds no library API.

Package-private immutable `GallerySection` and `GalleryCase` records define the committed gallery
inventory. They defensively copy their ordered values and use stable IDs matching
`[a-z][a-z0-9-]*` separately from display titles. Duplicate section or case IDs fail construction.
`GalleryDocument.create()` returns exactly four ordered sections:

- `markers` shows the eight `BuiltInMarker` values by enum name, a non-square multicolor raster icon
  with nearest and bilinear interpolation, a translucent marker over an opaque reference patch, and an
  ordered marker composite;
- `placement` shows all nine anchors against visible reference crosses, non-zero positive and negative
  x/y offsets, screen-pixel and map-unit sizes, declared rotations, and screen-relative and map-relative
  marker modes;
- `lines` shows a plain line, a child-ordered cased composite, distinct start/end markers, and an
  east-pointing arrowhead following horizontal, rising, and falling endpoint tangents;
- `fills` shows a solid fill, each of the three basic hatch patterns, a solid-plus-hatch composite,
  a line-symbol outline, and a polygon hole that exposes a lower reference color.

Every case contributes at least one real `Feature` in an `InMemoryLayer`; blank feature names keep map
font rendering out of the evidence. A Swing legend beside each map supplies the case display names and
short interaction cue. Each tab creates `SymbolRendererRegistry.builderWithBuiltIns().build()` and
passes that instance to its `MapView`, and symbols are retrieved from one immutable
`NamedSymbolCatalog` chosen by the example. The gallery therefore demonstrates the consumer-facing
catalog and explicit-registry path rather than relying on a global or hidden default.

The four maps use fixed initial logical sizes, Web Mercator coordinates in small valid extents, and
explicit fit padding. Pan and zoom remain enabled so a reviewer can observe the difference between
screen- and map-unit sizing and between screen- and map-anchored hatch phase. With Level 1's unrotated
viewport, screen- and map-relative marker rotations may have the same visible base bearing; both cases
remain named in the gallery, while the synthetic rotated-basis distinction stays in the G2-003
automated transform tests. The example does not add viewport rotation merely to make the gallery more
dramatic.

The headless construction test runs creation on the EDT and asserts the exact section/case ID matrix,
all eight marker enum values, all anchors, both units and rotation modes, both raster interpolation
values, all hatch patterns, non-empty feature lists, immutable inventory, catalog resolution, and an
explicit-registry constructor path for every view. It does not require a registry getter, open a
`JFrame`, wait for input, or treat component text pixels as rendering evidence.

#### HITL visual checkpoint

The named checkpoint is **G2 symbol-gallery visual approval**. A maintainer launches
`:examples:symbol-gallery:run`, inspects every tab at its initial fit, then performs at least one pan and
one zoom. Approval covers silhouette recognizability, anchor/reference alignment, configured offsets
and rotations, screen-versus-map sizing while zooming, composite order, endpoint direction, nearest
versus bilinear icon appearance, opacity, hatch phase/clipping, and the polygon hole. The task cannot be
Complete until its Notes record reviewer, date, pass/fail, OS, desktop scale, and full JDK
vendor/version. A failed review returns to implementation and automated verification; it is not hidden
by regenerating a screenshot. Screenshots may be placed in ignored build output for discussion but are
not committed as an oracle.

#### Dedicated regression lane

Portable rendering evidence lives in the AWT project's custom
`src/renderRegressionTest/java` source set rather than a new test-only module or the normal unit-test
source set. Gradle explicitly makes `renderRegressionTestImplementation` extend `testImplementation`
and `renderRegressionTestRuntimeOnly` extend `testRuntimeOnly`, then adds `sourceSets.main.output` to
the custom compile and runtime classpaths. The registered JUnit Platform `renderRegressionTest` task
uses that source set's output classes as `testClassesDirs` and its runtime classpath as `classpath`.
It is not wired into `test`, `check`, `checkAll`, or `qualityGate`. Normal formatting, Checkstyle, and
SpotBugs conventions may still inspect the custom Java sources; only the raster-executing `Test` task
stays outside the normal gate.

The root `renderRegression` verification task depends only on that focused test task. A separate Java
21 Linux CI job invokes `./gradlew renderRegression --console=plain`; the existing quality job
continues to invoke only `qualityGate`. This gives failures a distinct lane and does not make platform
raster evidence a normal developer prerequisite.

The test task forces `java.awt.headless=true`, disables JUnit parallel execution, and uses
`BufferedImage.TYPE_INT_ARGB` at fixed logical component dimensions. Each scenario factory creates an
unfitted view. On the EDT, the runner sets the exact component size and declared background, disables
component double buffering, and invokes the scenario's fixed-viewport or fit callback only after that
size is effective. It explicitly fills the entire image with the opaque background under
`AlphaComposite.Src`, then paints the view through a fresh `SRC_OVER` graphics child whose clip is the
full image or the scenario's declared inherited clip. It never assumes that an opaque bare
`JComponent` or a UI delegate clears the buffer. Feature names are blank and built-in renderers are
explicitly installed. Tests do not query a screen device, display scale, installed font, locale, wall
clock, random source, or default image interpolation.

Package-private test-only `RenderScenario` values contain a stable ID, dimensions, unfitted view
factory, post-size viewport callback, optional inherited clip, and ordered invariant callbacks. The
committed scenarios cover:

- every vector-marker silhouette with interior/exterior probes and tolerant painted bounds;
- all anchor positions, offsets, screen/map sizing, declared rotation, and nominal composite union;
- child paint order and opacity over a known background;
- a non-square RGBA icon's row orientation, alpha, nearest regions, and bilinear transition;
- line caps, cased child order, start/end attachment, and arrow direction on three tangents;
- even-odd polygon holes and fill-outline order; and
- forward, backward, and cross hatches clipped to exterior, holes, and the inherited graphics clip.

These are small fixtures owned by the lane, not a reusable rendering-test framework and not copies of
the complete gallery screen. A dynamic-test display name begins with the scenario ID, and every
assertion message names both that ID and the failed invariant.

#### Portable assertions and diagnostics

Assertions use one package-private immutable tolerance profile; individual scenarios cannot widen it.
The initial fixed values are a per-channel color tolerance of 18, painted/background maximum-channel
delta of 24, bounds margin of 2 logical pixels, matching-region minimum of 70%, exclusion-region
maximum of 2%, and hatch-occupancy interval of 8% through 65%. Probe regions are at least 7 by 7 pixels
and inset at least 3 pixels from an expected edge. Fixture colors and expected opaque composite colors
are chosen with pairwise maximum-channel distance of at least 64. A helper test computes that minimum
and proves `2 * colorTolerance < minimumPaletteDistance`, so two expected colors cannot both classify
the same pixel. Changing this one profile requires review of every scenario; there are no per-OS or
per-JDK exceptions hidden in fixtures.

Bounds use pixels whose maximum channel distance from the known background exceeds the fixed threshold.
Interior/color checks require the fixed matching proportion rather than one edge pixel. Ordering uses
overlap regions well inside both children; hatch checks use the fixed occupancy band plus outside/hole
exclusion regions; raster interpolation checks color-set behavior and the existence of a blended
transition rather than one implementation-specific sample. Toolkit-neutral command, transform, and
nominal-bound arithmetic remains asserted exactly or by numeric tolerance in its normal unit tests.

Focused assertion-helper tests operate on synthetic, directly filled images and include positive
controls plus negative controls for no paint, swapped child order, a filled hole, reversed raster rows,
and a missing hatch. Each negative control must produce `AssertionError`; this prevents a broadly
tolerant implementation from satisfying the lane merely because some pixels changed.

On invariant failure, the runner catches the original `AssertionError` and computes
`build/render-regression/diagnostics/<scenario-id>.png`. It creates the directory lazily and calls
`ImageIO.write`; a `false` return is treated as an image-write failure. The runner then throws a new
`AssertionError` whose message names the scenario, invariant, and attempted path and whose cause is the
original assertion. Any directory or image-write exception is added as suppressed to that new error,
never substituted for the rendering failure. The path is deterministic, `clean` removes it, and the
root build-output ignore rule covers it. Passing tests write nothing. No reference PNG, checksum,
baseline-update command, or platform-specific threshold is committed.

The root README documents the gallery command, the purpose and separation of `renderRegression`, and
the diagnostic path. G2-006 is the sole task that introduces this command; earlier task validation
continues to use focused tests and `qualityGate`.

#### Verification boundary

Example tests prove headless construction, complete named coverage, EDT use, explicit registry/catalog
construction, and immutable fixtures. Regression-lane tests prove every listed scenario and also assert
that the environment is headless. CI review confirms quality and rendering are separate jobs and that
neither Gradle aggregate depends on the other. The manual record proves intended appearance on one
named desktop; it does not justify cross-platform pixel identity. SVG, text/label regression, encoded
icon resources, Native Image resources, benchmarks, general snapshot infrastructure, and committed
screenshots remain out of scope.

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
| 2026-07-12 | Store vector paths as packed opcodes and ordinates with fixed even-odd fill. | The complete Level 1 command set stays toolkit-neutral, compact, deterministic, and directly convertible to Java2D. |
| 2026-07-12 | Compute marker placement as a toolkit-neutral affine result before AWT painting. | One tested transform order keeps anchors, units, offsets, and rotation identical across current and future renderers. |
| 2026-07-12 | Generate hatch lines only over the clipped screen extent with an explicit per-feature budget. | A simple packed lattice plus Java2D polygon clip preserves holes while bounding allocation and work. |
| 2026-07-12 | Register AWT symbol renderers by explicit role/key in an instance-owned immutable registry. | Consumers can extend rendering without reflection, discovery, toolkit leakage, or global mutable state. |
| 2026-07-12 | Verify rendering through invariant-based headless scenarios in a separate lane. | Tolerant bounds, regions, and ordering catch material regressions without promising cross-platform pixel identity or burdening the normal gate. |

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
| G2-002 | Packed vector paths, normalized built-in markers, Java2D conversion, and first render slice | Approved |
| G2-003 | Immutable placement/stroke values, core marker transforms, AWT painting, and composites | Approved |
| G2-004 | Solid line/fill values, endpoint tangents, bounded hatch layout, clipping, and migration | Approved |
| G2-005 | Bounded raster icons, immutable named catalogs, explicit AWT registry, and custom rendering | Approved |
| G2-006 | Runnable symbol gallery, named visual checkpoint, portable render scenarios, and separate lane | Approved |
