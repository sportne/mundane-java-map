# G3 — Interaction and measurement design

Project index: [DESIGN.md](../DESIGN.md).

## Interaction tools

### Tool lifecycle and navigation routing (G3-001)

#### Toolkit-neutral event values

G3 keeps the existing `MapPointerEvent`/`MapPointerListener` pair as the small `MOVED` and `CLICKED`
compatibility observer. Tools receive the separate, complete `MapToolEvent`; observer code is not
silently required to handle capture or lifecycle cancellation.

`MapPointerButton` is an immutable non-negative numeric value with named constants `NONE = 0`,
`PRIMARY = 1`, `MIDDLE = 2`, and `SECONDARY = 3`; larger positive numbers represent auxiliary buttons
without adding toolkit types or collapsing their identity. `MapInputModifier` is the fixed enum
`SHIFT`, `CONTROL`, `ALT`, `META`, and `ALT_GRAPH`. `MapToolCancelReason` is
`TOOL_REPLACED`, `TOOL_CLEARED`, `FOCUS_LOST`, `VIEW_DISABLED`, `VIEW_REMOVED`,
`POINTER_EXITED`, `POINTER_STATE_LOST`, or `USER_CANCEL`.

The immutable event has this canonical shape:

```text
MapToolEvent(
    long sequence,
    Type type,
    double screenX,
    double screenY,
    Optional<Coordinate> mapCoordinate,
    MapPointerButton button,
    Set<MapPointerButton> buttonsDown,
    Set<MapInputModifier> modifiers,
    int clickCount,
    double wheelRotation,
    boolean popupTrigger,
    Optional<MapToolCancelReason> cancelReason)

Type = PRESS | DRAG | RELEASE | MOVE | CLICK | WHEEL | CANCEL
```

`sequence` is positive and strictly increasing within one `MapView`; overflow fails before dispatch
rather than wrapping. Screen ordinates, every present map coordinate, and wheel rotation are finite.
G4-002 makes the map coordinate optional: it is empty when the finite viewport sample lies outside
the strict display-to-map operation domain or cannot be represented by the current conversion. The
screen-space event still routes, so hover, capture, release, cancellation, and navigation cannot be
stranded at a CRS boundary. Collections are defensive immutable copies with no nulls, and
`MapPointerButton.NONE` is forbidden inside `buttonsDown`. Modifiers may be present on every type. All
other invariants are total:

| Type | Changed button | Post-event `buttonsDown` | Click count | Wheel | Popup | Cancel reason |
| --- | --- | --- | ---: | ---: | --- | --- |
| `PRESS` | non-`NONE` | contains changed button | `>= 0` | `0` | either | empty |
| `DRAG` | `NONE` | non-empty | `0` | `0` | false | empty |
| `RELEASE` | non-`NONE` | excludes changed button | `>= 0` | `0` | either | empty |
| `MOVE` | `NONE` | empty | `0` | `0` | false | empty |
| `CLICK` | non-`NONE` | excludes changed button | `> 0` | `0` | either | empty |
| `WHEEL` | `NONE` | any valid set | `0` | any finite value | false | empty |
| `CANCEL` | `NONE` | any valid set | `0` | `0` | false | present |

`buttonsDown` is the host-reported complete physical snapshot, not merely buttons whose presses this
view observed. The AWT adapter enumerates the JDK 21-supported button masks 1 through 20 with
`InputEvent.getMaskForButton` against `getModifiersEx()`; the changed press is then added and changed
release removed to normalize platform transition differences. Thus a secondary/auxiliary button held
before pointer entry still prevents sole-button capture, while no `MouseInfo`, global listener, or
timing guess is used. Another toolkit using the public router must provide the same complete-snapshot
semantics.

AWT maps the five keyboard modifiers explicitly and copies precise wheel rotation, click count, and
popup-trigger. A `MOUSE_DRAGGED` event with no physical button, or `MOUSE_MOVED` with a physical button,
is not used to construct an invalid event; it causes `POINTER_STATE_LOST` cancellation and is
suppressed. A cancellation uses the current raw sample when available, otherwise the last successfully
converted sample; before any real sample, it uses the component center converted through the current
viewport and map/display operations. Effective dimensions are always at least one; the map coordinate
may be empty but is never null or non-finite.

#### Tool, result, cursor, and context contracts

The API surface is deliberately four small types:

```text
MapTool
  default onActivate(MapToolContext context)
  MapToolResult onMapToolEvent(MapToolEvent event, MapToolContext context)
  default onDeactivate(MapToolContext context)
  default cursorIntent() -> MapCursorIntent.DEFAULT

MapToolResult = PASS | CONSUME | CAPTURE
MapCursorIntent = DEFAULT | CROSSHAIR | HAND | MOVE

MapToolContext
  mapCrs() -> CrsDefinition
  displayCrs() -> CrsDefinition
  mapToScreen(Coordinate sourceMap) -> Optional<Coordinate>
  screenToMap(double screenX, double screenY) -> Optional<Coordinate>
  requestRepaint()
```

`CAPTURE` is valid only from a `PRESS` when no capture exists and `buttonsDown` contains exactly the
changed non-`NONE` button; it implies consumption. A second/nested capture or capture during a chorded
press is a programmer-state error. `PASS` permits remaining MapView behavior and `CONSUME` suppresses
it. While capture exists, all press/drag/release/click events are still delivered to the same active
tool but suppress defaults; a nonmatching release does not end capture, and the matching release ends
it after the callback. Before another non-cancel event is routed, omission of the captured button from
reconciled `buttonsDown` is state loss except for its own changed `RELEASE`; the router produces one
`POINTER_STATE_LOST` cancel and suppresses that raw event. Wheel input remains independent during
capture only while its button remains down and zooms only when the tool returns `PASS`. A lifecycle or
external-unavailability `CANCEL` result is ignored. For `CANCEL(USER_CANCEL)`, `PASS` permits the
host's prior Escape behavior and `CONSUME` suppresses it after cancellation; `CAPTURE` is illegal for
every cancel. Null results or cursor intents and capture at another event type use the callback-failure
path below.

The router reads `cursorIntent()` after successful activation and after a successful non-cancel event
only when the same session remains installed and the host is available. It never reads the old tool's
intent after cancellation or a pending lifecycle operation; `resume()` performs its separately defined
lookup. A tool may therefore change its EDT-confined cursor state during an ordinary callback without a
cursor mutation method. The accessor must otherwise be side-effect-free and deterministic for the
tool's current state.

Context values are callback-scoped snapshots. The map/display definitions, directional operations,
and viewport used by its coordinate conversions are the same pre-navigation snapshots used to build
the event. Optional conversion is empty only for a domain/unrepresentable coordinate;
an unexpected projection implementation failure still propagates. A repaint request delegates to the
host's ordinary coalescing repaint and does not paint synchronously. The context exposes no `MapView`,
AWT value, layer mutation, viewport mutation, registry, cursor setter, or overlay renderer, and must
not be retained after a callback. Selection and measurement overlays add their own bounded contracts
in G3-003/G3-004 rather than speculating here.

Tools may keep their own EDT-confined interaction state. They must make `onDeactivate` safe after a
partially failed activation and release any tool-owned state idempotently. Toolkit cursors are not
public: AWT maps the four intents only to predefined default, crosshair, hand, and move cursors.

#### One toolkit-neutral router

`MapToolRouter` in `mundane-map-core` is the single call-thread-confined state machine used by
`MapView` and available to another toolkit without Swing dependencies. It owns the active tool by
identity, activation session, captured button/press sequence, last physical button snapshot, quarantined
buttons, release candidate, one-following-click suppression, cancellation arming, and pending
lifecycle operation. Its minimal
surface is:

```text
activeTool() -> Optional<MapTool>
captured() -> boolean
currentCursorIntent() -> MapCursorIntent
setActiveTool(MapTool, MapToolEvent replacementCancel, MapToolContext) -> RouteOutcome
clearActiveTool(MapToolEvent clearCancel, MapToolContext) -> RouteOutcome
route(MapToolEvent, MapToolContext) -> RouteOutcome
cancelInteraction(MapToolEvent externalCancel, MapToolContext) -> RouteOutcome
resume() -> RouteOutcome

RouteOutcome(boolean suppressDefault, boolean captured, MapCursorIntent cursorIntent)
```

Lifecycle arguments named `*Cancel` must be `CANCEL` events with the corresponding reason; an event is
ignored only for first activation or a same-instance no-op. `cancelInteraction` accepts an
external-unavailability reason or `USER_CANCEL`; their distinct resume and coalescing behavior is
described below. Every accepted event sequence must exceed the router's prior sequence; an
out-of-order or reused sequence fails before callbacks and leaves state unchanged. `MapView` does not
expose the router.

Ignoring the cancellation callback on first activation does not mean inheriting an existing gesture.
MapView clears its pending navigation anchor whenever the active-tool identity changes, including the
first installation. If any physical button is down then, the router quarantines those buttons and
isolates their release/click before calling the new tool's activation. No synthetic cancel is delivered
because no prior tool exists. A first activation with no button down has no quarantine side effect.

Setting the same tool instance again is a no-op. A distinct instance—even if `equals`—replaces the old
tool in exact order: `CANCEL(TOOL_REPLACED)`, `onDeactivate`, then new `onActivate`. Clearing uses
`CANCEL(TOOL_CLEARED)` then `onDeactivate`. The old session and capture are cleared before the new
activation can affect routing. A new activation that fails is best-effort deactivated, left inactive,
and its original failure is rethrown with cleanup failure suppressed. If old cancellation or
deactivation fails, cleanup still runs, the old tool is left inactive, the new tool is not activated,
and the first failure is rethrown with later cleanup failures suppressed.

A reentrant host call to `MapView.setActiveTool` or `clearActiveTool` may queue one replacement or
clear operation during a pointer or command callback; a reentrant host focus/enable/removal transition
similarly queues one external cancel. The callback context itself exposes no lifecycle mutation. The
router waits for the callback to unwind and validates its non-null/legal result. On success it discards that
result, applies the pending lifecycle operation before any old cursor lookup or default handling, and
suppresses the triggering event. Repeated external cancels coalesce; a second explicit
replacement/clear request and replacement from activation/cancellation/deactivation callbacks are
programmer-state errors. Recursive pointer or command dispatch is rejected, including pointer from
command, command from pointer, and same-kind recursion. These rules avoid a stale callback installing
capture or a cursor after its tool has been replaced.

Pointer and command dispatch order inside the router is callback, result validation,
pending-operation handling, then cursor lookup only when the same session remains. If the callback
throws or its result is null or illegal, an explicit pending replacement/clear is discarded and that
callback failure wins. A pending host cancellation still performs its non-callback state cleanup
because the view really became unavailable, but it does not recursively invoke the tool that just
failed. If a valid callback has a pending operation, the operation runs and the old cursor is never
queried. Pointer-only button/capture cleanup remains confined to pointer dispatch.

Throwing or null `cursorIntent()` during activation is an activation failure and follows the existing
best-effort-deactivate rule. During ordinary routing it is a dispatch failure with the same quarantine
and pending-click behavior as a failed pointer callback; the active tool remains installed. During
`resume()`, it leaves the installed tool active but the effective cursor at default, rearms external
cancellation, and propagates. External-cancel state is cleared and quarantined before its tool callback;
if that callback fails, the cancel remains coalesced, the active tool remains installed, the cursor is
default, and the original failure propagates. Replacement/clear still attempts deactivation after a
cancel failure, retaining the first failure and suppressing later cleanup failures.

The router observes every supported raw event even with no active tool. Cancellation, activation,
replacement, or callback failure moves every physical down button crossing the session boundary to
quarantine. A release for a quarantined button is swallowed, removes it from quarantine, and arms one
pending-click token; the corresponding click is swallowed once.

Every ordinary release also leaves a non-suppressing `ReleaseCandidate(button, sequence)` until the
next non-cancel AWT-derived event. A lifecycle boundary or dispatch failure before that event promotes
the candidate to the pending-click token—even when it happens after `mouseReleased` returned—covering
focus/disable/removal and another component-local listener replacing the tool in the release/click gap.
Session termination or failure during the release promotes it immediately. Without a boundary, the
normal matching click clears the candidate and is routed normally; any other raw event expires it. A
promoted token is consumed by its matching immediate click and otherwise expires at the next raw event,
so it cannot suppress an unrelated later gesture.

Before routing a later event, named/observed button masks reconcile quarantine with physical state. A
quarantined button absent from `buttonsDown` on a move, wheel, or unrelated fresh press is known to
have been released outside the component and is removed with any obsolete click token. A fresh press
of the same quarantined button likewise clears its old quarantine/token before becoming a new gesture.
A click naming a still-quarantined or token-bearing button is swallowed and clears both. Thus missing
an off-component release cannot quarantine a button permanently or consume the first click of a later
fresh gesture.

Because Swing drag events name no changed button, any `DRAG` whose reconciled set still intersects
quarantine is suppressed as a whole. Other presses/releases are delivered to the tool for observation,
but capture is legal only for a sole down button and built-in navigation never starts or continues
while multiple buttons are down. A live multi-button drag can reach the tool once quarantine is empty,
but never pans; adding a second button clears any pending navigation anchor. These conservative chorded-
button rules prevent capture and navigation from being armed simultaneously without pretending that a
Swing drag can be attributed to one of several held buttons.

Focus loss, disable, removal, or an uncaptured pointer exit delivers one cancel to an installed tool,
clears capture and router/navigation gesture state, quarantines reconciled physical buttons, resets the
cursor, and leaves the tool installed. Pointer exit does not cancel a captured drag. Repeated external
cancellation signals coalesce until `resume()` or the next accepted pointer event; this prevents focus
loss followed by removal from double-canceling one interaction. `POINTER_STATE_LOST` follows the same
path for an inconsistent captured or dragged sequence. Replacement and clear always deliver their
lifecycle cancel even after external cancellation because they also end the activation session.
`USER_CANCEL` performs the same gesture cleanup and quarantine but represents an available host: it
does not arm or coalesce unavailability, and a successful callback immediately refreshes the
installed tool's cursor.

#### AWT routing and navigation order

`MapView` adds explicit `setActiveTool(MapTool)`, `clearActiveTool()`, and `activeTool()` methods,
using identity and non-null arguments. Construction calls `setFocusable(true)` and installs only
component-local mouse, mouse-motion, wheel, focus, enabled-property, and add/remove-notify hooks—never
a global AWT listener. An enabled property transition to false cancels immediately; transition to true
may resume only after the component is displayable. `removeNotify` cancels before delegating to Swing,
and `addNotify` resumes after delegation when enabled. Mouse enter resumes after pointer-exit cancel;
focus gain resumes after focus-loss cancel. Resume applies a tool cursor only while enabled and
displayable; otherwise the default remains. Swing's normal EDT rule applies to these mutations. Every
callback and resulting viewport/repaint/cursor mutation is synchronous on the EDT.

On an enabled/displayable button press, MapView calls `requestFocusInWindow()` before it snapshots the
viewport, constructs the tool event, or invokes the tool. The press is routed even if the asynchronous
focus request returns false; any later focus transition follows the explicit cancel/resume rules. This
ordering gives later keyboard-enabled tools a deterministic opportunity to acquire focus without
making focus a prerequisite for mouse interaction.

For each accepted AWT event, MapView snapshots layers and viewport, converts the toolkit-neutral event,
routes it to the tool, then performs remaining behavior only when `suppressDefault` is false. A primary
press begins the existing navigation gesture only when primary is the sole down button. Each sole-
primary drag applies the delta from the prior raw drag sample, and release ends it. A consumed drag
still advances that sample so a later passed drag does not jump. Adding another button clears the
navigation anchor, and a multi-button drag never pans. A captured press never begins navigation, and
every button-bearing event during capture suppresses navigation. A passed wheel uses the event's
screen anchor and precise rotation in the existing zoom formula. Tool event coordinates therefore
always describe the viewport before that event's pan or zoom. A missing optional map coordinate does
not suppress routing or defaults; screen-space navigation remains available over blank space outside
the display CRS domain.

Passed `MOVE` and `CLICK` events are then adapted to the existing observer event and delivered to a
listener snapshot in registration order. G4-002 likewise changes `MapPointerEvent.mapCoordinate` to
`Optional<Coordinate>`, so compatibility observers receive the event with the same presence/absence
rather than a clamped coordinate. Consumed or quarantined events do not reach compatibility listeners.
MapView neither synthesizes clicks nor changes the platform click count after a captured
release; an active tool can consume the later real click if it needs exclusivity. With no active tool,
no quarantine or promoted click token, and no canceled navigation gesture awaiting cleanup, the router
is clean-idle and passes, preserving G1 pan, wheel, moved, and clicked behavior. Isolation state still
suppresses its stale drag/release/click even after the prior tool has been cleared.

After a successful tool callback, MapView applies the returned cursor intent. Cancellation,
deactivation, unavailable component state, or callback failure applies `Cursor.getDefaultCursor()`.
A pointer/cursor exception, null result/intent, or invalid result clears capture and navigation state,
quarantines every currently down button, arms the changed button's immediate-click token when failure
occurred on release, suppresses defaults, resets the cursor, and propagates without recursively calling
the failed tool. Explicit pending replacement/clear is discarded; a pending host cancellation still
marks the component unavailable without another callback. The active tool remains installed for an
explicit later cancellation or replacement. A captured release clears capture in `finally`, even when
the callback fails. Every raw release also clears the matching navigation gesture after routing, even
when consumed, so a tool cannot leave panning armed accidentally.

#### Verification boundary

API tests cover every event-type invariant, finite present values, empty/present map coordinates,
defensive button/modifier sets, arbitrary auxiliary button identity, cancellation samples, and the
three result/four cursor values. Core tests
use recording tools to prove identity replacement order, partial-activation cleanup, exception
suppression, capture legality, captured release, independent wheel consumption, queued replacement,
external-cancel coalescing/resume, and stale-response rejection. They cover second-button capture
rejection, nonmatching release during capture, multi-button no-pan outcomes, off-component release mask
reconciliation, fresh-press recovery, ambiguous quarantined drags, and following-click suppression for
captured and uncaptured release-triggered replacement, clear, and failure. They also cover first-tool
activation during a no-tool primary pan and lifecycle promotion of a release candidate after its
handler returns but before click delivery. Failure matrices exercise pointer result, cursor lookup
during activation/ordinary event/resume but never cancel, external cancel, and a queued operation
followed by callback failure, including primary/suppressed exception order.

Swing tests dispatch real press/drag/release/move/click/wheel events on the EDT and assert exact
tool/default/compatibility-listener order and pre-navigation coordinates. They cover cumulative pan,
consumed-drag anchor advancement, cursor-centered precise wheel zoom, each cursor mapping, focus loss,
focus-request-before-press-callback, enabled-property disable/re-enable, add/remove notify, pointer exit
with and without capture, first activation during an ordinary pan, replacement during and immediately
after a release callback, release outside/re-entry, unobserved held secondary/auxiliary buttons,
mixed-button drags, stale release/click isolation, and events whose map coordinate is empty at each
strict display-domain edge. A no-tool fixture repeats G1 navigation and observer behavior. Hit testing,
selection, hover state, measurement, editing, touch/multitouch, custom
cursor images, global event interception, and background callback execution remain out of scope.

### Symbol-aware hit testing and single selection (G3-002)

#### Query and identity values

Hit testing is a screen-space query over the same immutable layer/feature snapshots and paint order as
one `MapView` pass. The public API adds only stable identity values:

```text
MapHit(String layerId, String featureId)

MapHitResults implements Iterable<MapHit>
  of(List<MapHit>)
  size()
  hits()
  topmost() -> Optional<MapHit>

FeatureSelection(String layerId, String featureId)
```

IDs are non-blank and otherwise preserved exactly, matching the existing `Feature` and
`InMemoryLayer` domain; no hit or selection constructor strips, normalizes, or changes case. Results
defensively copy their ordered hits, preserve value equality and iteration order, and never retain a
`Layer`, `Feature`, geometry, symbol, AWT shape, or view. `MapHitResults.of` declares its input to be
topmost-first, rejects a null entry or duplicate `(layerId, featureId)` pair with a field-naming
argument error, and otherwise preserves caller order; empty input is valid. `topmost` is the first list
element. `FeatureSelection` is the same stable pair as a distinct semantic value rather than an alias
for a past query result.

`MapView.hitTest(screenX, screenY, tolerancePixels)` requires finite screen coordinates and a finite
non-negative tolerance. A query center outside the current logical component rectangle returns empty;
inside, effective tolerance is capped at the component diagonal because a larger disk cannot reach
additional visible paint. This accepts every finite non-negative caller value without allowing it to
overflow a stroke or allocate a tolerance-sized buffer. The query footprint is a logical-pixel disk
clipped to the exact half-open component domain `[0,width) x [0,height)`. A footprint contributes only
where it intersects that domain after fill, stroke, hatch, transform, and interpolation-support
expansion; clipping a centerline or nominal marker bounds is not sufficient.

For the legacy-layer G3 baseline, every paint, public hit query, click-selection operation,
`setSelection` call, and selection read
captures one private immutable `ViewContentSnapshot` on the EDT. Capture copies the installed layer
list, calls each layer's `id()` and `features()` exactly once, copies each ordered feature list, and
validates all captured elements and IDs. Layer IDs must be unique in a view and feature IDs unique
within their layer. The operation then reuses that one snapshot for selection reconciliation,
traversal, and any selection update; it never re-reads a mutable third-party `Layer` mid-operation.
Viewport, projection, and renderer-registry snapshots are likewise fixed once for a hit or paint pass.

`setLayers` first copies the candidate list and captures and validates its complete content without
changing view state. It reconciles the current selection against that candidate snapshot, then assigns
the list and reconciled selection together and requests one repaint. A capture or validation failure
leaves both prior fields unchanged. Later operations repeat capture because a valid `Layer` may expose
a newer immutable feature snapshot after installation. A duplicate, blank, or null captured identity
or element is a field-naming programmer-state failure, not a source diagnostic.

Layers and features are visited last-to-first, exactly reversing paint order. Once any painted part of
a feature hits, one `MapHit` is appended and traversal advances to the preceding feature. Composite
symbol children and owned sub-symbols are themselves visited in reverse paint order and stop at their
first matching leaf. Thus child order affects which leaf proves the feature hit without producing
duplicate feature results; the first public result is always the topmost visible feature.

#### Toolkit-neutral screen geometry predicates

`ScreenGeometryHits` in `mundane-map-core` is a stateless final utility with these public predicates:

```text
pointWithin(px, py, qx, qy, radius) -> boolean
polylineWithin(CoordinateSequence screen, boolean closed,
               qx, qy, radius) -> boolean
filledPolygonWithin(CoordinateSequence exterior,
                    List<CoordinateSequence> holes,
                    qx, qy, tolerance) -> boolean
convexQuadWithin(double[] screenXy8, qx, qy, tolerance) -> boolean
```

All inputs are finite and radii/tolerances non-negative; the quad array is defensively unnecessary
because it is consumed synchronously but must contain exactly eight ordinates. The implementation uses
primitive loops and no per-segment objects. Point/segment comparison scales operands before dot/cross
products and compares normalized distances, avoiding squared-distance overflow. A zero-length segment
becomes a point test. `polylineWithin` is the union of round segment capsules, which exactly matches
Level 1's fixed round caps/joins; `closed` adds the final-to-first segment and has no endpoints.

`filledPolygonWithin` first treats any ring boundary within tolerance as hit, then applies an even-odd
ray crossing across the exterior and all holes. Consequently exterior and hole boundaries are
inclusive, a point deeper than tolerance inside a hole is excluded, and a point just outside the
exterior or just inside a hole may hit the nearby visible fill when tolerance is positive. Ring
orientation is irrelevant. Repeated coordinates and zero-length edges are legal; malformed rings and
repair remain outside this task. `convexQuadWithin` is point-in-quad plus minimum edge distance and is
used for transformed raster sample support. Extreme finite-coordinate tests prove that no NaN,
infinite intermediate, or division by a zero-length segment changes a result.

AWT projects each geometry coordinate to screen once per feature query and builds packed
`CoordinateSequence` values for these predicates. Centerline hit radius is
`screenStrokeWidth / 2 + tolerance`; checked addition that exceeds the maximum useful component
distance saturates to that distance. The generic segment predicate treats a zero-length segment as a
point, but line-symbol orchestration follows G2-004: it skips repeated coordinates while finding real
segments and treats an all-coincident part as unpainted and therefore non-hittable. Polygon fill and
each visible exterior/hole outline are evaluated independently, so holes stay empty while a configured
ring stroke remains selectable.

These core predicates operate on the unbounded logical screen plane. Their result is final when the
whole query disk is inside the component. When that disk touches a component edge, AWT must apply the
post-paint-footprint clip described below before accepting a candidate; an unbounded predicate is only
a rejection/broad-phase result in that case.

#### Renderer-owned symbol footprints

The hit contract is the ideal logical painted footprint before antialiasing, device-pixel sampling,
color-model conversion, or destination compositing. It uses the model's double-precision transforms,
mathematical round stroke caps/joins, even-odd fills, generated hatch segments, and interpolation
supports. Modeled alpha is tested before conversion to Java2D `float` or an integer destination: an
effective product equal to zero has no footprint and every positive product does. This deterministic
contract deliberately does not promise that a hit corresponds to a non-background device pixel on
every platform, and it requires no raster readback.

G3-002 adds one source-compatible default to the existing AWT extension interface:

```text
AwtSymbolRenderer
  ...existing supports/render methods...
  default hitTest(Symbol value, AwtSymbolHitContext context) -> false
```

Co-locating the optional hit method with the explicitly registered renderer keeps one role/key/value
compatibility check and avoids a second global registry. Every built-in overrides it. A custom renderer
that does not opt in is deterministically non-hittable; MapView never guesses from nominal bounds or
discovers a tester. Lookup, `supports`, and dishonest-value failures retain the G2 stable codes before
hit testing.

`AwtSymbolHitContext` is callback-scoped and parallels the read-only render context: role, feature ID,
original feature geometry, current render geometry, projection/viewport snapshots, inherited opacity,
closed-ring state, optional endpoint bearing, optional marker anchor, validated `MapScreenBasis`, query
point/tolerance, source-to-screen conversion, a defensive `Rectangle2D componentClip()` representing
the exact half-open logical component domain, and
`hitChild(Symbol child, double opacityMultiplier)`. Public child traversal is same-role and retains the
derived context. Package-private endpoint-marker and closed-ring-outline transitions mirror G2-005's
render transitions exactly. The context exposes no `Graphics`, `MapView`, selection mutation, registry
mutation, or parent context and must not be retained.

`visibleShapeHit(Shape logicalPaintFootprint)` is the canonical AWT edge helper. It intersects the
already filled or stroke-expanded footprint with `componentClip()` first and only then tests the
query disk, including logical footprint and clip boundaries under the declared half-open maximum-edge
rule. It uses Java2D path/area geometry, never device raster readback. Built-in vector, legacy, hatch,
and line renderers use their unbounded analytic fast path only when the query disk is wholly inside the
component; at an edge they pass the same final fill or `BasicStroke.createStrokedShape` used to define
paint to this helper. Raster candidates similarly pass each final transformed support quad. A fully
outside footprint therefore cannot hit through a discarded part of the disk, while any partially
visible fill, stroke, or pixel support remains eligible. A custom renderer's `hitTest` contract has the
same clipping requirement and may use this public helper rather than reproduce it.

The same modeled effective-opacity product used for logical paint gates hits. A
symbol/composite/owner opacity or paint/pixel alpha of zero contributes no footprint; any positive
product does. Traversal rules are:

- marker composites test last/top child first;
- vector markers test visible stroke before visible fill, using the same transformed `Path2D`; stroke
  uses its actual screen width plus twice the tolerance, while filled closed contours use containment
  plus a round boundary expansion for positive tolerance;
- line composites test last child first; within each open part, end marker, start marker, then
  centerline reverse the G2 paint order, while closed-ring contexts suppress endpoints recursively;
- solid fills test their outline before their visible even-odd interior; hatch fills test outline then
  the actually generated/clipped hatch segments and have no implicit background; and
- fill composites test last complete fill/outline child first.

Legacy point, line, and polygon styles retain equivalent circle, round-centerline, even-odd fill, and
ring-stroke tests through the Level 1 compatibility window. The G1 convenience point label is an
annotation, not part of a symbol footprint; text does not make an otherwise transparent feature
hittable before the later label system defines text hit policy.

#### Raster alpha footprint

Raster icons do not fall back to their nominal rectangle. `MarkerTransform` gains a checked
screen-to-local inverse used only after its already-validated non-singular forward transform. For
`NEAREST`, each source pixel with alpha greater than zero contributes the half-open pixel cell
`[x,x+1) x [y,y+1)` within the half-open image domain. For `BILINEAR`, that sample contributes its open
triangular-kernel support `(x-0.5,x+1.5) x (y-0.5,y+1.5)`, intersected with the half-open image domain;
the union of positive-sample supports is exactly where modeled interpolated alpha can be positive.

Zero tolerance tests the inverse-mapped query point directly in those half-open/open source supports,
so a zero-weight bilinear boundary is not a hit and each nearest cell includes its `x,y` edges while
excluding its `x+1,y+1` edges. For positive tolerance, each support's closure becomes a screen convex
quad and `convexQuadWithin` tests distance to that closure; equality with the positive radius is
included. This explicit boundary convention avoids depending on Java2D's device-pixel tie breaking
while preserving the logical interpolation footprint away from measure-zero edges.

The inverse transform maps the query's screen bounding square to a conservative local range, expanded
by one source pixel for bilinear support and clamped with checked `long` arithmetic. The tester scans
only candidate rows/pixels, reads packed alpha directly, and exits on the first hit. Its deterministic
worst case is the icon's existing `MAX_PIXELS`, with no new allocation proportional to tolerance or
image size. G7 may optimize from evidence; G3 does not introduce an alpha pyramid, cache, or platform
raster readback.

#### Selection state and click behavior

`MapView` owns `Optional<FeatureSelection> selection()` and explicit
`setSelection(FeatureSelection)`/`clearSelection()` methods. For a legacy layer, programmatic set
requires the exact IDs to exist uniquely in its one captured content snapshot; absence is a field-naming
`IllegalArgumentException`. It applies the selection against that same snapshot rather than reading a
layer again. Validation and existence checking occur before comparing with stored state, so setting the
same value is a no-op only when it remains valid in that snapshot. `clearSelection()` never consults
layer content: it clears the stored immutable pair directly and therefore still succeeds when an
unrelated mutable layer is invalid or throws. Clearing empty state is a no-op. Any real change requests
one repaint, although G3-003 supplies the first visual overlay and change listeners.

Legacy selection is reconciled from the operation's content snapshot before paint or hit traversal and before
`selection()` returns, and transactionally during `setLayers`. Removing the selected layer or feature
clears it; replacing objects under the same IDs or reordering them preserves it. This is ID continuity
only—no geometry/attribute/source-version matching is inferred. Duplicate IDs fail as above rather
than choosing an arbitrary selection. Reconciliation during an already scheduled paint does not queue
a redundant repaint; external reads and hit operations that discover and clear stale state request one.

G4-003 extends the private snapshot with bounded lazy-source entries. It does not treat absence from a
viewport query as source-wide removal, does not represent a source as `Layer`, and defines the
source-specific programmatic-selection and binding-replacement rules in the
[G4 source slice](G4-sources-and-crs.md#interaction-fitting-and-report-refinements).

After the G3-001 router passes an unmodified, non-popup, single primary `CLICK` with no buttons down,
including a passed clean-idle click when no tool is installed, MapView captures content once and
performs the hit traversal with public constant
`DEFAULT_SELECTION_TOLERANCE_PIXELS = 4.0`. The topmost hit becomes selection and an empty result clears
it against that same snapshot. Selection updates before a passed compatibility click listener is
notified, so observers can read the new state. A consumed/quarantined click, modified click, auxiliary
click, or multi-click does not change selection. Pan and wheel behavior remain unchanged.

#### Verification boundary

API tests cover non-blank exact ID preservation including surrounding whitespace, null/duplicate hit
rejection, declared topmost order, immutable results, one-hit-per-feature behavior, topmost access,
selection equality, and all tolerance/query input boundaries. Core tests cover points, open/closed
segments, round endpoints, repeated/zero-length segments, even-odd rings, holes, exact boundaries,
positive tolerance on either side, convex quads, and extreme finite operands.

AWT tests cover all eight built-in vector markers, fill/stroke alpha combinations, every placement
anchor/unit/rotation mode, raster row/alpha holes under nearest and bilinear interpolation, marker/line/
fill composites, endpoint order, cased lines, solid fills, all hatches, outlines, polygon holes, legacy
styles, custom renderer opt-in/default-false behavior, reverse layer/feature/child order, component-edge
clipping with fully outside-near-edge and partially visible fill/stroke/icon footprints, positive
modeled alpha, and fully transparent features. Raster cases distinguish exact nearest half-open edges
and zero-weight bilinear boundaries at zero tolerance from closure distance at positive tolerance,
including an opaque sample beside transparent neighbors. Interaction tests cover no-tool and
active-tool passed clicks versus consumed primary clicks, empty clear,
modified/multi-click no-op, listener observation order, programmatic set/no-op/clear, same-ID
replacement, removal invalidation, duplicate-ID failures, same-value validation, and successful direct
clear while another layer accessor fails. A deliberately changing `Layer` proves each paint, hit,
click, set/read, and `setLayers` transaction uses one feature-list snapshot and that a failed candidate
install leaves prior view and selection state intact. Direct iteration is deliberate; spatial
indexing, multi-selection, visual overlays, hover, label hits, editing, and source-version persistence
remain out of scope.

### Hover and selection rendering (G3-003)

#### Immutable change and overlay values

Hover is the current topmost hit, so G3-003 reuses `MapHit` instead of adding a third layer/feature-key
type. The API module adds these immutable values and functional listeners:

```text
MapHoverEvent(Optional<MapHit> previous, Optional<MapHit> current)
MapSelectionEvent(Optional<FeatureSelection> previous,
                  Optional<FeatureSelection> current)

MapHoverListener.onMapHoverChanged(MapHoverEvent event)
MapSelectionListener.onMapSelectionChanged(MapSelectionEvent event)

FeatureOverlaySymbols(MarkerSymbol marker, LineSymbol line, FillSymbol fill)
```

Event optionals are non-null and unequal; at least one side is therefore present. They retain no
feature, layer, view, renderer, or AWT object. Identity comparison uses the complete exact
`(layerId, featureId)` pair, so equal feature IDs in different layers still cause a hover transition.
`FeatureOverlaySymbols` requires the three exact roles, retains only immutable symbol values, has
value equality, and supplies source-listed `defaultHover()` and `defaultSelection()` factories. It is
one role bundle rather than a general interaction-theme hierarchy.

`MapView` exposes `hover()`, the existing `selection()`, ordered add/remove methods for both listener
types, and get/set methods for the hover and selection overlay bundles. Hover has no programmatic
setter because it represents a pointer-derived hit. Overlay configuration does not alter source
features and is independent of the selected and hovered keys.

The two defaults remain recognizable when color cannot be distinguished. Hover is a centered
18-logical-pixel diamond ring and 7-pixel line/polygon outline in translucent amber
`rgba(255,170,0,176)`. Selection is a centered 14-pixel circle ring and 3-pixel line/polygon outline in
opaque blue `rgba(0,102,204,255)`. Marker strokes are 4 and 2 pixels respectively; polygon interiors
are transparent, line endpoints are absent, all measurements use `SCREEN_PIXEL`, and rotations are
screen-relative. Painting the wider hover treatment first lets it remain visible around the narrower
selection treatment when both keys match.

#### EDT-confined state and notifications

`MapView` owns `Optional<MapHit> hover` and the existing `Optional<FeatureSelection> selection`; it
never retains the feature object that produced either. No public core state machine is added. Equality
transition logic is trivial, while validation against `ViewContentSnapshot`, repaint invalidation,
listener ordering, and lifecycle are all host/EDT concerns; moving those into core would split one
atomic Swing transaction across modules without enabling another toolkit.

A private AWT transition operation accepts proposed optionals, compares them with stored state, and
commits every real change before repaint or callback. It requests one full-component repaint for the
whole transaction, then enqueues selection notification before hover notification, and finally drains
a single FIFO. Pointer compatibility listeners run only after a successful drain; an aggregated
interaction-listener failure propagates first and suppresses that event's later compatibility
callback. Programmatic setters and clearers complete the same synchronous sequence before returning.

The FIFO prevents a listener-triggered state change from recursively reordering events. Each queued
event snapshots its relevant listener list at delivery time and visits registrations in order.
Duplicate listener instances receive duplicate callbacks; removal removes the first identical (`==`)
registration, and an equal but distinct or absent instance is a no-op. Mutation during a callback
affects only later queued events. Reentrant state changes take effect immediately and enqueue after the
current event, so the immutable event—not a later state read—is the authoritative old/new pair.

Runtime exceptions do not roll back committed state or its repaint. Delivery continues to the
remaining registrations and queued events; the first failure is rethrown after the FIFO drains and
later distinct failure instances are attached as suppressed in encounter order without attempting
self-suppression. An `Error` aborts delivery, clears the remaining notification queue, and propagates
after the guard resets. The queue guard always resets in `finally`. There is no synchronization or
thread marshalling: mutation and callback use the normal `MapView` EDT contract.

#### Hover lifecycle and operation order

After the G3-001 router passes a button-free `MOVE`, MapView reuses that event's one content, viewport,
projection, and registry snapshot and performs the G3-002 traversal with public
`DEFAULT_HOVER_TOLERANCE_PIXELS = 4.0`. Its topmost hit or empty result becomes the proposed hover
before the compatibility move listener runs. While hover is non-empty, MapView retains only the latest
finite screen coordinates of such a move as a private `HoverProbe`; it does not retain the hit feature,
geometry, or symbol. Movement over the same full key updates that probe but is otherwise silent—no
event and no repaint—even if a different symbol child proved the hit.

Hover is cleared once when the pointer stream can no longer justify it: any button press/drag gesture,
a consumed or quarantined move or button-bearing event, pointer exit (including during capture), a
successful pan, zoom, fit, viewport replacement, component resize, transition to disabled,
`removeNotify`, or `setLayers`. A failed `setLayers` transaction leaves hover unchanged; a successful
replacement clears it even when the same IDs occur in the candidate snapshot. Re-enable, re-add,
pointer enter, or tool resume does not synthesize hover—only the next passed button-free move may
restore it. Focus loss alone does not clear hover while the pointer remains in the component.

Every later content reconciliation that still has a hover probe reruns the G3-002 topmost query at
those coordinates through the internal snapshot-bound traversal, not the public reconciling method.
The resulting full key replaces, preserves, or clears hover before overlay painting. This catches
mutable same-ID geometry/symbol replacement and feature reordering without storing source objects.
Clearing hover also clears the probe; `setLayers` and viewport/lifecycle invalidation therefore never
synthesize a new hover without another move.

Selection retains G3-002 ID continuity: same-ID object replacement or reorder preserves it, while
missing content clears it. Programmatic set, direct clear, click selection, and removal reconciliation
all route through the transition/notification path. A content operation commits selection and hover
changes together, requests repaint once, then delivers selection followed by hover. Router or
lifecycle failures clear hover in host `finally` handling even if their original failure is later
propagated. That original tool/router/lifecycle failure remains primary; a hover-notification failure
is attached as suppressed.

Paint, hit, state reads, clicks, and `setLayers` continue to use the one per-operation
`ViewContentSnapshot` defined in G3-002. When reconciliation first discovers mutable-layer removal
during painting, it commits state before renderer traversal but defers public listener delivery until
the child graphics has been disposed. The paint pass uses the captured content and interaction state;
callback mutations schedule a later pass rather than altering traversal in progress.

#### Renderer-reported logical paint presence

Overlay eligibility needs to distinguish a transparent source from one that actually paints, but it
does not need a second bounds traversal. G3-003 extends the existing `SymbolRenderResult` with
toolkit-local enum `AwtLogicalPaintPresence = EMPTY | PRESENT | UNKNOWN`. Existing no-presence
factories remain source-compatible and produce `UNKNOWN`; overloads require an explicit presence.
Composite `union` returns `PRESENT` if any child is present, otherwise `UNKNOWN` if any child is
unknown, otherwise `EMPTY`.

Every built-in reports exact logical presence as part of the render work it already performs. Positive
modeled fill/stroke/hatch/endpoint paint is `PRESENT`; zero effective opacity, an all-coincident line,
no generated hatch segment or outline, and a raster icon with no positive-alpha sample are `EMPTY`.
Composites combine child presence. Legacy geometry follows the same symbol rules and excludes the G1
convenience label. This is pre-device logical paint presence under G3-002's alpha contract, not a claim
that a destination pixel changed.

A custom renderer's source-compatible `UNKNOWN` keeps rendering behavior intact but does not prove
overlay eligibility. It may opt in by returning `PRESENT` or `EMPTY`; a dishonest result is an
extension-contract violation just like an incorrect hit result. Programmatic selection and hover state
remain valid identity state regardless of presence. `EMPTY`, `UNKNOWN`, removed content, or content
skipped by the source pass suppresses only its overlay; `PRESENT` permits it. No registry, render, or
presence failure rolls state back.

#### Full invalidation and overlay pass

Every real hover, selection, or overlay-bundle change calls ordinary full-component `repaint()` before
listener delivery. Layer, viewport, resize, enablement, and lifecycle operations already do the same.
This one conservative region necessarily includes arbitrarily wide strokes, endpoint markers, map-unit
sizes, offsets, rotations, hatches, antialiasing fringes, and both the old and new location after
removal. It introduces no public bounds protocol, no duplicate renderer traversal, and no retained
geometry/render cache before G7 profiling demonstrates that narrow dirty regions matter.

A mutable `Layer` has no change-listener contract in Level 1; its owner must request a full MapView
repaint after publishing a new immutable feature snapshot. If reconciliation discovers removal or a
changed hover during a narrower externally initiated paint, the committed state transition schedules
the required full follow-up repaint. MapView retains only the two current source-presence states so a
same-key transition between eligible and ineligible overlay paint also schedules one full follow-up;
it retains no prior feature, geometry, symbol, or screen bounds.

Paint traverses all source content first and retains render presence only for the captured hover and
selection keys. It then renders a `PRESENT` hover feature's role-matched overlay symbol through the same
`SymbolRendererRegistry`, followed by eligible selection. Overlay dispatch passes the original
immutable geometry and feature ID, disables the G1 convenience label, and never constructs a synthetic
feature, edits the source symbol, changes hit results, or recursively adds another overlay. Overlay
renderer lookup or rendering failure propagates after source painting and leaves committed interaction
state intact. Paint `finally` disposes the graphics child and drains any deferred state events; an
earlier render failure remains primary and a notification failure is attached as suppressed.

The same placement, map-unit conversion, rotations, composites, endpoint rules, hatches, holes,
opacity, diagnostics, and explicit renderer registration therefore apply unchanged. Overlays never
participate in hit testing. The default bundles work with `SymbolRendererRegistry.builtIn()`; a custom
registry must explicitly register every configured overlay renderer, with the existing stable missing
or value-mismatch diagnostic rather than a hidden built-in fallback.

#### Verification boundary

API tests cover unequal old/new optionals, exact full-key identity, immutable event values, listener
functional contracts, overlay role validation/equality, and the exact defaults. AWT state tests cover
enter, unchanged move, cross-layer same-feature-ID transition, empty move, consumed/quarantined move,
button gesture, pan/zoom/resize, captured exit, failed/successful layer replacement, disable/re-enable,
remove/re-add, mutable-layer disappearance, same-ID geometry/symbol replacement, topmost reorder, and
repeated-clear silence.

Selection tests cover programmatic and click changes, same-value silence, direct clear, same-ID
replacement, removal discovered by reads and paint, selection-before-hover batch order, and
selection-before-compatibility-click observation. Listener tests cover identity duplicates, mutation,
FIFO reentrancy, failure aggregation, callback-triggered state changes, compatibility-callback
suppression after an interaction-listener failure, and EDT execution.

Render-result tests cover `EMPTY`/`PRESENT`/`UNKNOWN` algebra and exact built-in presence for
positive/zero opacity, all-coincident lines, endpoints, hatches, fully transparent raster icons,
composites, and legacy label exclusion. A custom renderer's source-compatible result stays `UNKNOWN`
until it opts in. Repaint-manager tests prove every real transition invalidates the full component for
large strokes, offsets, rotated/map-unit symbols, and removed old content. Presentation lookup/render
failure tests prove committed identity state and events are not rolled back.

Offscreen rendering tests compare source-only and overlay passes, prove unchanged pixels outside the
actual overlay footprint, preserve source feature/symbol equality, omit labels from overlays, keep
polygon holes unfilled by the defaults, and establish source-then-hover-then-selection order when both
keys match.
Architecture tests keep event/symbol values AWT-free and reject any second registry or discovery path.
Tooltips, accessibility, multi-selection, editing handles, animation, thematic styles, generalized
visibility, render caching, narrow dirty bounds, and performance claims remain out of scope.

### Distance strategies and measurement tool (G3-004)

#### CRS-bound distance values and strategies

Level 1 exposes distance in one canonical unit instead of adding a second unit hierarchy beside
G4's `CrsUnit`. The toolkit-neutral API surface is:

```text
DistanceResult(double metres)
  plus(DistanceResult other) -> DistanceResult

DistanceStrategy
  coordinateCrs() -> CrsDefinition
  distance(Coordinate start, Coordinate end) -> DistanceResult

MeasurementPhase = EMPTY | MEASURING | COMPLETE

MeasurementState
  phase() -> MeasurementPhase
  vertexCount() -> int
  vertex(int index) -> Coordinate
  packedVertices() -> defensive double[]
  preview() -> Optional<Coordinate>
  committedDistance() -> DistanceResult
  lastCommittedSegmentDistance() -> Optional<DistanceResult>
  previewSegmentDistance() -> Optional<DistanceResult>
  displayedDistance() -> DistanceResult
```

`DistanceResult` requires a finite non-negative metre value and canonicalizes either signed zero to
positive zero. `plus` adds in encounter order and fails before returning if the sum is non-finite;
there is no saturation, unit conversion, negative sentinel, or implicit formatting policy in this
public numeric value. Nulls and structurally invalid direct arguments use ordinary Java argument
failures.

`MeasurementState` is an immutable snapshot. It owns one packed primitive coordinate array, clones
input and output, uses value equality, and never retains a tool, view, strategy, layer, or AWT value.
`EMPTY` has no vertices, preview, or segment distances and a zero total. `MEASURING` has at least one
vertex; a preview and its distance are either both present or both absent. `COMPLETE` has at least two
vertices and no preview. A last committed segment is present exactly when at least two vertices are
present. The committed total is the ordered sum of committed segments and excludes the preview.
`displayedDistance` is the checked committed total plus the preview segment when present, otherwise
the committed total.

Core supplies exactly two explicit factories for Level 1:

```text
DistanceStrategies.planarMetres(CrsDefinition projectedCrs) -> DistanceStrategy
DistanceStrategies.epsg4326GreatCircle(CrsDefinition geographicCrs) -> DistanceStrategy
```

The planar factory requires a recognized projected definition whose x/y axes are
`EASTING/METRE` and `NORTHING/METRE`; the geographic factory requires exact equality with the
canonical registered EPSG:4326 definition, including axes and domain. A fabricated same-name
definition is not accepted. Each returned strategy retains that immutable exact definition, and a
measurement tool verifies it equals `MapToolContext.mapCrs()` during activation. A mismatch uses the
existing `CrsException`/`CrsProblem` with `CRS_DEFINITION_MISMATCH` and bounded `expectedCrs` and
`actualCrs` context. Missing and unknown source metadata are not measurement states: a successfully
constructed `MapView` already has a recognized map CRS, and source binding owns those G4 diagnostics.
No `LinearUnit`, metadata resolver, measurement diagnostic type, or numeric-range CRS guess is added.

For EPSG:3857, planar distance is the Euclidean distance between projected metre coordinates. It is
explicitly projected-coordinate distance, not an ellipsoidal ground-distance correction; Web
Mercator scale distortion is documented in the public factory Javadoc and example. Future projected
CRSs with other units wait for G10-007 rather than acquiring a conversion table here.

#### Deterministic numeric policy

The planar strategy validates both coordinates against its exact CRS domain and evaluates
`StrictMath.hypot(end.x - start.x, end.y - start.y)`. The geographic strategy validates longitude and
latitude against the full EPSG:4326 domain, with x as longitude east and y as latitude north under
G4's visualization convention. A coordinate-domain failure retains the existing
`CRS_COORDINATE_OUT_OF_DOMAIN` shape; a non-finite calculated result uses
`CRS_TRANSFORM_NON_FINITE`. Strategy construction and use never translate those failures into a
source diagnostic unless a later source boundary is the caller.

The geographic result is a spherical great-circle distance with radius exactly
`6_371_008.8` metres. That is the average Earth radius `6 371.008 771 4 km` published in
[ITU-R P.1511-3](https://www.itu.int/dms_pubrec/itu-r/rec/p/R-REC-P.1511-3-202408-I%21%21PDF-E.pdf),
rounded once to the nearest decimetre as the library constant. It is not presented as an ellipsoidal
geodesic.

Core uses `StrictMath` throughout. It converts latitude and longitude differences to radians,
normalizes longitude difference with `atan2(sin(delta), cos(delta))`, evaluates the haversine term,
clamps only accumulated floating-point drift to `[0, 1]`, and obtains the central angle as
`2 * atan2(sqrt(a), sqrt(1 - a))`. This keeps identical, antimeridian, near-antipodal, and exact
antipodal inputs finite without wrapping the stored coordinates or introducing a branch to a second
algorithm. Signed zero is normalized only in the result.

The measurement tool computes a candidate segment and checked candidate cumulative/displayed total
before mutating its arrays or publishing a preview. It retains cumulative totals alongside its packed
vertices so Backspace restores the exact prior total rather than subtracting and accumulating drift.
Zero-length segments are valid.
Numeric tests use an absolute/relative tolerance of
`max(1e-6 metre, abs(expectedMetres) * 1e-12)` for calculated reference distances; zero and declared
boundary cases are asserted separately.

#### One bounded command extension to the tool router

Backspace is a semantic tool command, not a raw toolkit key in the pointer event. The API adds:

```text
MapToolCommand = DELETE_BACKWARD
MapToolCommandEvent(long sequence, MapToolCommand command)

MapTool
  default onMapToolCommand(MapToolCommandEvent event, MapToolContext context) -> PASS

MapToolRouter
  routeCommand(MapToolCommandEvent event, MapToolContext context) -> RouteOutcome
```

Command sequence numbers share the owning `MapView`'s strictly increasing event sequence. Command
routing uses the same installed session, callback guard, pending replacement/clear handling, failure
and cursor rules as pointer routing, including cross-kind recursive-dispatch rejection and applying a
queued host lifecycle operation before cursor/default handling. It does not change button, capture,
release-candidate, or quarantine state. `PASS` permits the host's prior key behavior and `CONSUME`
suppresses it; `CAPTURE` is illegal. There is no arbitrary key code, typed character, modifier set,
global key listener, key registry, or general shortcut manager.

`MapToolCancelReason` adds `USER_CANCEL`. Escape is deliberately not a second command: it constructs
the normal `CANCEL(USER_CANCEL)` event from the latest bounded event sample and enters the router's
existing interaction-cancellation path. Unlike focus/disable/removal unavailability, user cancel
ends and quarantines any current gesture but leaves the host available, the tool installed, and the
router immediately ready; after a successful callback it refreshes that same tool's cursor. User
cancel is not coalesced with a prior Escape. Its validated `PASS`/`CONSUME` result becomes the route
outcome's `suppressDefault`, augmented when a capture or MapView navigation gesture was actually
ended; cancellation cleanup has already occurred either way. With no installed tool, it passes unless
it ended a navigation gesture. Failure leaves the tool installed, applies the existing
default-cursor/quarantine behavior, and propagates.

`MapView.processKeyBinding` considers only an unmodified pressed Backspace or Escape while the
component is enabled, displayable, and focused. Backspace is routed only with an installed tool;
Escape is routed with an installed tool or live MapView navigation gesture. A suppressed outcome
returns handled; a passed outcome, including no tool, empty measurement, or no live gesture,
delegates to `super.processKeyBinding` and preserves any application/LAF binding. Releases, modified
keystrokes, and all other keys delegate unchanged. This stays component-local and needs no new
`MapToolContext` mutation method.

These additions correct one omission in G3-001: its prose allowed a callback-queued clear/replacement
without exposing such a context capability. G3-004 does not add one merely for measurement; public
MapView replacement/clear remains host-owned, and command callbacks follow the router's already
specified pending host-lifecycle rules.

#### Tool-owned measurement state and event order

`MeasurementTool` is one final public AWT tool because the only demonstrated host and painter are
Swing. It owns the supplied `DistanceStrategy`, packed committed vertices, cumulative totals,
optional preview, phase, and its cached immutable `MeasurementState`. `MapView` does not mirror those
fields. `state()` returns the cached immutable snapshot for painting, tests, and application status
display. The tool has a configurable vertex limit with default `10_000`, requires a limit of at least
two, and allocates/grows only up to that value. Adding the vertex that reaches the limit completes the
path automatically.

One mutable tool instance may be installed in only one `MapView` at a time. As part of its existing
concrete measurement integration, MapView performs a package-private identity claim before router
activation and releases it in `finally` after deactivation or failed activation. A claim held by a
different view fails before either router callback or view state changes; setting the same instance
again in its owner remains the router's normal identity no-op. This local owner field is not public,
is not a global manager or registry, and is never used as measurement state. After release, the
cleared tool may be installed in another view.

Activation first verifies the strategy's exact coordinate CRS against the callback context, then
retains no context. `cursorIntent()` is always `CROSSHAIR`. Deactivation clears all state
idempotently. Replacement and explicit tool clear therefore remove the measurement and its overlay.
Focus loss, pointer exit, disable/removal, and pointer-state loss receive their existing cancellation
reason, clear only a transient preview, and preserve committed vertices so the installed tool can
resume. `USER_CANCEL` clears a non-empty measurement, returns `CONSUME`, and leaves the tool active;
at `EMPTY` it returns `PASS` without repaint so the prior Escape binding can run. Every real state
change requests one ordinary full repaint; repeated equal preview samples and no-op clears do not.

The pointer policy is exact:

- every `MOVE` is consumed while the tool is active; with at least one vertex in `MEASURING`, a
  present map coordinate becomes the preview after checked distance calculation, while an empty map
  coordinate clears an existing preview; a move in `EMPTY` or `COMPLETE` changes no measurement;
- an unmodified, non-popup primary `CLICK` is consumed even when its map coordinate is empty; a
  count-one click appends its present coordinate, first clearing a completed path when necessary;
- a click count greater than one appends nothing and changes `MEASURING` to `COMPLETE` only when at
  least two vertices exist; Swing's ordinary count-one then count-two double-click sequence therefore
  adds the endpoint once and completes without a duplicate zero-length endpoint;
- a next count-one click after completion starts a fresh path; zero-length vertices intentionally
  added by separate count-one clicks remain valid;
- `PRESS`, `DRAG`, `RELEASE`, `WHEEL`, popup clicks, modified clicks, and non-primary clicks pass;
  any button press clears the transient preview before passing, but the tool never returns `CAPTURE`;
- `DELETE_BACKWARD` consumes and removes the last vertex when one exists, clears preview, and changes
  `COMPLETE` back to `MEASURING`; removing the sole vertex produces `EMPTY`; at `EMPTY` it passes so
  any prior Swing binding remains available.

A missing optional map coordinate is ordinary domain-boundary behavior, never a warning. It cannot
add a vertex or preview, but the qualifying move/click is still consumed so hover or selection is not
changed accidentally. Releases, lifecycle events, primary-drag pan, and wheel zoom remain routable in
screen space. Passed primary press/drag/release retains G3-001 navigation, and passed wheel retains
cursor-centered zoom. Measurement never captures, so the verification case proves absence of capture
rather than exercising a capture path.

G3-003's existing host ordering applies without a second state machine. A consumed measurement move
clears hover through the host's ordinary rule; a consumed measurement click leaves selection
unchanged. The tool does not read or mutate either state. Other passed input follows the existing
hover, selection, compatibility-listener, pan, and zoom order.

#### Concrete AWT measurement pass

Measurement painting extends the one captured MapView paint transaction in this fixed order:

```text
source features -> hover overlay -> selection overlay -> measurement overlay
```

At paint start, MapView includes the active `MeasurementTool`'s one immutable state snapshot with its
existing content, CRS operation, viewport, interaction, and renderer snapshots. Only an active
measurement tool contributes. The renderer never calls back into the live tool during traversal.
Measurement coordinates are in `mapCrs`, not display CRS; each vertex is transformed with the same
captured map-to-display operation and viewport used by that paint.

The AWT-private `MeasurementOverlayRenderer` draws committed segments with a fixed solid line,
preview with a fixed dashed line, and fixed logical-pixel vertex marks. The current segment label is
the preview segment when present, otherwise the last committed segment in either `MEASURING` or
`COMPLETE`; it is absent only before a second vertex. The total badge always formats
`displayedDistance`, so it includes the checked preview segment while previewing and otherwise shows
the committed total. A high-contrast white casing, opaque crimson line/vertex outline, white vertex
fill, and black text on a translucent white background are package-private constants. G11-002 may
introduce a theming decision later; G3 adds no public measurement style, scene graph, overlay SPI,
renderer registry, or label-placement engine.

Each vertex and segment is transformed independently. An absent endpoint skips that segment and is
never bridged to the next representable point. Before Java2D draw calls, each finite segment is
clipped analytically to the component rectangle expanded only by the fixed stroke/mark allowance, so
extreme but valid projected coordinates cannot create an extent-sized allocation or unbounded
rasterizer input. Java2D's component clip remains the final device boundary. Labels are emitted only
when their anchor is representable and intersect the component; the total badge uses fixed component
inset when any vertex exists.

Distance text is an AWT-private presentation detail. `Locale.ROOT`, no grouping, and
`BigDecimal.valueOf` with `RoundingMode.HALF_UP` produce exactly one decimal and `m` below 1,000
metres, or two decimals and `km` at and above 1,000 metres. Unit selection occurs before rounding, so
the boundary is stable. Public numeric results remain independent of this Level 1 display choice.
Font glyph pixels are not a rendering contract: tests assert the separately formatted text, path and
mark geometry, bounded paint regions, layering/color presence, and background outside the actual
overlay rather than whole-image or glyph identity.

Measurement graphics are not features. They do not use source logical-paint presence, cannot be hit
or selected, never create synthetic features, and retain no source identity. Every state change uses
G3-003's conservative full-component invalidation; no retained overlay bounds or cache is introduced
before G7 evidence.

#### Example, verification, and Native Image boundary

`examples/measurement-viewer` is created only with working behavior. Its public headless factory
constructs a tabbed content panel with two already-configured identity-CRS views: an EPSG:3857 metre
coordinate-plane example using `planarMetres`, and an EPSG:4326 longitude/latitude example using the
great-circle strategy. Each view owns its own measurement tool. Switching tabs does not mutate a
view's CRS or strategy. Copy in the planar tab states that its result follows projected coordinate
metres and is not corrected for Web Mercator ground-scale distortion.

API tests cover distance/state invariants, defensive packed storage, phase consistency, equality,
checked committed/preview totals, the command value, and the new cancel reason. Core tests cover
projected metre factory validation, exact strategy CRS, Euclidean segments, great-circle radius,
quarter-equator, antimeridian, poles, identical points, exact/near antipodes, zero segments, domain
failures, ordered accumulation, and the stated tolerances. Router tests cover command sequence/order,
PASS/CONSUME, illegal capture, callback/pending-lifecycle failures, cursor refresh, and available-host
user cancel.

AWT tests cover present/empty moves, add, zero-length add, count-one/count-two completion, triple-click
stability, new measurement after complete, Backspace in every phase, Escape with no tool and with
empty/non-empty state, replacement, cross-view claim/reuse, external cancellation/resume, vertex
limit, failure-before-mutation, no capture, pan/wheel coexistence, focus and `processKeyBinding`
fallback, hover clearing, selection preservation, and EDT confinement.
Offscreen tests prove independent-segment clipping, missing transform endpoints, fixed paint order,
full invalidation, non-hittable overlays, every phase's current-segment/committed-plus-preview total,
exact private formatting, and tolerant color/geometry invariants. The example test builds both tabs on
the EDT without a frame, checks their exact map CRS and strategy pairing, and paints representative
states offscreen.

The task's focused future implementation command includes the new example test, followed by the
normal quality gate. G3-004 does not create a separate specialized lane and requires no manual
checkpoint; G8-002 owns manual example review. The algorithms use JDK-only values and `StrictMath`,
and the command/measurement paths use explicit construction with no reflection or resources. G8-001
adds representative planar, geographic, state, and AWT measurement behavior to its aggregate native
scenario rather than creating task-specific metadata here.

#### G3 design closeout

G3 closes with one core session router, one MapView-owned hover/selection transaction, screen-space
hit predicates that share explicit symbol renderers, and one tool-owned measurement state painted by
a concrete AWT pass. Pointer, semantic command, lifecycle, hover, selection, and measurement ordering
now compose without a second router, generalized input framework, duplicate unit model, overlay
registry, measurement manager, synthetic feature, or capture gesture.

The cross-gate review makes three implementation-plan corrections explicit. G3-001 follows G4-002
because its approved context and optional event coordinates use the CRS contracts; G4-002 owns the
existing pointer/MapView optional-conversion migration but not not-yet-created G3 tool types. G3-003
keeps hover/selection state in MapView, full invalidation, complete `(layerId, featureId)` identity,
and exact `PRESENT`/`EMPTY`/`UNKNOWN` eligibility instead of the older task-card state-machine,
bounds, feature-ID-only, and undefined hidden-content wording. G3-004 follows G3-003 and G4-002,
reuses `CrsUnit`, treats missing coordinates as ordinary, proves it does not capture, and adds the
bounded command/AWT integration described above. These corrections change stale planning language,
not the intent of any approved prior design.
