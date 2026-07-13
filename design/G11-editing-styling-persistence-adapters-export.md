# G11 — Editing, styling, persistence, adapters, and export design

Project index: [DESIGN.md](../DESIGN.md).

## Editing, undo, and snapping model (G11-001)

### Profile and ownership boundary

The named HITL checkpoint **G11 editing command and snapping profile approval** approves this
point-first profile before implementation tasks are created. Editing is an application-owned in-memory
workspace, not mutation of a `FeatureSource`, `Layer`, geometry value, file, or parser. A successful
edit replaces complete immutable `FeatureRecord` values in one ordered session snapshot. Existing
read-only sources remain readable and may be copied into a session explicitly, but the session retains
no source, cursor, binding, path, format handle, or write-back callback.

The future implementation adds immutable contracts to `mundane-map-api`, the owner-thread state
machine and snap resolver to `mundane-map-core`, and one explicit editable binding plus one concrete
point controller to `mundane-map-awt`. It creates no editing module, mutable geometry hierarchy, generic
document model, command bus, event-sourcing store, topology engine, or service-provider boundary.
Swing and Java2D remain confined to AWT. API/core remain JDK-only and native-targeted code uses no
reflection, discovery, dynamic proxy, Java serialization, JNI, `Unsafe`, or internal JDK API.

One session has one exact `CrsDefinition`, one ordered feature namespace, one monotonically increasing
revision, one current immutable snapshot, and bounded undo/redo history. Opening captures the current
thread as its permanent owner. Every session read, edit, listener mutation, undo, and redo checks that
identity before touching state. An AWT editable binding therefore accepts only a session opened on the
Swing EDT, and all its later calls remain on that EDT. Immutable snapshots, commands, results,
problems, and events may be read on other threads after safe publication; the mutable session itself
may not migrate. An application may prepare an immutable initial snapshot elsewhere and open its
session from that snapshot on the EDT.

The first observable capability is a point-edit viewer backed by a real session and editable map
binding. It can create, move, and delete point records, undo and redo each committed transaction, and
snap a dragged point to a vertex or segment in an explicitly captured same-CRS reference set. The
session and generic replace operation accept every already sealed immutable geometry kind, but the
first interactive tools edit points only. Line/polygon vertex editing, topology, multi-selection, and
format write-back require later profiles rather than hidden generalization of the point tools.

### Immutable public values

The public model is deliberately closed and small:

```text
FeatureEditSnapshot(long revision, CrsDefinition crs,
                    List<FeatureRecord> records)

FeatureEditCommand =
  CreateFeature(FeatureRecord feature)
  ReplaceFeature(String featureId, FeatureRecord replacement)
  DeleteFeature(String featureId)

FeatureEditTransaction(long expectedRevision, String description,
                       List<FeatureEditCommand> commands)

FeatureEditStatus = APPLIED | UNCHANGED | REJECTED
FeatureEditCause = COMMIT | UNDO | REDO

FeatureEditProblem(String code, String message,
                   Map<String,String> context)

FeatureEditResult(FeatureEditStatus status,
                  FeatureEditSnapshot snapshot,
                  Optional<FeatureEditProblem> problem)

FeatureEditEvent(FeatureEditCause cause,
                 FeatureEditSnapshot previous,
                 FeatureEditSnapshot current,
                 String description)

FeatureEditListener.onFeatureEdit(FeatureEditEvent event)

FeatureEditNotificationException
  committedResult() -> FeatureEditResult

FeatureEditConfigurationException
  problem() -> FeatureEditProblem

PointFeatureDraft(String id, String name, Map<String,Object> attributes)
```

Records and command lists are defensively copied and non-null. A transaction is non-empty, has a
non-negative expected revision, and names each feature ID at most once. The session rejects a command
count over its captured limit before staging. The description is nonblank, preserved exactly, and at
most 128 UTF-16 characters; it
is an application label for history controls, not a stable ID or localization mechanism. Create always
appends after the current last record. Replace preserves the exact list position and requires the
replacement's exact ID to equal `featureId`. Delete removes that position. Reorder, attribute patch,
coordinate patch, merge, arbitrary command implementations, nested transactions, and command
serialization are absent.

`PointFeatureDraft` applies the same exact ID/name/canonical-attribute rules as `FeatureRecord` but has
no placeholder coordinate or symbol. The controller constructs the one immutable `PointGeometry` only
after a click supplies a usable map coordinate. A draft is toolkit-neutral data even though its first
consumer is AWT; it does not imply a generic feature builder or mutable geometry.

Constructors reject nulls, invalid feature values, duplicate command IDs, an ID mismatch, or malformed
problem context as programmer input before a session sees the value. A problem uses the same bounded
code/message/context grammar as `CrsProblem` and `SourceDiagnostic`, but is a distinct application-edit
value: edit conflicts are not malformed-source diagnostics and never enter a source report. The first
stable codes are:

```text
EDIT_REVISION_CONFLICT
EDIT_FEATURE_ALREADY_EXISTS
EDIT_FEATURE_NOT_FOUND
EDIT_FEATURE_LIMIT_EXCEEDED
EDIT_COMMAND_LIMIT_EXCEEDED
EDIT_SNAPSHOT_LIMIT_EXCEEDED
EDIT_HISTORY_ENTRY_LIMIT_EXCEEDED
EDIT_REVISION_EXHAUSTED
EDIT_NOTHING_TO_UNDO
EDIT_NOTHING_TO_REDO
EDIT_CRS_MISMATCH
EDIT_SELECTION_NOT_EDITABLE
EDIT_GESTURE_VIEW_CHANGED
EDIT_SNAP_LIMIT_EXCEEDED
EDIT_SNAP_COORDINATE_UNREPRESENTABLE
EDIT_SNAP_CANCELLED
```

Only applicable bounded keys from `expectedRevision`, `actualRevision`, `commandIndex`, `layerIndex`,
`featureIndex`, `componentIndex`, `partIndex`, `elementIndex`, `maximum`, `actual`, `expectedCrs`, and
`actualCrs`, and `reason` are used. `reason` is one of the listed finite ASCII tokens for its
problem; selection uses `empty`, `wrongLayer`, `missing`, or `notPoint`. Feature and
layer IDs are deliberately absent because their public values need not fit diagnostic context; the
caller owns the indexed command/reference and the result still preserves exact identity. A rejected
operation returns a `REJECTED` result and the exact current snapshot; it does not throw for an ordinary
stale revision, missing/existing feature, empty history, or configured limit. Unexpected implementation
failures and listener failures retain
ordinary Java propagation. `UNCHANGED` is returned only when every replace value equals its current
record; it publishes no revision, history, repaint, or event. Create and delete cannot be unchanged.

`FeatureEditResult` enforces exact variants: `APPLIED` and `UNCHANGED` carry no problem, while
`REJECTED` carries exactly one. A configuration problem is different from a session attempt:
`FeatureEditConfigurationException` is the unchecked, problem-bearing failure for constructing a
controller against incompatible CRS/view/binding values. It has no edit result because no controller
or session transition was attempted. `FeatureEditNotificationException` remains the only exception
that says an edit transition already committed.

`FeatureEditSnapshot` requires a non-negative revision, one exact CRS, and an ordered defensive record
copy with unique IDs. `FeatureEditEvent` requires equal CRS definitions, unequal snapshots, and
`current.revision == previous.revision + 1`; its description obeys the transaction bound.
`FeatureEditNotificationException` carries only an `APPLIED` result because unchanged/rejected attempts
emit no session event.

### Session transaction and revision semantics

`FeatureEditSession` is one public final core owner:

```text
FeatureEditSession.open(FeatureEditSnapshot initial,
                        FeatureEditLimits limits,
                        FeatureEditHistoryLimits historyLimits)
FeatureEditSession.open(CrsDefinition crs, List<FeatureRecord> records)

snapshot() -> FeatureEditSnapshot
limits() -> FeatureEditLimits
historyLimits() -> FeatureEditHistoryLimits
canUndo() / canRedo() -> boolean
undoDescription() / redoDescription() -> Optional<String>
apply(FeatureEditTransaction transaction) -> FeatureEditResult
undo(long expectedRevision) -> FeatureEditResult
redo(long expectedRevision) -> FeatureEditResult
addFeatureEditListener(FeatureEditListener listener)
removeFeatureEditListener(FeatureEditListener listener)
```

The convenience opener uses revision zero and the specified defaults. Opening captures and later
checks `Thread.currentThread()` as described above. An initial nonzero revision is accepted for a
caller-owned snapshot, but history always starts empty. Opening validates exact unique feature IDs,
the feature count, complete immutable values, logical snapshot payload, and one exact CRS before
retaining the defensive list. No source ID, layer ID, symbol, selection, or view belongs to the
session.

`apply` validates the expected revision before command semantics and stages all commands against one
private ordered candidate. Commands execute in declaration order, although the unique-ID rule means no
command observes another command against the same feature. The session validates the candidate feature
count, computes the complete inverse delta and its prospective history charge, and checks revision
increment before publishing anything. Any rejection discards staging and leaves snapshot, revision,
history, redo, and listeners unchanged. A successful non-equal candidate receives exactly
`previous.revision + 1`; the complete immutable snapshot, one history entry, and redo clearing publish
as one state transition before notification.

Revision is session history identity, not content identity. Undo and redo each require the exact
current revision, move one complete transaction between history stacks, restore all affected records
and their exact positions atomically, and publish a new monotonically incremented revision. They never
reset to an earlier number. A revision conflict wins before an empty-stack result. Undo/redo replay
private validated deltas rather than calling public commands. Deltas remain in original command order
and retain each command-time list position. Undo stages them in reverse order; redo stages them forward.
Thus shifting positions from earlier creates/deletes are inverted before older positions are restored.
Both paths validate revision increment and the complete candidate before moving the entry or publishing
state. Revision exhaustion, an unexpected replay invariant failure, or any staging failure leaves both
stacks, content, revision, and listeners unchanged; an invariant failure is an implementation defect,
not a user conflict.

The session is deliberately not `AutoCloseable`: its feature and history payload is bounded, and it
owns only ordinary state plus explicitly registered listener references. A host removes listeners when
detached. It has no finalizer, `Cleaner`, executor, lock, global registry, persistence, or background
callback.

### Bounded history and deterministic eviction

`FeatureEditLimits` and the separately introduced `FeatureEditHistoryLimits` own positive maxima with
complete withers and these initial defaults:

| Session limit (`FeatureEditLimits`) | Default |
| --- | ---: |
| Features in a snapshot | 100,000 |
| Commands in one transaction | 10,000 |
| Logical current-snapshot payload | 268,435,456 bytes |

| History limit (`FeatureEditHistoryLimits`) | Default |
| --- | ---: |
| Retained undo/redo entries | 256 |
| Retained logical history payload | 67,108,864 bytes |

Opening and every candidate publication charge eight bytes each for the revision, retained CRS
reference, and record-list reference; eight for each record-list slot; and the complete G4 logical
`FeatureRecord` content of each record occurrence. The immutable CRS is separately hard-bounded by G4
and is not charged again as owned text. Candidate staging must fit the same payload ceiling before the
private list is allocated or retained. The prior and candidate lists may coexist briefly, so peak
session-owned list/record logical payload is bounded by twice this ceiling plus history; the limit is
deterministic logical capacity, not a heap-size promise.

History stores only per-feature before/after values and exact list positions, never whole snapshots.
One retained stack entry charges exactly eight bytes each for its stack reference slot, description
reference, and delta-list reference; twice the description's UTF-16 length; and, for each delta, eight
bytes for the delta-list slot, one byte for command kind, four for original list position, eight for
each present before/after reference, and the complete G4 logical record content of each present
before/after value. There is no separately retained feature ID. The implementation extracts one
package-private record-measure helper from
`FeatureQueryAccounting`; it does not expose heap-size estimates or a general public memory-accounting
framework. Sharing an immutable object does not deduplicate its conservative charge.

An individual entry larger than the payload ceiling is rejected before the content commit, so every
successful ordinary commit is initially undoable. Staging a new commit prospectively treats the entire
redo branch as discarded, adds the new entry, then selects oldest complete undo entries for eviction
until both entry and byte ceilings hold. Only successful publication clears redo and performs those
evictions; rejection retains both stacks unchanged. It never removes a partial transaction. Moving an
entry between undo and redo leaves the combined charge unchanged. Branch replacement releases charges
deterministically. The current snapshot is not charged as history because the session must own it
regardless; limits do not claim actual heap usage or GC behavior.

### Notification and editable AWT binding

Listener identity and failure handling reuse G3's synchronous rules without its reentrant FIFO.
Registrations are visited from one snapshot in order, duplicates are permitted, removal removes the
first identical instance, and listener-list mutation affects only the next event. `apply`, `undo`, or
`redo` during edit-listener delivery is a programmer-state `IllegalStateException` before revision or
content inspection; bounded edit notifications therefore cannot recursively create an unbounded event
chain or cause a view to observe revision two before revision one. Read-only session access and
listener add/remove remain permitted during delivery.

State and history commit before delivery and are never rolled back by a callback failure. Delivery
continues after every `RuntimeException`. If one or more occur, the call throws one
`FeatureEditNotificationException` after all registrations have run; it carries the already committed
`FeatureEditResult`, uses the first listener failure as its cause, and attaches later distinct failures
as suppressed in encounter order without self-suppression. A tool can therefore complete selection and
preview cleanup without guessing whether content committed. An `Error` aborts remaining listeners and
propagates after the delivery guard resets. AWT controller operations reconcile from the session's
current snapshot in `finally` before propagating either failure. Events contain the authoritative
old/new snapshots and do not require a later mutable session read.

When the working behavior lands, `MapLayerBinding` gains an `editableFeature` factory accepting a
layer ID/name, one borrowed `FeatureEditSession`, and explicit marker/line/fill symbols. The binding is
not a `FeatureSource` and opens no cursor. It attaches to at most one `MapView`, validates the session
CRS exactly equals that view's map-coordinate CRS, captures one session snapshot per paint/hit/fit
operation, and maps its records through the existing map-to-display operation. Binding removal removes
its listener and claim but does not clear or close the caller-owned session.

An active point controller cannot outlive that target silently. `setLayerBindings` first copies enough
of its candidate to determine whether the exact target binding object is retained. If target removal is
requested while the G3 router is inside any pointer, command, activation, cancel, or deactivation
callback, MapView rejects that nested binding operation with a lifecycle `IllegalStateException` before
candidate validation, claims, controller queuing, or view/session mutation. It does not rely on G3's
deferred `clearActiveTool`, because a nested synchronous caller could not know that clear completed
before committing the binding list.

Outside router dispatch, MapView validates and claims the complete candidate as G4 requires, then
clears a target-losing controller synchronously through the existing router with
`CANCEL(TOOL_CLEARED)` and the old view/context before committing. Cancellation and deactivation clear
the gesture and preview idempotently and never edit. If either callback fails, G3 still leaves the
controller inactive; MapView releases candidate claims, keeps the old binding/listener installed,
reconciles and repaints that old content, and propagates the first router failure with later cleanup
failures suppressed. It does not commit or close any candidate/old binding. A successful clear is
followed by the ordinary binding-list commit, listener detachment, reverse close, and established
post-commit close-failure behavior. Retaining the same target binding object, including during reorder,
does not clear the controller and is not subject to this target-removal guard.

Every public controller operation and every pointer commit checks on the EDT that its target binding is
still installed in its exact view immediately before touching the session. A detached target is a
lifecycle `IllegalStateException`, not an edit rejection. A binding listener captured before a
permitted removal outside router dispatch no-ops if it is detached by the time its callback is reached;
the successful removal transaction has already reconciled selection/hover. A target-removing call from
a listener reached within point routing is rejected by the guard above and becomes that listener's
runtime failure; the edit may already be committed, but the binding remains installed and the session's
notification aggregation preserves the failure. A post-commit create never selects its feature if a
permitted removal completed during edit notification.

While attached, a session edit callback runs on the owner EDT, captures the already committed event snapshot,
reconciles the view's ID-based selection against that exact record list, clears hover because geometry
or order may have changed, requests one full repaint, and then uses the existing selection-before-hover
notification FIFO. A retained selected ID survives replace and unrelated edits; deleting it clears
selection. Creating a feature does not implicitly select it, and undo/redo restore content without
restoring historical selection or hover. Concrete edit tools may explicitly select a successfully
created or moved feature through their AWT host operation. A listener failure cannot prevent the
binding listener from eventually observing the event because session delivery continues after runtime
exceptions.

Programmatic session mutation from another thread fails the session's owner-thread check before state
change whether or not a binding is attached. Multiple borrowed bindings may observe one session only
on that same Swing EDT; a candidate view rejects the same session appearing twice in its binding list.
The binding never owns the session, makes it thread-safe, writes a source, or pretends a source's
viewport query is a mutable complete feature set. Importing from a source is an explicit bounded
application operation that drains and closes a cursor before opening a session.

### Snapping profile

Snapping is a pure, operation-local core query over immutable same-CRS feature snapshots. It performs
no source I/O, format access, view discovery, registry lookup, or hidden cache. The public immutable
values are:

```text
SnapTargetType = VERTEX | SEGMENT
SnapQueryStatus = SNAPPED | UNSNAPPED | REJECTED
SnapFeature(String featureId, Geometry geometry)
SnapReferenceLayer(String layerId, List<SnapFeature> features)
SnapReferenceSet(CrsDefinition crs, List<SnapReferenceLayer> layers)
SnapQuery(double screenX, double screenY, double tolerancePixels,
          CrsOperation coordinatesToDisplay,
          CrsOperation displayToCoordinates,
          MapViewport viewport, SnapReferenceSet references,
          Set<FeatureSelection> exclusions, SnapLimits limits,
          CancellationToken cancellation)
SnapResult(Coordinate coordinate, double distancePixels,
           SnapTargetType targetType, String layerId, String featureId,
           int componentIndex, int partIndex, int elementIndex)
SnapQueryResult(SnapQueryStatus status, Optional<SnapResult> result,
                Optional<FeatureEditProblem> problem)

FeatureSnapper.find(SnapQuery query) -> SnapQueryResult
```

Toolkit-neutral snap target/reference/result values live with the other public contracts in
`mundane-map-api`. The operation-local `SnapQuery` and stateless `FeatureSnapper` live in
`mundane-map-core` because they compose the core `CrsOperation` and `MapViewport` algorithms. No core
implementation type is added to an API value, and AWT types appear in neither module.

The eventual exact API may group geometry-location indexes in one immutable value, but it must retain
these semantics and may not expose AWT shapes or mutable index nodes. The pointer and returned
coordinate are respectively in screen space and the reference/edit session CRS. The two operations
must be exact opposites whose coordinate endpoint equals the reference/session/map CRS and whose world
endpoint equals the captured view display CRS; the viewport is expressed in that world CRS. This
reuses G4's explicit map/display transform while keeping every editable and snap-reference geometry in
one CRS. `SnapFeature` retains only exact identity and immutable geometry; names and attributes cannot
affect a snap or inflate its working set. Cross-CRS reference sets, source-backed live queries, and
per-layer operations are deferred; numeric coordinate ranges never imply CRS identity.

Reference layers have exact nonblank unique layer IDs; feature IDs are exact and unique within each
layer. Duplicate identities and an exclusion whose layer/feature strings are malformed fail reference
construction as programmer input rather than producing ambiguous target order.

The controller's supplied `SnapReferenceSet` contains external reference layers only, ordered from
lowest to highest explicit snapping priority; this is not claimed to mirror MapView paint order. Its
layer IDs must not equal the editable binding ID. At press, the controller converts the captured
session records to `SnapFeature` values in session order and appends that editable layer last. The
editable layer and its later records therefore win an otherwise exact distance/type tie. The combined
set is immutable for the gesture, and the moved feature's exact key is excluded. No other installed
MapView layer is discovered or queried implicitly.

`SNAPPED` carries exactly one result and no problem; `UNSNAPPED` carries neither; `REJECTED` carries
one stable problem and no result. Structural query-value failures remain ordinary argument failures.
The `SnapQuery` constructor requires finite screen coordinates; opposite operation endpoints;
`references.crs() == coordinatesToDisplay.sourceCrs() == displayToCoordinates.targetCrs()`; and equal
display endpoints in the other direction. A controller separately verifies that display endpoint is
its immutable `MapView.displayCrs()` before constructing a query because `MapViewport` itself owns no
CRS. A structural mismatch is a caller-value `IllegalArgumentException`; ordinary querying never
guesses or repairs endpoints.

`FeatureSnapper.find` translates only the two known `CrsException` failures possible from point
transformation: `CRS_COORDINATE_OUT_OF_DOMAIN` and `CRS_TRANSFORM_NON_FINITE` become rejected
`EDIT_SNAP_COORDINATE_UNREPRESENTABLE` with numeric layer/feature/element location and no raw ID or
coordinate. This applies to forward vertex/segment endpoints and the reverse closest-point transform.
Any other `CrsException` or unexpected runtime failure is a contract/implementation failure and
propagates unchanged. Limit, mapped CRS, domain, or non-finite failures reject the whole query rather
than returning ordinary absence or a partial candidate winner.

Tolerance is finite, in `(0, 256]`, measured in logical screen pixels, and inclusive. Default is
`8.0`. `SnapLimits` defaults to at most 256 reference layers, 100,000 features, 1,000,000 coordinates,
and 1,000,000 candidate segments per query. The exclusion set is defensively copied, contains at most
the feature limit, and uses exact layer/feature keys. The resolver checks an already-cancelled token
before traversal, then polls within controlled loops at least every 4,096 coordinates/segments and
immediately before publishing a winner. Cancellation returns rejected `EDIT_SNAP_CANCELLED`; a token
implementation failure propagates unchanged. The resolver checks prospective counts before traversing
the item that would cross a ceiling and returns the stable limit problem without a partial snap. It
linearly scans in declared layer/feature/geometry order; G7's packed index is not silently reused
because this is a distinct operation-local mixed-reference snapshot and no snapping profile evidence
justifies retained indexing yet.

After structural construction, deterministic failure precedence is: already-cancelled token; then for
each declared unit, cancellation, prospective layer/feature/coordinate/segment limit, and its coordinate
transform; then one final cancellation check before winner publication. The first failure terminates.

Every geometry coordinate is a vertex candidate. The duplicated terminal coordinate of a closed ring
does not create a second vertex identity. Every consecutive non-zero-length line/ring segment is a
segment candidate; zero-length segments are skipped. Excluded features contribute no count or
candidate. Points and multipoints contribute only vertices.
Lines/multilines contribute their part vertices and segments without bridging parts. Polygons and
multipolygons contribute exterior and hole rings independently, including their explicit closing
segment. Segment snapping uses the clamped closest point on the exact straight screen segment painted
between transformed endpoints, converts that screen point through the captured viewport to display
coordinates, and uses the exact reverse operation to obtain the returned edit coordinate. A vertex
winner returns its original exact coordinate. No topology, curve, intersection, midpoint, grid, angle,
tangent, or inferred polygon interior target is created.

Candidates outside the inclusive pixel tolerance are rejected. Coordinate deltas whose absolute x or
y exceeds the at-most-256-pixel tolerance are discarded before squaring; the remaining squared
distance calculation is finite. Segment projection uses scaled checked arithmetic and rejects a
non-representable intermediate rather than relying on overflow. Survivors are ordered by exact
`Double.compare` of squared screen distance, then `VERTEX` before `SEGMENT`, then highest-priority
layer and feature (reverse declared layer and feature order), then ascending component, part/ring, and
element indexes.
This makes a vertex win its coincident segment endpoint and makes every remaining tie reproducible.
Non-finite transform/intermediate results reject the whole query with the relevant edit problem rather
than choosing from a partial candidate set. With no survivor, snapping is `UNSNAPPED` and the point
tool uses the event's unsnapped finite map coordinate; if that optional coordinate is absent, it makes
no coordinate-dependent preview or commit.

Snap-location indexes are zero-based semantic indexes, independent of packed global offsets:

| Geometry | `componentIndex` | `partIndex` | `elementIndex` |
| --- | --- | --- | --- |
| Point | `0` | `0` | `0` |
| MultiPoint | point index | `0` | `0` |
| LineString | `0` | `0` | vertex or segment-start index |
| MultiLineString | line-part index | `0` | vertex or segment-start index within that part |
| Polygon | `0` | ring index (`0` exterior, then holes) | vertex or segment-start index within that ring |
| MultiPolygon | polygon index | ring index within that polygon | vertex or segment-start index within that ring |

An open line with `n` vertices has vertex indexes `0..n-1` and segment-start indexes `0..n-2`. A
closed ring with stored size `n` uses unique vertex indexes `0..n-2`; its ordinary segments start at
`0..n-3`, and the closing segment from vertex `n-2` to vertex `0` has element index `n-2`. The duplicated
terminal coordinate at stored index `n-1` is never a vertex identity or segment start. These same
indexes are reported in `SnapResult` and used by the final ascending tie rules.

An interactive gesture captures the session revision, immutable session/reference snapshots,
exact `MapViewport`, tolerance, and limits at press. Drag previews may query that fixed set repeatedly;
they do not observe mid-gesture edits or layer changes. While a controller capture is live, every wheel
event returns `CONSUME`, so G3 cannot zoom the viewport beneath the gesture. Default pan is already
suppressed by capture.

Resize, fit, or programmatic viewport replacement can still occur. Before every later routed event or
preview calculation, the controller compares `view.viewport()` with the exact captured immutable value. A mismatch
skips all coordinate work, clears the visible preview, and records one rejected
`EDIT_GESTURE_VIEW_CHANGED` against the current session snapshot. The triggering pointer event is
consumed; the router's physical capture remains until its matching release and the controller consumes
that remaining stream, but no session operation occurs and the rejection is notified only once.
Overlay painting independently omits a preview whenever the two viewport values differ, so a repaint
after a view change cannot draw old
gesture coordinates in the new transform even before another pointer event arrives.

With an unchanged viewport, release submits one replace transaction with the captured revision. A
concurrent edit therefore yields `EDIT_REVISION_CONFLICT`, clears the preview, and leaves the newer
content untouched. Escape, deactivation, focus loss, pointer-state loss, or explicit cancellation
clears preview without a transaction or history entry.

### Point controller and selection behavior

G3's `MapToolContext` deliberately exposes no selection, content, or view mutation. G11 does not widen
that toolkit-neutral context. AWT instead adds one final view-bound `PointEditController` implementing
`MapTool`. Construction takes the exact `MapView`, installed editable binding, optional immutable snap
reference set, and limits on the EDT. It retains no `Graphics`, callback context, source, or format
handle. Its public operations select `CREATE` with an immutable point draft, select `MOVE_SELECTED`,
delete the current selected editable point, undo, redo, inspect the last result, and register ordered
point-edit-result listeners. The host reference is intentional and confined to AWT: it supplies the
existing selection setters, active-tool lifecycle, session binding, and transient overlay integration
that the generic context must not expose.

Construction obtains the view's already-resolved immutable map-to-display and display-to-map operation
snapshots through a package-private AWT host path. It checks the binding session and every supplied
reference set use the exact map CRS and the operations' other endpoint is the exact display CRS. A
known mismatch throws `FeatureEditConfigurationException(EDIT_CRS_MISMATCH)` before listener
registration or controller publication. The exception carries bounded expected/actual canonical CRS
identifiers. Since a view's CRS configuration and a binding session's CRS are immutable, activation
does not rediscover or retranslate CRS; a removed/wrong target is instead the lifecycle failure below.

The controller is installed through ordinary `MapView.setActiveTool` and validates during activation
that it belongs to that view, its exact binding is still installed, and the session owner is the
current EDT. Deactivation clears gesture/preview state but not committed content or history. It is not a
general editor registry, mode framework, shortcut manager, or owner of the borrowed session or binding.

Its point behaviors are:

- `CREATE` commits one supplied immutable point draft at an unmodified primary click's usable map
  coordinate, explicitly selects it after success, and consumes that attempted click whether the
  session result applies or rejects;
- `MOVE_SELECTED` captures only from an unmodified primary press whose exact topmost hit equals the
  current editable point selection, previews the original point plus snapped candidate, commits one
  replace on release, and preserves that same selection; and
- `deleteSelected` is an explicit controller operation against the selected editable point and commits
  one delete; successful deletion lets normal reconciliation clear selection.

Every selection-dependent action first captures one complete session snapshot and reconciles the
view's selection against it. A selection is editable only when it is present, its layer ID exactly
equals the controller binding ID, its feature ID exists in that snapshot, and that record owns a
`PointGeometry`. `deleteSelected` returns one rejected `EDIT_SELECTION_NOT_EDITABLE` result with
`reason=empty|wrongLayer|missing|notPoint` when the corresponding check fails; it performs no session
operation and leaves a valid other-layer selection unchanged. Reconciliation clears a stale missing
selection from the target binding before publishing the rejection.

In `MOVE_SELECTED`, an eligible press captures that same session snapshot/revision and asks MapView's
existing G3 symbol-aware hit traversal for one result at the event screen coordinate with
`DEFAULT_SELECTION_TOLERANCE_PIXELS`. The view captures every other layer, viewport, CRS operation, and
renderer once, but uses the already captured edit snapshot for the target binding. Reverse paint order,
polygon holes, symbol footprints, opacity, and renderer registration therefore select the same exact
topmost `(layerId, featureId)` as ordinary selection. An empty/wrong-layer/absent/non-point current
selection publishes the same rejected selection problem and returns `PASS` without a gesture. A valid
editable selection whose topmost hit is absent or a different key also returns `PASS`, but silently:
the edit target is valid and that press simply did not name it. A missing map coordinate or any
modified, non-primary, chorded, or non-press event likewise passes without edit. Only an exact hit of
the valid selection returns `CAPTURE`; a source-backed layer encountered by the ordinary whole-view hit
traversal retains G4's existing bounded query/report behavior and is never copied into edit state.

After a create commits—also when its result comes from
`FeatureEditNotificationException.committedResult()`—the controller verifies the exact target binding
is still installed and the committed complete snapshot contains the new ID as one point. Before point
result listeners run, it selects that key through MapView's editable-selection path against that same
snapshot/revision. Reentrant session edits are already forbidden during session notification, so a
different current revision is an implementation-state failure rather than a second lookup policy. A
rejected create, a removed target, or a non-point/missing committed value never selects anything.

The controller retains only immutable gesture snapshots and uses the existing G3 router, capture,
`USER_CANCEL`, cursor, repaint, and callback-failure semantics. It does not modify session content on
each drag sample. Failed/rejected release clears preview, reports the `FeatureEditProblem` through one
ordered AWT edit-result listener, and consumes the completed gesture; it never falls through into pan
or click selection. Undo and redo are
explicit controller methods and example actions, not overloaded uses of G3 measurement's
`DELETE_BACKWARD` command or guessed platform shortcuts. Key bindings remain application-owned.

A rejected snap query becomes one `FeatureEditResult(REJECTED, currentSnapshot, problem)` before the
controller clears preview, updates `lastResult`, and notifies its result listeners. It consumes the
pointer event and performs no session operation. A configuration exception occurs before a controller
exists and therefore changes no `lastResult` and notifies nobody. Activation lifecycle failures and
unexpected CRS/runtime failures propagate through G3's existing activation/callback path and likewise
do not masquerade as an edit result. Ordinary applied/unchanged/rejected session results and the
committed result carried by a notification exception do update `lastResult` before result delivery.

In `CREATE`, an absent map coordinate passes silently because G4 defines it as ordinary blank margin.
Otherwise the controller resolves snapping and performs exactly one create attempt; its applied,
unchanged-impossible, or rejected outcome follows the consume/selection/result rules above so default
click selection cannot replace the controller's decision.

Point-result listeners use one registration snapshot in declaration order; duplicates and identity
removal match the project listener convention. Controller mutations during result delivery are
programmer-state failures. Delivery continues after runtime failures, then rethrows the first with
later distinct failures suppressed; `lastResult` and any committed edit remain authoritative. An
`Error` aborts later result listeners after the controller resets its delivery guard. No second event
queue or result-notification exception is added.

Every controller call that invokes the session handles an ordinary result or
`FeatureEditNotificationException.committedResult()` identically for content/selection cleanup, then
delivers its point result, and propagates the notification exception after host reconciliation. That
session-notification exception remains primary; any runtime point-result notification failure is
attached as suppressed. A `finally` reconciliation also covers an `Error` after commit. A pre-commit
implementation failure leaves content unchanged and still clears only the transient gesture that
initiated it.

Preview and snap indicators are non-destructive AWT overlays painted after feature labels and before
the existing hover/selection overlays, using fixed built-in symbols and no mutation of the authoritative
feature snapshot. G11-002 label layout and G11-005 export consume committed snapshots only; transient
editing overlays are neither persisted nor exported.

### Verification and implementation decomposition

The decision task changes design and task text only. Later implementation is decomposed into these
reviewable vertical slices; the task index gains the files only after this checkpoint is accepted:

1. **G11-010 — Immutable point-edit session slice:** deliver commands, transactions, revisions,
   snapshots/results/problems, the apply/event portion of the core session, editable binding, and a
   viewer that programmatically creates/replaces/deletes points through the real render stack. It does
   not add placeholder history, snap, or tool methods.
2. **G11-011 — Bounded undo/redo slice:** deliver delta accounting, eviction, history controls, event
   ordering, the separate history-limits value and final opener overload, and viewer undo/redo with
   rejection and rollback evidence. The G11-010 opener remains and delegates to default history once
   working history exists.
3. **G11-012 — Same-CRS snap resolver slice:** deliver the bounded vertex/segment resolver, immutable
   reference capture, deterministic tie behavior, and a visible snap-preview scenario.
4. **G11-013 — Point editing tool completion:** deliver create/move/delete interaction, selection and
   cancellation integration, runnable viewer completion, Javadocs, render-regression scenarios, and a
   representative Linux Native Image smoke before making an editing-native claim.

The dependencies are `G11-001 -> G11-010 -> G11-011`, `G11-010 -> G11-012`, and
`G11-011 + G11-012 -> G11-013`. The eventual surface sketched above is introduced only with its working
slice; no earlier task adds a throwing stub, empty module, unused registry, or speculative extension
interface.

API tests cover every immutable-value invariant, defensive copy, closed command variant, problem
bound, and feature-ID rule. Core tests cover atomic multi-command staging, command order, all
rejections with no state/history/event mutation, unchanged transactions, monotonic revision,
undo/redo branch replacement, forward/reverse mixed-command delta replay and exact position restoration,
revision-exhaustion stack preservation, just-below/at/above snapshot and history
limits, oldest-whole-entry eviction, owner-thread enforcement, reentrant-listener mutation rejection,
post-commit notification failure results, and every geometry's snap candidates and exact location
indexes, cancellation, limits, distances, priority ties, holes, parts, repeated endpoints, and
zero-length segments.

AWT tests cover binding claims and CRS rejection, one snapshot per operation, same-ID selection
continuity, deletion clearing selection, hover invalidation, event/repaint order, EDT enforcement,
controller/view ownership, retained versus removed target bindings, clear/cancel/deactivate/replacement
failure order, nested target-removal rejection, every invalid-selection reason, selected-versus-topmost
hit cases, create-selection snapshot validation, capture/release/conflict/cancel paths, captured-wheel
consumption, programmatic/resize/fit viewport mismatch and preview omission, unsnapped and snapped
previews, known CRS translation,
post-commit listener failure cleanup, no edit during drag, and no source cursor/write-back. The viewer
manually demonstrates create, move, delete, undo, redo, vertex
snap, segment snap, selection, pan/zoom coexistence, and rejected stale gesture behavior. Rendering
assertions use geometry/bounds/color tolerances, not cross-platform pixel identity.

Collaborative editing, remote conflict resolution, persistent command logs, arbitrary command
plugins, mutable geometry handles, line/polygon editing, topology validity/repair, snapping indexes,
grid/intersection/midpoint/angle snapping, multi-selection, format writers, transactions across
sessions, and JTS coupling remain out of scope. These omissions are deliberate boundaries, not empty
extension points.
