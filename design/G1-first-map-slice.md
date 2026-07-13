# G1 — First map slice design

Project index: [DESIGN.md](../DESIGN.md).

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
`FeatureStyle` only to fix a verified contract defect; the approved migration is defined in the
[G2 symbol contract](G2-symbols-and-vector-graphics.md#symbol-contracts-and-placement-profile-g2-001).

## Projection pipeline

```text
source coordinate -> map projection -> projected world coordinate -> viewport -> screen pixel
```

`Projection` owns forward and inverse projection. The first-slice implementation accepts
longitude/latitude degrees, currently clamps latitude to the representable Web Mercator limit, and
produces projected meters; G4-002 replaces that baseline with explicit endpoint CRS metadata and
strict domain failure. `MapViewport` never interprets a CRS; it owns only projected-world to screen math.
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
features in order. Because a bare custom `JComponent` has no UI delegate that is guaranteed to clear
its pixels, an opaque `MapView` first fills its full current bounds with its configured background on
every paint. G1 preserves the direct geometry dispatch already in the component; explicit
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

Pointer events cover `MOVED` and `CLICKED`. They retain the originating finite screen coordinates. The
G1 baseline always carries the inverse-projected coordinate; G4-002 evolves that value to optional so
an event over finite viewport space outside the projection domain still routes without clamping or
throwing.
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

The Native Image convention explicitly selects an executable rather than relying on the build-tool
plugin's shared-library value. The Java 21 AWT native libraries use JNI and one reflective rendering
engine internally, so the support application checks in the minimal `jni-config.json` and
`reflect-config.json` captured from this exact blank-label offscreen scenario. The configuration was
generated with the Java 21 tracing agent, reviewed to contain only JDK/AWT internals plus the inherited
`MapView.coalesceEvents` hook, and proved by running the image; no resource/service metadata, proxy,
serialization, production reflection call, or metadata-repository fallback is introduced. This is
support-module configuration for JDK implementation reachability, not an application discovery API.
Changing the JDK baseline or broadening the smoke requires recapturing and reviewing this bounded
metadata rather than accepting an unreviewed agent directory.

The native lane remains separate from `qualityGate`. Its absence is reported as unavailable rather
than treated as JVM success; when the G1 implementation task is accepted, the named HITL checkpoint
requires a maintainer with GraalVM to record the native command result.

G2 expands that same entrypoint and shared JVM/native method to the three-feature vector, composite,
and exact raster-resource scenario defined by the
[G2 native symbol smoke](G2-symbols-and-vector-graphics.md#native-symbol-resource-smoke-g2-007); it does
not retain a second G1-only executable or parallel assertion path. The one Java 21
resource-metadata entry is test-module configuration, not a production discovery facility. Later
native tasks continue extending representative behavior in this single smoke application until G8
assembles the Level 1 release lane.

### G1 design closeout

G1 adds verification depth, not a second map model. The packed geometry values, immutable viewport,
single AWT component, in-memory layer, and real native render remain the smallest end-to-end slice.
Gate closeout found and fixed only three cross-cutting defects: identity-based listener removal,
consistent zero-width line suppression, and deterministic background clearing. It also made the
existing native executable/metadata assumptions explicit rather than adding a second renderer or
smoke application. The desktop checkpoint confirmed point, line, polygon, fit, primary-drag pan,
cursor-centered wheel zoom, and live pointer coordinates; the Linux Java 21 image executed the same
offscreen scenario. Symbols, generalized tools, hit testing, and source lifecycles stay out of this
gate.
