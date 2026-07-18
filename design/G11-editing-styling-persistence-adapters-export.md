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

Decision record (2026-07-17): **G11 editing command and snapping profile approval** is approved
through the maintainer's advance HITL authorization for dependency-free remaining tasks. Approval
selects the owner-thread immutable-record session, bounded delta history, same-CRS snap resolver,
point-only controller, and G11-010 through G11-013 exactly as specified above. It creates no API,
production code, module, implementation task file, source write-back path, or external dependency.

## Thematic portrayal and point-label placement (G11-002)

### One binding-owned portrayal, not a styling language

The first styling profile adds one immutable toolkit-neutral `FeaturePortrayal` value. A vector
binding owns exactly one portrayal and applies it to every immutable feature snapshot it presents.
The portrayal has one optional selector for each existing marker, line, and fill role plus one
optional singular-point label profile. An absent role selector deliberately omits that geometry role.
At least one selector must be present, and a label profile requires a marker selector.

This is a closed value model, not a callback, predicate tree, expression language, stylesheet, or
renderer registry. It cannot inspect the viewport, layer, feature ID, geometry coordinates, another
attribute, mutable application state, or AWT state. The existing explicit `SymbolRendererRegistry`
continues to render the selected immutable symbol; thematic styling does not create a second dispatch
or plugin surface.

The public API shape is:

```text
FeaturePortrayal(
  Optional<SymbolSelector> marker,
  Optional<SymbolSelector> line,
  Optional<SymbolSelector> fill,
  Optional<PointLabelProfile> pointLabel)

sealed SymbolSelector
  FixedSymbolSelector(Symbol symbol)
  CategoricalSymbolSelector(
      String attribute,
      List<CategoricalSymbolRule> rules,
      Optional<Symbol> fallback)
  GraduatedSymbolSelector(
      String attribute,
      List<GraduatedSymbolStep> steps,
      Optional<Symbol> fallback)

CategoricalSymbolRule(ThematicValue value, Symbol symbol)
GraduatedSymbolStep(BigDecimal lowerInclusive, Symbol symbol)
```

`mundane-map-api` owns those values plus `ScreenBox`, the label configuration/output values, and
label failure values. `ScreenBox(minX, minY, maxX, maxY)` requires finite ordered edges and represents
a half-open logical-screen rectangle. `mundane-map-core` owns the immutable resolver and greedy
placement algorithm plus its immutable operation-input batch. `mundane-map-awt` owns binding
integration, Java2D text metrics, transient association of a placed result with its `TextLayout`, and
drawing. No new module is introduced.

Every selector exposes its one `SymbolRole`. Fixed selectors derive it from the symbol. Thematic
selectors require a non-empty rule/step list and require every listed and fallback symbol to have the
same role. `FeaturePortrayal` requires its three positions to contain only the matching role. These
checks occur during immutable-value construction, before a binding is claimed. A fixed selector with
one symbol is used instead of a one-rule thematic selector with no attribute meaning.

`FeaturePortrayal.fixed(marker, line, fill)` is the convenient no-label form. A `withPointLabel`
factory returns another immutable value; there is no mutable builder or live style setter. Existing
source and future editable-binding factories that accept three role symbols delegate internally to a
fixed portrayal and add the internal compatibility label described below. Existing snapshot bindings
retain each `Feature.symbol()` through a private
feature-owned selector adapter; `MapLayerBinding.portrayedSnapshot(layer, portrayal)` is the explicit
override for applications that want binding-level thematic portrayal. Source and editable bindings
gain corresponding portrayal overloads. Changing portrayal means transactionally replacing the
binding, which already supplies attachment, repaint, interaction-reconciliation, and cache-invalidation
boundaries.

G11 replaces the G1 per-feature convenience-label draw with this one global label pass. Compatibility
factories install one exact internal profile: `FeatureName`, opaque `rgba(32,32,32,255)`, `NORMAL`,
12 logical pixels, positions `[NE]`, gap 4, offsets `(0,0)`, collision padding 1, priority 0, and the
inclusive resolution range `[Double.MIN_VALUE, Double.MAX_VALUE]`. Existing APIs therefore continue
to show isolated non-blank point names in the same dark color and north-east intent without carrying
two label engines. The measured visual box is now separated from the marker's top/right by four
pixels; it deliberately replaces rather than reproduces G1's unmeasured baseline at
`(marker.maxX + 4, marker.minY - 2)`. Collision omission is likewise an intentional Level 2 visual
change. An explicit `FeaturePortrayal` has no labels unless its caller supplies a profile.

### Exact categorical and graduated semantics

`ThematicValue` is a closed immutable match value with `TEXT`, `LOGICAL`, `NUMERIC`, `DATE`, and
`NULL` forms. Text and date retain exact value equality. Logical retains its boolean. Numeric stores a
normalized `BigDecimal`: integral attributes use `BigDecimal.valueOf(long)`, finite floating values
use `BigDecimal.valueOf(double)`, decimal attributes use their value, then trailing zeros are removed
and every numeric zero becomes `BigDecimal.ZERO`. Numeric categories therefore make `1L`, `1.0d`, and
`1.00` equal without parsing text or using binary floating comparison. `AttributeNull.INSTANCE` maps
to `NULL`; a missing key remains distinct. `AttributeBytes` is not a thematic value in this profile.

A categorical selector preserves its declared rule list for equality and documentation but rejects
two rules whose values are equal after numeric normalization. Evaluation converts the one present
canonical attribute when supported and performs one exact lookup. A missing key, unsupported binary
value, or unmatched value chooses the optional fallback; without a fallback it returns no symbol.
`NULL` can be matched explicitly. There is no first-match precedence, wildcard, coercion, regex,
locale, case folding, range rule, or implicit `toString`.

A graduated selector accepts only a canonical `Long`, finite `Double`, or `BigDecimal` attribute and
normalizes it by the same numeric rule. Steps are declared in strictly increasing normalized order.
The selected symbol is the step with the greatest lower bound less than or equal to the value, so the
implicit classes are `[lower[i], lower[i+1])` and the final class is `[lower[last], +infinity)`. A
value below the first threshold, a missing/null/non-numeric attribute, or an unsupported value uses
the optional fallback; otherwise it returns no symbol. NaN and infinities cannot enter a canonical
`FeatureRecord`, and selectors do not parse numeric text.

Categorical selectors contain at most 256 rules and graduated selectors at most 64 steps. Attribute
names reuse G4's exact non-blank, at-most-256-UTF-16-character rule. Construction rejects unordered or
duplicate thresholds, normalized duplicate categories, nulls, wrong roles, and excessive counts with
field-naming `IllegalArgumentException`; these are configuration failures, not source diagnostics.
Missing, null, wrong-type, unmatched, and below-range data follow the declared fallback/omission policy
and emit no warning per record, preventing diagnostic floods for ordinary thematic data.

`FeaturePortrayalResolver` in `mundane-map-core` compiles one immutable portrayal into categorical
lookup maps, graduated threshold arrays, an ordered unique symbol-attribute list, and an optional
label-attribute name. It performs no I/O and retains only the immutable configuration. It resolves a
geometry role plus canonical attributes to an optional symbol and resolves label text as described
below. This is a small reusable algorithm boundary for AWT and later export, not a general expression
evaluator or cache.

### Binding, source-query, and interaction behavior

The compiled symbol-attribute order is marker selector, line selector, then fill selector, with
exact-name duplicates removed on first occurrence. Paint appends the label attribute on first use only
when the profile is visible at the captured resolution. Hit/hover/click operations request only symbol
attributes because labels are not hittable; fit requests `NONE`. A source-backed portrayed binding
uses `AttributeSelection.ONLY` with the operation's non-empty list, or `NONE` when it is empty; it never
widens a query to `ALL`. If the source has a known schema, attachment checks every selector and label
field and rejects an absent one before claiming the binding. A dynamic schema may omit a field per
record, which follows normal fallback/no-label behavior. Snapshot and editable records already own
their canonical attributes and need no projection.

Attachment also recursively preflights renderer availability and value compatibility for every symbol
reachable from every rule, step, and fallback, not merely a fixed or currently selected value. This
reuses G4's transactional candidate validation and existing symbol failures; a later data value cannot
surprise an already attached binding with an unregistered renderer.

Paint and hit operations capture the portrayal/resolver with the same content, viewport, CRS, and
registry snapshot already required by G3/G4. They resolve each encountered feature once for its
geometry role in that operation. The selected symbol follows the existing component dispatch,
renderer lookup, paint-presence, clipping/simplification, and private G7 cache paths unchanged. An
absent selection paints no geometry and contributes no hit, hover, click selection, interaction
overlay, or label. It remains source content for query/extent/fit and stable ID purposes: a previously
stored or programmatically assigned ID selection may persist invisibly, but pointer interaction cannot
create it and no presentation overlay is painted until that immutable binding resolves a symbol.
This preserves G3's identity semantics without treating a portrayal as a source filter.

General labels are annotations and are not part of symbol hit footprints. Text never makes an
otherwise omitted or transparent marker hittable, and label boxes are not searched by `hitTest`.
Hover and click selection therefore continue to select geometry/symbol paint, not glyphs. A selected
point's label is painted in the ordinary label pass, not duplicated in hover/selection overlays.

Fit and source extent operations continue to use authoritative geometry and `AttributeSelection.NONE`;
thematic omission cannot make an extent depend on a viewport or presentation rule. G7's screen-plan
cache remains keyed by authoritative immutable geometry, viewport, operation, clip, and exact stroke
margin; selected color and opacity do not invalidate geometry plans, while a changed symbol margin
already misses. Vector templates remain keyed by immutable path identity. No selected-symbol,
attribute, label, or `TextLayout` cache is added.

### Bounded singular-point label profile

The initial label profile supports only one label for a singular `PointGeometry`. Multipoints,
lines, polygons, repeated labels, curved text, area anchors, shields, wrapping, rich text, and
locale-aware formatting remain later work. The public toolkit-neutral values are:

```text
PointLabelProfile(
  LabelTextSource textSource,
  LabelTextStyle style,
  List<PointLabelPosition> positions,
  double gapPixels,
  double offsetXPixels,
  double offsetYPixels,
  double collisionPaddingPixels,
  int priority,
  ResolutionRange visibleResolution)

sealed LabelTextSource
  FeatureName
  TextAttribute(String attribute)

LabelTextStyle(Rgba color, LabelWeight weight, double sizePixels)
LabelWeight = NORMAL | BOLD
PointLabelPosition = N | NE | E | SE | S | SW | W | NW
ResolutionRange(double minUnitsPerPixelInclusive,
                double maxUnitsPerPixelInclusive)
```

The logical family is fixed to Java's `SansSerif`; public values never contain `Font`,
`FontRenderContext`, `GlyphVector`, `Shape`, or `TextLayout`. Size is a finite 6 through 72 logical
screen pixels. Label color must have positive alpha; an invisible label is rejected as configuration
rather than silently reserving collision space. Gap and collision padding are finite 0 through 64
pixels; each offset is finite and within -256 through 256 pixels. Positions are non-empty, unique,
retain declaration order, and contain at most all eight values. Resolution endpoints are finite,
positive, ordered, and inclusive. The current exact `MapViewport.unitsPerPixel()` decides visibility,
avoiding a synthetic zoom level.

`FeatureName` uses the immutable record/feature display name. `TextAttribute` uses only a present
canonical `String` under its exact field name. Missing, `AttributeNull`, non-text, or blank (`isBlank`)
input produces no candidate and no diagnostic. Text is neither trimmed nor formatted. A candidate
may contain at most 256 Unicode code points and may not contain CR, LF, or Unicode line/paragraph
separators; violations use the stable label failures below rather than truncation. Tabs and other
characters are passed to the JDK text implementation as one logical line. No numeric/date formatting,
locale, template, expression, fallback text, or ellipsis policy is implied.

After a marker symbol renders successfully, the label request uses the final union of its nominal
axis-aligned marker bounds. G2 requires every `MARKER` result to contain those bounds, including a
zero-opacity marker; an absent result remains `SYMBOL_RENDERER_INVALID_RESULT` with `role` and `key`
and never becomes a fallback point anchor. The configured x/y offset translates that reference box
before candidate placement. For each declared compass position, the measured text visual box is
aligned outside the corresponding side or corner with the exact gap on each separated axis; a cardinal
position centers the other axis. The translated visual bounds, expanded by collision padding, are the
collision box. Baseline coordinates come from that same translation, so paint and collision cannot
disagree.

### One global deterministic placement pass

AWT creates `new Font("SansSerif", PLAIN|BOLD, 1).deriveFont(size)` and one `TextLayout` per eligible
request through one package-private `LabelTextMetrics` helper. G11-005's holistic review fixes that
helper's immutable metric profile for both ordinary painting and export capture:

```text
FontRenderContext(
  identity AffineTransform,
  RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
  RenderingHints.VALUE_FRACTIONALMETRICS_ON)
```

The helper never reads the current child graphics' device transform or hint state. It extracts only
finite visual bounds and advance into toolkit-neutral immutable placement input and returns one
package-private pair containing those scalars plus the operation-local `TextLayout`. Ordinary paint
retains the layout only until drawing; capture copies only the scalars and drops it. Before drawing,
the child graphics sets the same
text-antialias and fractional-metrics hints; its ordinary device transform then maps the approved
logical layout to the screen. `MapView.captureVectorExportSnapshot` invokes the same helper on the EDT
without a `Graphics2D` and discards every AWT value after copying the placed-label scalars. There is no
retained last layout or second metric path. Installed logical-font mapping and exact glyph outlines
remain JDK/platform behavior; identical text/profile inputs on one runtime use one metric result, but
pixel-identical glyphs across operating systems are not promised.

`GreedyPointLabelPlacement` in core receives the logical component box, measured requests, and exact
paint ordinals. AWT assigns consecutive `int` ordinals from zero only to eligible requests as they are
encountered in ordinary layer/feature paint order; the request limit makes overflow impossible, and a
later/topmost request has the larger ordinal. Placement compares priorities and ordinals directly,
without subtracting and risking overflow. It orders requests by descending explicit priority, then
descending ordinary paint ordinal so the topmost layer/feature wins an equal-priority collision. It
visits a request's positions in declared order and accepts the first collision box wholly contained in
`[0,width) x [0,height)` that has no positive-area intersection with an already accepted box. Boxes
that only touch at an edge or corner do not collide. If every candidate is clipped or collides, that
label is normally omitted. The initial algorithm linearly scans accepted boxes; the hard limits below
bound this deliberately simple implementation before any profiling justifies a spatial index.

The placement result contains immutable toolkit-neutral `PlacedPointLabel` values with layer/feature
ID, exact text/style, baseline x/y, measured advance, visual bounds, collision bounds, and ordinary
paint ordinal. It contains no AWT value. Accepted values are returned in ascending ordinary paint
ordinal for drawing, even though priority/topmost order decided admission. This stable handoff lets
G11-005 consume already placed labels and an explicit measured advance without making an AWT-free SVG
writer measure fonts or duplicate collision policy.

The paint stack becomes:

```text
all vector/raster feature geometry in ordinary layer/feature order
all accepted point labels in ordinary paint order
G11-001 editing preview and snap indicators
G3 hover overlay
G3 selection overlay
G3 measurement overlay
```

Raster layers produce no label requests. Measurement graphics retain their G3 tool-owned pass and do
not enter the feature-label engine. Label collision tests consider other accepted label boxes only;
they do not reserve geometry, markers, editing graphics, component insets, or future UI chrome. AWT
draws each accepted `TextLayout` once at the approved baseline with `SRC_OVER` and the configured RGBA
color, restoring graphics state per draw. There is no background, halo, shadow, font download, device
pixel snap, or glyph-outline export in the first profile.

### Limits, failures, and cache policy

One paint may collect at most 4,096 eligible label requests, evaluate at most 32,768 candidate boxes,
perform at most 10,000,000 accepted-box collision comparisons, and retain at most 262,144 total label
code points. These are fixed first-profile limits shared by the AWT collector and core placement
utility, with checked arithmetic before retaining the next request, candidate, or comparison. Eight
positions per request make the candidate limit independently explicit. The quadratic collision scan
is therefore bounded; G11-024 records real-stack evidence before any grid, tree, or label cache is
proposed.

Expected omissions—resolution exclusion, missing/blank/wrong-type text, no selected symbol,
off-viewport candidates, and collisions—are not diagnostics. Invalid public configuration uses normal
field-naming argument failures. Runtime hard failures use `LabelPlacementException` with one immutable
`LabelPlacementProblem` and stable codes:

- `LABEL_REQUEST_LIMIT_EXCEEDED` with `limit` and `attempted`;
- `LABEL_CANDIDATE_LIMIT_EXCEEDED` with `limit` and `attempted`;
- `LABEL_COLLISION_WORK_LIMIT_EXCEEDED` with `limit` and `attempted`;
- `LABEL_TEXT_LIMIT_EXCEEDED` with `layerIndex`, `featureIndex`, `limit`, and bounded
  `attemptedAtLeast`;
- `LABEL_TEXT_BUDGET_EXCEEDED` with `limit` and `attempted`;
- `LABEL_TEXT_MULTILINE_UNSUPPORTED` with `layerIndex`, `featureIndex`, and bounded `codePoint`;
- `LABEL_METRICS_NON_FINITE` with `layerIndex`, `featureIndex`, and bounded `quantity`; and
- `LABEL_LAYOUT_NON_FINITE` with `layerIndex`, `featureIndex`, `position`, and bounded `quantity`.

Context values are insertion ordered, ASCII keys are fixed above, messages contain no full label text,
and request-specific contexts always put zero-based captured `layerIndex` before zero-based
`featureIndex`. They are checked non-negative `int`/`long` traversal values under existing binding and
source-query counts, distinguish legal same-ID features in different layers, and follow G11-001's
bounded-problem precedent without copying unbounded public IDs. The request/candidate/work/budget
codes describe the whole batch and have no offending identity. Collection/layout completes before any
label is drawn, so a failure leaves the geometry pass visible but never publishes a partial label
pass. Unexpected JDK text or renderer failures propagate unchanged. Label failures are presentation
failures, never `SourceDiagnostic` entries and never attributed to a parser.

The portrayal resolver's immutable lookup tables are configuration, not a result cache. Placement,
metrics, and `PlacedPointLabel` values live for one paint/export snapshot and are released afterward.
There is no weak/global cache, background layout, repaint listener, or retained last-layout value in
`MapView`. A later cache requires a G7-style profile, key/invalidation proof, and measured benefit.

### Verification, decomposition, and checkpoint

API tests cover immutability, role checks, all value/count/range boundaries, numeric normalization,
normalized duplicate rejection, exact text/date/logical/null equality, and ordered defensive copies.
Core tests cover categorical fallback/omission, graduated lower-inclusive boundaries, required-field
deduplication, label text extraction, all eight candidate alignments, inclusive resolution edges,
priority/topmost admission, declared-position fallback, touching versus overlap, clipping, stable
returned paint order, every hard limit, checked overflow, and stable failure context. Deterministic
metric stubs make placement tests independent of installed fonts.

AWT integration tests cover portrayed snapshot/source/editable records, exact `ONLY` attribute
projection, known/dynamic schemas, role omission, compatibility-label migration, selector agreement
between paint/hit/hover/click/overlay, recursive renderer preflight, mandatory marker-bound anchors,
missing-bound `SYMBOL_RENDERER_INVALID_RESULT`, positive-alpha font/weight/size/color,
geometry-label-edit-hover-selection-measurement order, and graphics-state isolation. Rendering
regression asserts selector decisions, label count/order, non-overlapping/inside logical boxes, and
broad tolerant ink regions; it never hashes glyph pixels. The manual example uses deliberately wide
separations around font-sensitive boundaries and is reviewed on one named desktop without making a
cross-platform glyph-identity claim.

Implementation is deferred into these reviewable vertical slices after this profile is approved; this
design task creates no task files or modules for them:

1. `G11-020` adds immutable portrayal values/resolver and a fixed/categorical marker slice through
   snapshot, source, and editable bindings.
2. `G11-021` adds graduated selection and complete marker/line/fill role behavior with query and
   interaction agreement.
3. `G11-022` adds point-label values, text extraction, AWT metrics, and the global label paint pass.
4. `G11-023` adds bounded greedy collision placement, the styling/label example, and tolerant
   `renderRegression` scenarios.
5. `G11-024` adds Javadocs, performance evidence, consumer/publication checks, representative Linux
   Native Image verification, and the styling/label simplicity closeout.

The exact future dependency graph is `G11-010 + G11-002 -> G11-020`; `G11-020 -> G11-021` and
`G11-020 -> G11-022` (which may proceed in parallel); `G11-021 + G11-022 -> G11-023`; and
`G11-023 -> G11-024`. The G11-010 prerequisite supplies the editable binding consumed by G11-020;
none of these future tasks may introduce that surface early.

The named HITL checkpoint is **G11 thematic and point-label profile approval**. A maintainer approves
the selector equality/fallback semantics, single-point label/font/position profile, exact placement and
paint order, compatibility-label migration, annotation-only hit policy, limits/failures, example
scenarios, and five-slice decomposition before G11-020 is created. The result is the smallest design
that makes attribute-driven cartography and collision-aware labels useful without turning a small map
library into a stylesheet, expression, font, or cache framework.

Decision record (2026-07-17): **G11 thematic and point-label profile approval** is approved through
the maintainer's advance HITL authorization for dependency-free remaining tasks. Approval selects the
closed selectors, singular-point label profile, deterministic global placement, compatibility
migration, and G11-020 through G11-024 exactly as specified above. It creates no production API,
module, later task file, styling language, label cache, font dependency, or external dependency.

## Workspace persistence profile (G11-003)

### Portable configuration boundary

The first workspace format persists enough portable Level 1 configuration to reopen the same local
map without serializing live Java objects. One future JDK-only, AWT-free module named
`mundane-map-workspace` owns the immutable file model, secure reader, canonical writer, explicit
resource registries, and all-or-nothing opened session. It depends only on API, core, and the JDK
`java.xml` module; it does not depend on AWT, a concrete format module, or an external library and
exposes no format implementation type. The module is created only in G11-030 with working read
behavior and tests.

Version 1 persists exactly:

- the canonical Level 1 map-coordinate and display CRS keys, exactly `EPSG:4326` or `EPSG:3857`;
- display-space viewport center and units per logical screen pixel, but not component dimensions;
- ordered feature/raster layers with exact IDs and display names;
- one local relative source reference per layer;
- for a feature layer, one external named-symbol catalog ID and exact marker, line, and fill names; and
- for a raster layer, `NEAREST|BILINEAR` interpolation and finite opacity in `[0,1]`.

The current component size is runtime UI state. When an application constructs its view, it combines
the persisted center/scale with the actual positive component dimensions and lets `MapViewport`
validate the resulting finite edges. Raster and feature source metadata remain authoritative for fit,
diagnostics, and CRS validation; the workspace never copies their extents or schemas.

Version 1 deliberately does not persist snapshot feature data, source attributes, parser/decoder
limits, caches, diagnostics, catalog contents, renderer/decoder/CRS registries, selection, hover,
active tools, measurement, G11-001 edit snapshots/history/snapping, G11-002 portrayal/label profiles,
elevation/DTED, G10 formats/tiles, remote references, optional adapter configuration, credentials, UI
window geometry, or application objects. Fixed source bindings still receive their ordinary
compatibility point-name labels at runtime. These exclusions keep G11-003 dependent only on G8-004;
it neither serializes nor blocks the independent G11-001/G11-002/G9/G10 work.

The file suffix is `.mmap.xml`. This is an application convention checked case-sensitively by the
public file facade, not operating-system association or format sniffing. XML is selected because Java
21 supplies a bounded streaming parser; no JSON dependency, `Properties` encoding compromise, Java
serialization, object graph, annotations, databinding, schema compiler, or migration framework is
introduced.

### Immutable public model and ownership

The workspace module's initial public shape is:

```text
WorkspaceDocument(
    WorkspaceViewState view,
    List<WorkspaceLayerDefinition> layers)

WorkspaceViewState(
    String mapCrsKey,
    String displayCrsKey,
    double centerX,
    double centerY,
    double unitsPerPixel)

sealed WorkspaceLayerDefinition
  WorkspaceFeatureLayer(
      String id, String name,
      WorkspaceSourceReference source,
      WorkspaceSymbolReferences symbols)
  WorkspaceRasterLayer(
      String id, String name,
      WorkspaceSourceReference source,
      RasterInterpolation interpolation,
      double opacity)

WorkspaceSourceReference(
    String openerId,
    SourceIdentity identity,
    WorkspaceRelativePath path)

WorkspaceSymbolReferences(
    String catalogId,
    String markerName,
    String lineName,
    String fillName)
```

The transport version is not mutable domain state: the reader reports every version other than `1`
before model construction, and the writer always emits version 1. All values defensively copy ordered
lists. Layer IDs are unique across the document and retain their existing non-blank exact,
at-most-256-UTF-16-character rules. Each persisted `SourceIdentity` retains those same individual
value rules but may intentionally repeat when two differently portrayed layers open the same source.
Layer and source display names may be empty and are at most 256 characters. Catalog IDs use the
opener-ID grammar below. Symbol names are non-blank, at most 256 characters, and must equal their own
`strip()` result exactly as `NamedSymbol` requires; interior whitespace and Unicode remain exact.
Every persisted string, including fields copied from `SourceIdentity`, is validated at
workspace-value construction as an XML 1.0 Unicode-scalar sequence with no isolated surrogate or
disallowed code point. No identifier is trimmed, case-folded, normalized, or interpreted as a
path/URI. The hard model ceiling is 4,096 layers, while an operation's `WorkspaceLimits` may set a
lower ceiling. Construction also prospectively charges the exact retained-model inventory defined in
the limits section against a non-configurable 33,554,432-byte model ceiling. An empty workspace is
useful and valid.

`openerId` is at most 128 ASCII characters and follows G2's dotted key grammar: two or more nonempty
lowercase segments, each starting with `[a-z]` and continuing with `[a-z0-9-]`. It names an exact
application-registered, versioned opening policy rather than a Java class or a format option bag. A
caller-supplied opener is trusted application code and may perform only the access its application
explicitly authorizes; the workspace module cannot sandbox an opener, but it never supplies a URI,
credential, environment value, classpath resource, parser limit, or arbitrary string option to one.

`WorkspaceFiles.read(path, limits)` returns `WorkspaceFile(document, baseDirectory)`. The base is the
real parent directory captured by the successful file-read transaction and is not serialized.
`WorkspaceFiles.write(path, document, limits)` writes canonical version 1 bytes atomically as defined
below. A programmatically built document stores only `WorkspaceRelativePath`, so changing output
location never silently rewrites source references.

```text
WorkspaceOpener.open(
    WorkspaceFile file,
    WorkspaceOpenContext context,
    CancellationToken cancellation) -> WorkspaceSession

WorkspaceOpenContext(
    CrsRegistry crsRegistry,
    WorkspaceSourceRegistry sources,
    WorkspaceSymbolCatalogRegistry catalogs)

WorkspaceSession implements AutoCloseable
  document() -> WorkspaceDocument
  mapCrs() / displayCrs() -> CrsDefinition
  layers() -> immutable ordered List<OpenedWorkspaceLayer>
  isClosed()
  close()
```

An opened feature layer exposes one borrowed `FeatureSource` and its three resolved role-correct
symbols; an opened raster layer exposes one borrowed `RasterSource`, interpolation, and opacity. The
session owns every returned source and closes them in reverse layer order. Consumers may create
borrowed AWT bindings, but the session must outlive those bindings and the view must be closed before
the session. The future workspace-viewer demonstrates this exact try-with-resources order. There is no
hidden AWT adapter, global current workspace, finalizer, `Cleaner`, shutdown hook, background loader,
or source ownership transfer.

Session close marks the session closed before closing each source exactly once. The first close
failure remains primary and later failures are suppressed; repeated close is a no-op. The immutable
document, resolved CRS values, layer descriptors, source metadata, and opening reports remain readable
after close because the underlying G4 contracts already preserve them. Operational source calls after
close retain their existing lifecycle failure.

### Version 1 XML grammar

The namespace is exactly `urn:mundanej:map:workspace`; the root's `version` attribute is exactly
decimal `1`. Prefix spelling is not semantic: input may bind the workspace namespace as the default
or to one prefix, but every element must resolve to that namespace, ordinary attributes must be
unqualified, and no second explicit namespace binding is accepted. Namespace declarations are
validated separately from ordinary attributes. The canonical writer uses exactly one default
namespace declaration. Element order is fixed and layer order is data:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<workspace xmlns="urn:mundanej:map:workspace" version="1">
  <view map-crs="EPSG:4326" display-crs="EPSG:3857"
        center-x="0.0" center-y="0.0" units-per-pixel="1000.0"/>
  <layers>
    <feature-layer id="roads" name="Roads">
      <source opener="application.shapefile.v1" id="roads-source"
              name="Road data" path="data/roads.shp"/>
      <symbols catalog="application.default" marker="point" line="road" fill="area"/>
    </feature-layer>
    <raster-layer id="image" name="Image" interpolation="BILINEAR" opacity="1.0">
      <source opener="application.image-world-file-epsg3857.v1" id="image-source"
              name="Image data" path="data/image.png"/>
    </raster-layer>
  </layers>
</workspace>
```

`workspace` contains exactly one `view` followed by one `layers`. `layers` contains only ordered
`feature-layer` or `raster-layer` elements. A feature layer contains exactly `source` then `symbols`;
a raster layer contains exactly one empty `source`. Attributes are exactly those shown for their
element and may arrive in any order. Duplicate singleton elements, duplicate attributes, unknown
elements, unknown attributes, non-whitespace character content, CDATA, processing instructions,
DTD/entity events, and a second root are terminal. Bounded comments are ignored and never emitted.

The reader accepts an optional XML 1.0 declaration whose encoding is absent or case-insensitive
`UTF-8`; it rejects XML 1.1 and every other declared encoding. It accepts an optional leading UTF-8
BOM but the canonical writer emits none. Standard predefined and numeric XML character references are
allowed; DTD-defined entities are impossible. Decoded values must be valid XML 1.0 Unicode scalar
sequences with no isolated surrogate. No Unicode normalization occurs.

View coordinates, scale, and opacity use this exact ASCII grammar, where `digit` is `[0-9]`:

```text
decimal := [+-]? (digit+ ('.' digit*)? | '.' digit+) ([eE][+-]? digit+)?
```

Thus integers, `1.`, `.5`, either sign, and lower/upper exponent markers with an optional exponent
sign are accepted, while a bare sign or decimal point is not. The complete bounded token then goes to
`Double.parseDouble`; hexadecimal forms, surrounding whitespace, NaN, infinity, overflow to infinity,
and a non-positive scale fail. Signed zero is canonicalized to positive zero. Opacity must be within
`[0,1]`. Every finite `Double.toString` result matches this grammar. The writer uses
`Double.toString` on canonical values. It emits the declaration shown,
two-space indentation, fixed element/attribute order, XML escapes `&`, `<`, `>`, and `"`, numeric
escapes for tab/CR/LF in attributes, `/>` for empty elements,
and LF after every line including the last. Identical documents therefore produce identical bytes
independent of parser provider, locale, platform line separator, input formatting, or map iteration.

Version 1 defines no ignored extension element or field. A namespace/version other than the exact
pair is `WORKSPACE_VERSION_UNSUPPORTED`; an unknown field in the supported version is
`WORKSPACE_FIELD_UNKNOWN`. The only supported version set is `{1}`, so there is no older-version
migration in this release. A future version must explicitly decide whether to read v1 and construct a
v2 value; it cannot activate reflection, deserialize a class name, or register a generic migration
chain. The writer always emits version 1 until such a task changes the public profile.

### Local paths and explicit source openers

`WorkspaceRelativePath` stores a portable `/`-separated XML-1.0-valid string of one or more nonempty
segments. Every segment differs from `.` and `..`; the complete value contains no leading/trailing
slash, backslash, colon, empty segment, URI scheme, drive prefix, or UNC form and is at most 4,096
UTF-16 characters. It is never expanded from `~`, environment variables, system properties, or a
current working directory.

Reading requires the workspace path itself to be an existing readable regular file that is not a
symbolic link, snapshots its basic attributes and bytes, and derives the real parent directory. Before
opening a source, the path guard resolves its validated segments below that base, requires a regular
non-symbolic-link target, follows the target to its real path, and verifies it still starts with the
real base. Missing input uses a stable missing-resource problem;
lexical escape, symlink, special file, or real-path escape is a path problem. Checks defend an
untrusted document against traversal at resolution time; they do not claim to sandbox trusted opener
code or defeat a privileged concurrent filesystem attacker. Existing G5/G6 file snapshots remain the
mutation authority after handoff.

The source registry is instance-owned and immutable. A builder registers an exact opener ID, its
`FEATURE|RASTER` kind, one correspondingly typed `WorkspaceFeatureSourceOpener` or
`WorkspaceRasterSourceOpener`, and one immutable `WorkspaceLocalPathProfile`; duplicate IDs fail
during construction. The path profile contains one through eight ASCII-case-insensitive primary
suffix branches. Each branch has one dot-prefixed ASCII primary suffix and zero through sixteen exact
dot-prefixed ASCII replacement suffixes, all at most sixteen characters; entries are unique and the
profile has no callback. For the matching primary branch, the workspace derives each candidate by
replacing the final primary suffix on the already validated lexical path. It preflights the required
primary and every existing candidate as a regular non-symlink whose real path remains below the base;
missing replacement candidates are allowed because the format facade owns missing/ambiguity policy.
No XML value can add a candidate.

After preflight, the opener receives only the persisted `SourceIdentity`, guarded primary `Path`, and
`CancellationToken`, then returns one open `FeatureSource` or `RasterSource`. The example's shapefile
profile declares `.shp` with `.shx`, `.SHX`, `.dbf`, `.DBF`, `.cpg`, `.CPG`, `.prj`, and `.PRJ`; its
image profile has `.png`, `.jpg`, and `.jpeg` branches with exactly G6's corresponding long, short,
lower/upper, and `.wld` replacement suffixes. The profile preflight and facade probe are separate
finite checks. A replacement between them is governed by the already approved G5/G6 local-file race
boundary; the workspace does not claim a filesystem lock. Opening never uses `ServiceLoader`,
reflection, a class name from XML, classpath/resource scanning, URL handling, or automatic format
detection. An unregistered opener fails before any source file is opened. Returned metadata identity
must equal the requested value exactly; mismatch closes the source and fails the transaction.

The workspace module supplies no concrete opener and has no format dependency. The runnable example
explicitly registers `application.shapefile.v1` as application glue around `Shapefiles` with fixed
limits/encoding/CRS policy, plus `application.image-world-file-epsg3857.v1` around `RasterImages` with
fixed world-file CRS, image/cache limits, and an explicit decoder registry. A different approved policy
gets a different versioned opener ID; the XML cannot tune it, raise limits, select a decoder, or embed
credentials. No default registry, network opener, future-format stub, or adapter is present.

`WorkspaceSymbolCatalogRegistry` is likewise an immutable exact map from bounded application catalog
ID to `NamedSymbolCatalog`. It copies registrations and rejects duplicates. Feature-layer preflight
resolves all three names and validates marker/line/fill roles recursively through the ordinary symbol
contract before source I/O. Catalog definitions, symbol values, raster-icon resources, and renderer
registrations stay application-owned and are never copied into XML. The exact `CrsRegistry` supplied
in the open context resolves both view CRS keys; source metadata is validated through the ordinary G4
attachment boundary, with no workspace alias guess or CRS database access.

`WorkspaceViewState` admits only exact `EPSG:4326` and `EPSG:3857` keys, so aliases cannot enter a
programmatic document or canonical output. Opening resolves each key and requires the entire returned
definition to equal `CrsRegistry.level1().resolve(key)`, including canonical identifier, kind, axes,
units, and coordinate domain. A same-ID fabricated definition fails as `WORKSPACE_VALUE_INVALID` with
`field=mapCrs|displayCrs` and `reason=definitionMismatch`. A registry without either required
definition uses `WORKSPACE_CRS_UNREGISTERED`. Every Level 1 alias is rejected at workspace-value/read
construction rather than accepted and rewritten.

### Secure read, limits, and stable failures

Reading snapshots one bounded file before XML parsing: normalized absolute `NOFOLLOW_LINKS` checks,
captured size, exact byte read plus one-byte ceiling probe, close, and basic-attribute comparison. It
strictly decodes shortest-form UTF-8 and rejects malformed/overlong input, UTF-16/32 BOMs, isolated
surrogates, and XML-disallowed characters. No path/channel survives the read; only the real base in
`WorkspaceFile` remains for later guarded resolution.

Each read uses `XMLInputFactory.newDefaultFactory()`, sets and reads back namespace awareness,
non-coalescing, non-validating, DTD disabled, external entities disabled, entity replacement disabled,
`XMLConstants.ACCESS_EXTERNAL_DTD=""`, and `XMLConstants.USE_CATALOG=false`, plus a resolver that
always throws and a reporter that returns one project-owned failure. An unsupported/ineffective
property is an internal `IllegalStateException`; there is no weaker fallback. There is no DOM, XSD,
catalog, XInclude, URI resolver, internal JDK parser, static/thread-local parser, or parser discovery
chosen by file content.

`WorkspaceLimits` has positive finite ceilings, complete withers, no zero/unlimited sentinel, checked
prospective accounting, and these default/hard-maximum pairs:

| Limit | Default | Hard maximum |
| --- | ---: | ---: |
| Input or canonical output bytes | 4,194,304 | 16,777,216 |
| Peak workspace-owned operation bytes | 16,777,216 | 67,108,864 |
| XML element depth | 8 | 16 |
| Elements | 8,192 | 32,768 |
| Attributes | 32,768 | 131,072 |
| Layers | 1,024 | 4,096 |
| UTF-16 characters per attribute/comment | 4,096 | 16,384 |
| Aggregate decoded attribute/comment characters | 1,048,576 | 4,194,304 |

Structural limits are checked before retaining the crossing value. Fixed semantic limits for IDs,
opener IDs, paths, and catalog/symbol names apply in addition. Input/output bytes and aggregate
characters use checked `long`; other counters use checked `int`. Equality succeeds and plus one fails
in tests. A caller may lower or explicitly raise a configurable ceiling only through its listed hard
maximum. Construction requires `layers <= elements`, per-value characters no greater than aggregate
characters, and input/output bytes no greater than operation bytes; checked arithmetic rejects an
inconsistent pair before use.

Operation-byte accounting charges the input snapshot at one byte per byte, the complete decoded input
at two bytes per UTF-16 code unit, the canonical output sink at one byte per emitted byte, and the
retained model inventory below. Charges are prospective and released only when that operation drops
ownership.

- Charge 64 bytes once for the document, view state, each layer variant, each source reference, each
  symbol-reference group, and each relative-path wrapper.
- Charge another 64 bytes for the `SourceIdentity` retained by every source reference, even when two
  references share the same immutable instance.
- Charge eight bytes for every slot in the document's defensively copied layer list; the list wrapper
  is included in the document's 64-byte charge.
- Charge two bytes per UTF-16 code unit for every retained string field occurrence: both CRS keys;
  every layer ID/name; opener ID; source identity ID/name; relative-path text; and catalog, marker,
  line, and fill names. Equal strings and shared Java `String` identities are charged separately for
  each field occurrence. Enums and primitive fields add no separate charge beyond their owner value.

The same inventory defines the fixed model ceiling and the model portion of a read operation, so
defensive copying or aliasing cannot change the result. The canonical writer emits directly to one
bounded byte sink rather than first building a `String`. This is deterministic logical allocation
accounting, not a claim about JVM object headers or StAX provider internals. The non-configurable
4,096-layer model ceiling and all fixed string ceilings apply even when no operation limits object is
present.

`WorkspaceException` carries one immutable `WorkspaceProblem(code, context)` plus
`Optional<DiagnosticReport> sourceReport()`. The report is present only for a mapped
`SourceException`. Context is an ordered map whose exact per-code keys are below; `?` marks the one
conditionally present trailing key.

| Code | Exact ordered context and closed values |
| --- | --- |
| `WORKSPACE_IO_FAILED` | `phase=input`, `reason=missing|symlink|wrongKind|open|size|read|changed|close` |
| `WORKSPACE_ENCODING_INVALID` | `reason=bom|malformed|xmlCharacter` |
| `WORKSPACE_XML_INVALID` | `reason=security|declaration|wellFormed|content` |
| `WORKSPACE_VERSION_UNSUPPORTED` | `reason=namespace|version` |
| `WORKSPACE_FIELD_UNKNOWN` | `field=element|attribute|namespace`, `layerIndex?` |
| `WORKSPACE_STRUCTURE_INVALID` | `reason=order|missing|duplicate|cardinality|text`, `layerIndex?` |
| `WORKSPACE_VALUE_INVALID` | `field`, `reason=grammar|duplicate|nonCanonical|definitionMismatch|range|xmlCharacter`, `layerIndex?` |
| `WORKSPACE_LIMIT_EXCEEDED` | `limit`, `requested`, `maximum` |
| `WORKSPACE_PATH_INVALID` | `reason=grammar|suffix|escape|symlink|wrongKind|identity`, `layerIndex` |
| `WORKSPACE_RESOURCE_MISSING` | `kind=primary`, `layerIndex` |
| `WORKSPACE_SOURCE_OPENER_UNREGISTERED` | `kind=FEATURE|RASTER`, `layerIndex` |
| `WORKSPACE_SOURCE_KIND_MISMATCH` | `expected=FEATURE|RASTER`, `actual=FEATURE|RASTER`, `layerIndex` |
| `WORKSPACE_SOURCE_IDENTITY_MISMATCH` | `layerIndex` |
| `WORKSPACE_SYMBOL_CATALOG_UNREGISTERED` | `layerIndex` |
| `WORKSPACE_SYMBOL_NOT_FOUND` | `role=marker|line|fill`, `layerIndex` |
| `WORKSPACE_SYMBOL_ROLE_MISMATCH` | `role=marker|line|fill`, `layerIndex` |
| `WORKSPACE_CRS_UNREGISTERED` | `field=mapCrs|displayCrs` |
| `WORKSPACE_SOURCE_OPEN_FAILED` | `kind=FEATURE|RASTER`, `layerIndex` |
| `WORKSPACE_CANCELLED` | `phase=preflight|path|sourceOpen|publish`, `layerIndex?` |
| `WORKSPACE_ATOMIC_MOVE_UNSUPPORTED` | empty |
| `WORKSPACE_WRITE_FAILED` | `phase=validate|encode|temporary|write|force|move|cleanup`, `reason=io|target|changed` |

`field` outside the enumerated special cases is one of the exact view tokens `mapCrs`, `displayCrs`,
`centerX`, `centerY`, and `unitsPerPixel`; layer tokens `layerId` and `layerName`; source tokens
`sourceOpener`, `sourceId`, `sourceName`, and `sourcePath`; symbol tokens `catalogId`, `markerName`,
`lineName`, and `fillName`; or `interpolation`, `opacity`, and `pathProfile`. `limit` is one of
`inputBytes|outputBytes|operationBytes|depth|elements|attributes|layers|valueChars|aggregateChars`;
`requested` is the exact prospective non-negative count that would cross `maximum`, not a retained
count or vague lower bound. Fixed hard maxima keep these additions representable. Context token values
are ASCII and at most 32 characters; numeric values are canonical non-negative decimal `long`s.
Contexts never contain XML text, names, symbol/catalog/opener IDs, relative/absolute paths, provider
messages, credentials, localized exception text, or source bytes. Reader precedence is argument/limit
configuration, file snapshot/I/O, encoding, XML security/well-formedness, grammar/version, then value
validation. Opener precedence is document/registry/CRS/catalog preflight in layer order, guarded path
resolution for every layer, then source opening in layer order.

When a typed opener throws `SourceException`, the workspace retains that cause and its immutable
`DiagnosticReport` without copying its message into context. A report whose terminal code is exactly
`SOURCE_CANCELLED` maps to `WORKSPACE_CANCELLED` with `phase=sourceOpen`; every other source report maps
to `WORKSPACE_SOURCE_OPEN_FAILED`. A token becoming cancelled after a successful return is observed by
the required post-open check and also maps to workspace cancellation after closing that source. A
non-cancellation source failure already encountered is never overwritten merely because the token is
later cancelled. Any other unchecked opener failure is a programmer error: transactional cleanup
still runs, then the original failure escapes with cleanup failures suppressed.

The optional `layerIndex` is present exactly when grammar processing has entered a layer or opening
has selected one; document-level failures omit it. For `WORKSPACE_CANCELLED`, `phase=preflight`
includes the index only at checks immediately before/after resolving that layer's opener, catalog, and
symbols, and omits it at document/CRS checks. `phase=path` and `phase=sourceOpen` always include the
selected layer index; `phase=publish` always omits it. Direct construction rejects invalid public
arguments with the project's ordinary named argument exceptions; the stable workspace table governs
reader, opener, and writer failures on otherwise structurally callable operations.

Opening is all-or-nothing. It validates every registry/opener/catalog/symbol/CRS reference, registered
path profile, persisted primary path, and derived sidecar candidate before opening the first source.
It then opens sources in layer order, checking cancellation before and after each controlled path,
registry, and open phase. A failure or cancellation closes the current returned source if any and
every earlier source in reverse order before throwing; the original problem remains primary and
cleanup failures are suppressed. No `WorkspaceSession` or partial layer list escapes. Successful
source warning reports remain attached to their source and are available from the published session.

### Canonical atomic write

Writing validates the complete document and materializes its canonical bytes under the same output
ceiling before touching the destination. The target parent must already exist; a target symbolic link
or non-regular existing target is rejected. The writer creates one private temporary regular file in
the same directory with `CREATE_NEW`, writes all bytes, calls `FileChannel.force(true)`, closes it, and
moves it with exactly `ATOMIC_MOVE` plus `REPLACE_EXISTING`. `AtomicMoveNotSupportedException` becomes
`WORKSPACE_ATOMIC_MOVE_UNSUPPORTED`; there is no silent non-atomic fallback. A pre-move failure leaves
an existing target unchanged. Temporary cleanup is attempted once, with the original failure primary
and cleanup failure suppressed. Success means the provider completed one atomic name replacement; it
does not overclaim portable directory-fsync or power-loss durability.

The writer does not resolve or open source references, test catalog registrations, create directories,
change opener policy, preserve comments/input formatting, or embed a checksum. Canonical byte
identity and ordinary caller-selected file backup/versioning are sufficient for the first profile.

### Verification, decomposition, and checkpoint

API/module tests cover every immutable value, defensive copy, unique ID, opener/path/profile grammar,
XML-scalar and symbol-name validation, every numeric grammar branch and finite boundary,
canonicalization, fixed/configurable/hard limit edge,
model/operation allocation accounting, and every exact problem shape. Reader tests cover the
canonical document, every Level 1 CRS key and alias, alternate attribute order, BOM/declaration cases,
strict UTF-8/XML 1.0,
all unknown/duplicate/missing grammar branches, comments/whitespace, DTD/entities/external-access
negative controls, every count/depth/string/byte boundary, and no raw-data leakage. Writer tests cover
byte-for-byte output, escaping, locale/line-ending independence, write-read-write identity, replacement,
unsupported atomic move, injected write/force/move/cleanup failures, and preservation of the old file.

Registry/session tests use explicit fake openers to prove preflight before I/O, returned-identity and
kind validation, same-ID rejection, known/missing resources, lexical/real/symlink escapes, every
closed suffix candidate and the documented race boundary, mismatch cleanup, catalog lookup/role
checks, canonical/full-definition CRS resolution, fabricated same-ID rejection, every cancellation
phase/index shape and direct/source-reported cancellation mapping, all-or-nothing
reverse close, warning retention, and repeated close. A runnable workspace viewer registers
application glue that opens a small local shapefile plus world-file PNG, applies persisted view/raster
state and fixed catalog symbols, and closes the view before the session. No test uses network access,
ambient credentials, classpath discovery, or Java serialization.

Implementation is deferred into five later vertical slices; this decision creates no module or task
file:

1. `G11-030` creates the AWT-free module with immutable values and the secure XML v1 reader.
2. `G11-031` adds canonical serialization and atomic replacement with round-trip/failure evidence.
3. `G11-032` adds explicit registries and all-or-nothing session opening with fake openers.
4. `G11-033` adds the runnable workspace viewer and full local restore/ownership slice.
5. `G11-034` adds hostile-input hardening, Javadocs, publication/consumer checks, and a representative
   Linux Native Image read/write/open smoke before making a workspace-native claim.

The dependency graph is the deliberately sequential
`G11-003 -> G11-030 -> G11-031 -> G11-032 -> G11-033 -> G11-034`. These slices share one small module
and public contract surface, so apparent parser/writer/opener parallelism is not path-safe. No earlier
slice adds an empty module, AWT dependency, generic migration engine, or future-format stub.

The named HITL checkpoint is **G11 workspace persistence profile approval**. A maintainer approves the
portable field/exclusion list, strict XML grammar/version policy, local-path threat model, two example
opener policies, external symbol/catalog rule, limit/failure/atomic-write behavior, viewer scenario,
and five-slice decomposition before G11-030 is created. This is the smallest workspace that can reopen
a useful local map while keeping runtime ownership, data, secrets, presentation extensions, and future
formats outside the file.

## Optional-adapter dispositions (G11-004)

### Evidence gate, not an adapter framework

G11-004 records one disposition for each named external integration after its concrete G10/G11 use
case has been designed. It creates no module, dependency, interface, registry, or implementation task.
The three possible dispositions are:

- `ACCEPT`: one demonstrated capability has an exact module, dependency, license, conversion,
  lifecycle, diagnostic, platform, publication, verification, and Native Image policy, plus working
  vertical-slice cards;
- `DEFER`: the current project has no demonstrated gap worth the dependency/deployment cost, so the
  missing evidence and reopen condition are recorded but no name, module, dependency, or card is
  reserved; or
- `REJECT`: the integration conflicts with a non-waivable project boundary and may be reconsidered
  only by explicitly changing that boundary.

An accepted adapter remains a concrete consumer of existing MundaneJ contracts. This decision does
not add a generic `Adapter`, external-geometry facade, database abstraction, native-loader SPI,
provider registry, classpath discovery mechanism, or exception bridge. `mundane-map-api` remains
unchanged. G10-003, G10-004, G10-007, and G11-001 are the exact evidence dependencies for GDAL,
SQLite, PROJ, and JTS respectively; the general G0 adapter policy is already transitively closed and
does not justify another task dependency.

The approved dispositions are:

| Candidate | Decision | Current evidence |
| --- | --- | --- |
| SQLite through Xerial JDBC | `ACCEPT` | G10-004 has two bounded read-only container profiles and complete working-card roots. |
| JTS | `DEFER` | G11-001 needs only point replacement and same-CRS vertex/segment snapping already designed in core. |
| PROJ | `DEFER` | G10-007 selected no third CRS and explicitly recorded `DEFER`. |
| GDAL | `DEFER` | G10-003 selected a bounded JDK-only GeoTIFF reader and identified no missing runtime capability. |

No candidate is `REJECT`: a later evidenced need can reopen a deferred choice without implying that
the current design promised compatibility.

### Accepted Xerial format adapters

Acceptance is limited to the already-profiled, separately published Level 2 modules
`mundane-map-io-geopackage-xerial` and `mundane-map-io-mbtiles-xerial`. Their `mundane-map-io-*` names
are required because they implement concrete data formats; the `-xerial` suffix makes the external
implementation/deployment boundary visible. There is no generic SQLite artifact, shared public
connection, JDBC facade, or third adapter. The G10-004 format contracts remain authoritative for SQL,
schemas, geometry/tile decoding, limits, caching, cancellation, immutable-input checks, and source
ownership rather than being duplicated here.

The accepted coordinate is exactly `org.xerial:sqlite-jdbc:3.53.2.0`. G10-004 records the independently
verified POM, code-classifier, Linux-classifier, and rejected all-platform JAR hashes. Each adapter
declares the `without-natives` classifier for compilation and runtime and the `natives-linux`
classifier at runtime only, both non-transitively. Resolution must contain those exact two JARs and
POM metadata—no ordinary/default JAR, `natives-all`, optional SLF4J binding, version range, dynamic
selector, unclassified duplicate, or extra transitive component. A version, classifier, checksum, or
constructor change first amends the G10 profile; an implementation card may not drift silently while
claiming this approval.

The code classifier's `org.sqlite.jdbc4.JDBC4Connection` is constructed directly inside each module's
private connection policy. Project code does not call `DriverManager`, `ServiceLoader`, `Class.forName`,
`SQLiteDataSource`, reflection, resource lookup, `System.load*`, `Runtime.load*`, or another native
loader. No project class declares a native method. The upstream classifier's own service descriptor,
JNI declarations, resource/URL extraction, `System.load*`, process-global loader, LoggerFactory
`Class.forName("org.slf4j.Logger")`, `/proc/self/map_files` and `/etc/os-release` musl probes, and
the supported-path `uname -o` plus fixed `/system/lib/libGLESv1_CM.so` and
`/system/lib64/libGLESv1_CM.so` Android probes are an inventoried external-artifact exception under G0. The
exact graph contains no SLF4J, making the caught reflective miss and JDK logger fallback deterministic
in supported evidence. External-artifact tests scan those descriptors, paths, strings, symbolic calls,
and native entries; they are not copied, shaded, repackaged, registered, or represented as MundaneJ
code. Thus the approved external native classifier is allowed while project-owned or repacked native
binaries remain excluded.

Every public or protected class, constructor, field, method, record component, generic bound, throws
clause, and annotation surface in the two modules contains only JDK or MundaneJ types. Private
implementation fields and methods may use the qualified JDBC/Xerial connections, statements, results,
exceptions, SQL text, native paths, and loader state. Public openers return only `FeatureSource` or
`RasterSource` and use
the explicit static format facades approved by G10-004; no registration or plugin discovery is added.
Format sources own connections/statements/cursors under the existing all-or-nothing and reverse-close
contracts. Raw SQL, database identifiers, filesystem/native paths, provider messages, and exception
messages never enter diagnostics.

Both project modules are classified in the authoritative inventory as published Level 2 **Optional
adapters**, never Level 1, with Native Image policy `not-targeted`. Before connection initialization,
one private policy requires exact Linux plus `amd64|x86_64` system properties and a false result from
Xerial `OSInfo.isMusl()`; every other result maps to `unsupportedPlatform`. That approved external
probe accounts for the host-file access above. The support floor is Java 21 on x86-64 Linux with glibc
2.35: pinned Ubuntu 22.04/glibc 2.35 and Ubuntu 24.04/glibc 2.39 are the exact positive lanes, while
glibc below 2.35 is unverified even though artifact inspection finds no native symbol newer than
`GLIBC_2.3`. The classifier's unused architectures/libc variants do not create support. Windows,
macOS, musl, other architectures, Android, system SQLite, caller-supplied binaries, and Native Image
all require a new decision. Neither adapter enters the shared native executable, and absence of a
Native Image test is explicit rather than a compatibility claim.

The implementation cards record Xerial's Apache-2.0 license, the retained Zentus BSD-2-Clause notice,
SQLite's public-domain statement, bundled notices, and the exact native inventory. MundaneJ artifacts
retain the project license and never shade or redistribute Xerial bytes. Each module joins settings,
the project inventory, normal checking, publication staging, release-contract checks, the exact
build-only classifier mirror, and the offline consumer only with its first working behavior. The
published POM/module metadata must carry the approved classified dependency scopes so a clean consumer
does not need an ambient driver or global Gradle cache.

G10-004's stable `SQLITE_ADAPTER_UNAVAILABLE` outcomes remain the only deployment boundary:
`reason=unsupportedPlatform|nativeLoad|temporaryDirectory`. Other connection/query/format failures
retain that design's closed codes and safe contexts. Tests must force each deployment outcome without
copying native messages. Every deployment negative runs in its own fresh forked JVM because Xerial's
loader is process-global: non-Linux/non-x86 forks override only their system properties; a pinned
Alpine x86-64 Java 21 process supplies the musl case; a native-load fork deliberately receives the
code classifier without the Linux classifier; and a temporary-directory fork points
`org.sqlite.tmpdir` at a controlled non-directory/unwritable fixture. The subsequent successful open
runs in a separate fresh JVM with the exact graph and writable private temporary directory. Tests
never depend on fork order and add no production loader seam. No missing-class or version failure is
invented as an application protocol: dependency resolution, classifier identity, signatures, and
checksums fail normal build/consumer verification; omitting the native classifier is only the isolated
negative-control fork for `nativeLoad`.

### Deferred JTS, PROJ, and GDAL candidates

`DEFER` deliberately leaves no placeholder API or module:

| Candidate | Why no adapter exists now | Evidence required to reopen |
| --- | --- | --- |
| JTS | Point create/move/delete and same-CRS vertex/segment snapping have bounded project-owned algorithms; line/polygon editing, topology repair, overlay, and validity are excluded. | Name one working feature that needs topology/overlay/repair; define geometry/empty/Z/M/precision/ring conversion semantics; compare JDK-only complexity and measured cost; then decide exact dependency, license, public conversion ownership, and native policy. |
| PROJ | No third CRS pair, datum/grid operation, accuracy target, or fixture is selected, and G4 already permits explicit reviewed direct operations. | Complete G10-007's workflow/CRS/domain/datum/epoch/accuracy/format/platform/conformance packet and select `PROJ_REQUIRED`; then qualify the database/grid resources, native packaging, fixed-epoch 2D mapping, envelope behavior, diagnostics, and deployment. |
| GDAL | The approved GeoTIFF profile has a bounded JDK-only parser, and G9's use of GDAL for offline fixture independence is not a runtime need. | Demonstrate a required format/codec/warp capability the JDK-only profiles cannot meet; record interoperability and performance evidence, exact GDAL build/dependency/plugin/data-file inventory, license notices, platform/native policy, and non-leaking raster/elevation conversion. |

A future proposal evaluates only the evidenced capability; it cannot bundle JTS, PROJ, GDAL, SQLite,
or unrelated formats into one catch-all native adapter. Benchmark evidence is mandatory before citing
performance, and a custom native library remains a separate decision even if one of these candidates
is later accepted.

### Verification, task graph, and checkpoint

G11-004 itself changes only design/task documentation. Later SQLite evidence must include exact
dependency resolution and SHA-256 checks; release-signature/advisory review; POM/module-metadata and
license/notice assertions; public/protected-signature scans for external types; project-bytecode/
resource scans for prohibited discovery/native calls; the exact external reflection, host-file,
process, descriptor, resource, JNI, and native inventory; pinned Ubuntu/glibc JVM success in isolated
processes; all three `SQLITE_ADAPTER_UNAVAILABLE` reasons in fresh negative forks;
connection/cursor/source cleanup; format
parity and hostile-input tests; publication staging; and a fresh offline consumer. Native Image is not
run or claimed for these adapters unless a later task changes `not-targeted` with real evidence.

No new G11 implementation card is created. The accepted work uses G10's graph:

```text
G6-004 + G10-006 -> G10-039
G10-004 + G11-004 -> G10-040 -> G10-041
G10-041 + G10-039 -> G10-042
G10-039 + G11-004 -> G10-043
G10-042 + G10-043 -> G10-044
```

G10-039 may land before G10-004 or G11-004; those decisions do not own or block the dependency-neutral
G6 helper. After G10-039 and this decision, the GeoPackage and MBTiles working roots consume it and
are logically independent. They are not path-safe in parallel when they both touch settings, the
project inventory, root Gradle, publication/consumer fixtures, the task index, or roadmap; one
integrator serializes those shared changes. Deferred candidates add no tasks and cannot be reported
as blocked work.

The named HITL checkpoint is **G11 optional-adapter disposition approval**. A maintainer approves the
one `ACCEPT` and three `DEFER` outcomes, exact Xerial coordinate/classifiers/checksums, two-module and
public-type boundary, external-JNI exception, licenses/notices, Java 21 Linux x86-64/glibc 2.35+
support, Native Image `not-targeted` policy, deployment diagnostics, reopen evidence, and reused G10
graph before G10-040 or G10-043 is created. This is the smallest adapter policy consistent with
demonstrated needs: two useful format adapters, no generic integration framework, and no speculative
geometry/projection/GDAL cost.

## Canonical vector map export profile (G11-005)

### One static SVG target, not an export framework

G11-005 selects canonical static SVG 1.1 for viewport map export. SVG is the one justified target
because G2 already represents vector paths and symbols, G10-001 already owns a secure published SVG
artifact, and ordinary browsers provide the manual interoperability surface. The design extends
`mundane-map-io-svg`; it creates no export module, generic document tree, renderer backend, format
SPI, PDF path, print service, or automatic exporter registry.

The export is a bounded picture of one captured logical-screen viewport, not a map-data interchange
format. One SVG user unit is one logical screen pixel. It contains the configured page background,
accepted vector feature portrayal, and already placed G11 point labels. It deliberately contains no
CRS/georeferencing, source/layer/feature identity, attributes, editing state, query, cache, timestamp,
producer comment, arbitrary metadata, interaction overlay, tool overlay, UI chrome, or live object.
The resulting SVG cannot be reopened as a map and is not round-trippable through G10-001's marker
importer.

The public operation is explicitly split at the toolkit boundary:

```text
mundane-map-awt
  MapView.captureVectorExportSnapshot(
      VectorExportSnapshotLimits limits, CancellationToken cancellation)
    -> VectorExportSnapshot

mundane-map-io-svg
  SvgMapExports.encode(
      VectorExportSnapshot snapshot, SvgExportLimits limits,
      CancellationToken cancellation)
    -> byte[]

  SvgMapExports.writeAtomically(
      Path target, VectorExportSnapshot snapshot, SvgExportLimits limits,
      CancellationToken cancellation)
```

`VectorExportSnapshot`, `VectorExportSnapshotLimits`, `VectorExportSnapshotException`,
`VectorExportSnapshotProblem`, and the snapshot's nested values live in `mundane-map-api`, the only
module both producers and consumers may expose without reversing a dependency. The one new
`MapView` method is the only public capture entry point; another AWT utility/provider hierarchy is
unnecessary. The SVG module stays AWT-free and gains the one exact G0-approved dependency on
`mundane-map-core` needed for `SymbolTransforms`, `LineTangents`, and `HatchLayouts`. It continues to
depend on API and `java.xml`. Neither API nor AWT depends on the SVG module, and core does not know
about snapshots or SVG. This boundary is the demonstrated exception to avoiding a snapshot
abstraction: an immutable value is required to cross from the live AWT/source stack to an AWT-free
writer.

The G10 importer retains `SvgSymbols` and `SvgImportLimits`. Export adds only `SvgMapExports`,
`SvgExportLimits`, `SvgExportException`, and `SvgExportProblem` to that artifact. There is no shared
SVG DOM or parse/write model. Import and export share only small private ASCII number/color/XML
helpers where their exact grammar agrees; neither is implemented by running the other direction.

`VectorExportSnapshotProblem` and `SvgExportProblem` each contain one non-blank stable ASCII code and
an insertion-ordered, defensively copied immutable `Map<String,String>` context. Their matching
unchecked exceptions expose `problem()` and retain an optional Java cause; messages are human-readable
but not contracts. Snapshot exceptions have no source report because existing source/CRS/label
exceptions escape unchanged. `SvgMapExports` supplies convenience overloads using
`SvgExportLimits.defaults()` and `CancellationToken.none()`; `VectorExportSnapshot.of` and the
`MapView` method likewise have defaults/no-cancellation conveniences. No overload accepts a stream,
writer, URI, URL, provider, renderer, callback, or mutable builder.

### Detached snapshot values and invariants

The API shape is one final top-level value with final nested records:

```text
VectorExportSnapshot.of(
  int widthPixels,
  int heightPixels,
  Rgba background,
  ViewFrame viewFrame,
  int layerCount,
  List<Primitive> primitives,
  List<Label> labels,
  VectorExportSnapshotLimits limits)

ViewFrame(
  double screenPixelsPerMapUnit,
  double mapXAxisScreenBearingDegrees,
  Coordinate mapOriginScreen)

Primitive(
  int layerIndex,
  int featureIndex,
  Geometry screenGeometry,
  Symbol symbol)

Label(
  String text,
  LabelTextStyle style,
  double baselineX,
  double baselineY,
  double measuredAdvance,
  int ordinaryPaintOrdinal)
```

The snapshot defensively copies both lists and validates their immutable nested records. Existing
geometry, symbol, color, style, and coordinate values are already immutable and may be shared. It
retains no `MapView`, binding, source, cursor, registry, portrayal selector, attribute map, source diagnostic,
`TextLayout`, AWT value, path, or callback. `Label` intentionally strips the layer/feature IDs and
visual/collision boxes from `PlacedPointLabel`; the writer needs only paint order, text/style,
baseline, and measured advance. Numeric layer/feature ordinals remain solely for deterministic order
and bounded failure context.

`widthPixels` and `heightPixels` are positive logical component dimensions. `ViewFrame` records the
validated G2 similarity basis in canonical form: positive finite screen pixels per projected map
unit, normalized clockwise screen bearing of map-positive x, and the finite screen position of
projected map origin `(0,0)`. These values preserve map-unit sizes/offsets, map-relative rotation,
and hatch phase without duplicating or exposing core's `MapScreenBasis` through API. The writer
reconstructs x delta `s*(cos(b),sin(b))` and y delta `s*(sin(b),-cos(b))` with `StrictMath`, then
validates the negative-determinant basis through `MapScreenBasis.of`. A snapshot stores positive zero
for zero bearing and every zero-valued coordinate.

`screenGeometry` uses the six G4 geometry families with every coordinate already transformed into
logical screen space. A primitive contains exactly one ordinary feature portrayal after role
selection, even when its geometry is multipart or its symbol is composite. Its symbol role must
match the geometry family. Primitives are strictly ordered by ascending `(layerIndex, featureIndex)`,
each pair is unique, every layer index is below `layerCount`, and a feature index is non-negative.
Empty layers are represented by `layerCount`, not placeholder primitives. Labels are strictly
ordered by unique ascending `ordinaryPaintOrdinal`. Empty primitive and label lists are allowed, so
a background-only export is useful and canonical.

The public factory enforces scalar/list/order/role invariants, the supplied snapshot limits, and the
hard snapshot caps below.
It also iteratively validates the closed supported symbol tree: only exact built-in vector values
listed in the next section are accepted. The check uses exact final classes and roles, never renderer
keys alone. This keeps a snapshot self-contained: it cannot require an AWT registry or application
callback later. Null, scalar, list, order, and role construction defects are field-naming
`NullPointerException` or `IllegalArgumentException`; an otherwise well-formed but unsupported symbol
tree uses `VECTOR_EXPORT_SYMBOL_UNSUPPORTED`, whether supplied programmatically or found during live
capture.

Snapshot accounting is a semantic inventory, not a JVM heap estimate. It reuses G4's primitive sizes
and fixes every wrapper/container charge so two conforming implementations calculate the same value:

| Retained snapshot occurrence | Logical byte charge |
| --- | ---: |
| `VectorExportSnapshot` wrapper, including dimensions/background and list references | 64 |
| `ViewFrame` | 64 |
| each `primitives` or `labels` list slot | 8 |
| each `Primitive` or geometry node | 64 |
| each `CoordinateSequence` occurrence | 32 |
| each geometry coordinate pair | 16 |
| each part/ring/polygon-ring fencepost | 4 |
| each singular-polygon hole-list slot | 8 |
| each exact built-in symbol node, including all of its fixed scalar/enum/color/placement/stroke fields | 64 |
| each composite child, endpoint, or fill-outline reference edge | 8 |
| each `VectorPath` wrapper | 32 |
| each vector-path opcode / ordinate | 1 / 8 |
| each `Label`, including its style and numeric fields | 64 plus 2 per retained text UTF-16 code unit |

Point geometry has one coordinate pair and no sequence charge. Singular line/polygon and all packed
multipart values charge every owned sequence occurrence, coordinate pair, and declared fence; a
singular polygon additionally charges its hole-list slots. Derived envelopes/caches and object headers
are excluded. A symbol node's 64-byte charge includes `Rgba`, `MarkerPlacement`, `SymbolSize`,
`SymbolStroke`, and `SymbolLength` state; only structural child edges and a vector marker's variable
path payload add charges. The supplied limits and problems are not retained and are not charged.

Every occurrence is charged without identity deduplication, so a shared geometry, symbol, path, or
text value used by two primitives is charged twice. The counter is cumulative, uses checked
prospective arithmetic, accepts equality, and records `requested=Long.MAX_VALUE` on overflow before
publication.

### Synchronous AWT capture

Capture must be invoked on the Swing event-dispatch thread. An off-EDT call is a programmer error;
the method does not call `invokeAndWait`, start a worker, or hide deadlock/reentrancy policy. It
captures positive current component dimensions, immutable content/binding, viewport, CRS registry,
symbol registry, portrayal resolver, supplied snapshot limits, and cancellation token once. It also
captures the current non-null, opaque component background and converts that `Color` immediately to
`Rgba`; a non-opaque component or color cannot produce a self-contained picture and uses
`VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID field=componentBackground reason=nonOpaque`. A programmatic
snapshot may still declare a transparent background.

Before opening any feature cursor, capture preflights every layer. Raster and elevation bindings are
terminally unsupported, as are a closed view or incompatible component dimensions. It does not reject
an unselected portrayal rule: only a symbol actually resolved for a captured feature enters the
picture/profile and has a meaningful `featureIndex`. Capture then visits feature layers in ordinary
paint order, opens exactly one viewport query per source layer using G11-002's exact
projected portrayal/label attributes, and closes each cursor before publishing the snapshot. Snapshot
and editable bindings use their immutable records directly. A source, CRS, or label-layout failure
retains its existing typed exception and problem/report; capture does not relabel it as SVG.

Each accepted record is transformed from authoritative geometry, not a G7 clipped/simplified screen
plan or render-cache entry. Portrayal resolves exactly once for the geometry role. An absent symbol
produces no primitive or label. A present symbol is recursively profile-checked even when effective
opacity is zero; unsupported content never becomes acceptable merely because it would paint nothing.
The same G2 placement algorithms derive marker nominal bounds used by G11 label collection. Capture
performs the one G11 global metric/placement pass through the fixed `LabelTextMetrics` profile used by
ordinary paint, then copies accepted labels in ascending ordinary paint order. No feature geometry or
label is published until every layer/query, transform, symbol check, label placement, limit, and final
cancellation check succeeds.

Cancellation is checked before preflight, before each layer/cursor open and advance, before each
geometry/symbol traversal, before and after label layout, and immediately before publication. Every
cursor closes exactly once; on failure, the original typed failure remains primary and close failures
are suppressed under the existing G4 rules. The method retains no last snapshot and adds no listener,
background work, cache, or capture-mode mutation to `MapView`.

### Exact supported and rejected paint profile

All six toolkit-neutral vector geometry families are supported: point, multipoint, line string,
multi-line string, polygon, and multi-polygon. The exact accepted symbol tree is:

| Role | Accepted exact value | Export behavior |
| --- | --- | --- |
| Marker | `VectorMarkerSymbol` | Transform its `VectorPath`, then emit fill and optional stroke. G10-imported symbols are ordinary values here. |
| Line | `SolidLineSymbol` | Emit each part's round-cap/join centerline, then supported start and end marker trees using G2 tangent rules. |
| Fill | `SolidFillSymbol` | Emit each polygon component with even-odd holes, then a recursively supported solid line outline without closed-ring endpoints. |
| Fill | `HatchFillSymbol` | Emit bounded G2 hatch segments under a deterministic polygon clip, then its recursively supported line outline; cross hatches retain forward-then-backward order. |
| Any one role | `CompositeSymbol` | Traverse non-empty role-homogeneous children in declaration order, multiplying inherited opacity. |

Endpoint marker trees may contain only vector markers and marker composites. A solid- or hatch-fill
outline may contain only solid lines and line composites. Composite traversal is iterative with the same
child-major/component-major rules approved in G2; nested boundaries remain relevant to opacity and
diagnostics even though no SVG group-opacity semantics are used. Effective alpha is the product of
all enclosing composite/symbol/color alpha and is flattened onto each leaf fill or stroke. This
matches G2's `SRC_OVER` leaf semantics; SVG group opacity, filters, masks, and offscreen compositing
are not used.

For limits and failures, `symbolOrdinal` resets to zero at each primitive and follows iterative
preorder: root; composite children in declaration order; line start then end endpoint trees; and the
optional outline tree of either fill kind. Root depth is one. This is also the accounting/preflight order, so a crossing node and
unsupported descendant are selected independently of collection/hash iteration or paint opacity.

The complete terminal rejection set is raster/elevation layers, `RasterIconSymbol`, deprecated
`FeatureStyle`, any consumer-defined symbol or renderer key/value, role mismatch, unsupported
descendant, and any future built-in not added by an explicit profile revision. Rejection is whole
operation: there is no image embedding, glyph outlining, raster fallback, partial layer omission,
renderer callback, warning-only degradation, or `opacity=0` escape. Editing previews, hover,
selection, measurement, cursor/tool state, and UI decorations are not layers and are never captured.

### Geometry, paint order, clipping, and hatches

The SVG writer validates the snapshot root/limits, then uses two deterministic streaming traversals
into one private bounded byte sink: the first writes the viewport and hatch clip definitions; the
second writes background, geometry, and labels. It retains no element plan, display list, path-token
list, ID string, or transformed-geometry copy. Both traversals assign monotonically increasing hatch
ordinals from the same structural order; tests assert that the paint pass consumes exactly the number
defined by the first pass. IDs are `v0` for the viewport and `c1`, `c2`, ... for hatch clips; their
ASCII digits are written directly from counters and derive only from traversal, never from
source/layer/feature/catalog identity. A later failure discards the private partial bytes. The writer
never uses user text as an XML name or URL. Internal `url(#...)` clip references are the sole URL
syntax in the document.

The background rectangle is first inside the viewport-clipped paint group. Ordinary feature
primitives follow snapshot order. Point/multipoint components retain coordinate order. Line parts
are part-major inside one line child: centerline, start marker if the part has a distinct tangent,
then end marker; a line composite is child-major across all parts. Polygon components are emitted as
separate SVG paths so overlapping components composite as G2 paints them rather than cancelling each
other under one even-odd path. Each component path contains its exterior followed by holes and uses
`fill-rule="evenodd"`. Fill interior/hatches paint before its outline; composite fill children remain
child-major.

Vector marker fill and stroke are separate leaf paths in that order. The fill path contains only
explicitly closed source subpaths, preserving their relative order; an open subpath is omitted from
fill rather than relying on SVG's implicit fill closure, and no fill element is emitted when none is
closed. The stroke path contains every source subpath. A line centerline uses `fill="none"`, fixed
`stroke-linecap="round"`, and `stroke-linejoin="round"`; an all-coincident part is omitted exactly as
in G2. The writer transforms marker path commands through the reconstructed G2 basis, preserves
move/line/quadratic/cubic/close topology, and validates every transformed coordinate. Endpoint transforms use
`LineTangents.outwardScreenBearings` and `SymbolTransforms.markerAtScreenBearing`; ordinary markers
use `SymbolTransforms.marker`. Symbol length conversion uses `SymbolTransforms.screenLength`.

Hatches reuse `HatchLayouts.cover` over the intersection of the polygon's screen bounds and page
rectangle. G11-041 adds the matching allocation-free preflight:

```text
HatchLayouts.candidateSegmentCount(
    HatchPattern pattern, Envelope bounds, Coordinate latticeOrigin,
    double orientationBaseBearing, double spacingPixels, String featureId) -> long
```

It returns the exact combined conservative candidate count for the same inputs, zero for an empty
intersection, and `Long.MAX_VALUE` for count arithmetic overflow; other invalid/non-finite inputs keep
G2's symbol failure. `cover` delegates to that same private count calculation before its existing
limit/allocation path. Hatch phase uses screen origin for screen-relative rotation and the snapshot's projected
map-origin screen coordinate for map-relative rotation. The writer emits one deterministic
`clipPath clipPathUnits="userSpaceOnUse"` per hatch-painted polygon component and one path containing
the packed hatch line segments. The clip path preserves that component's holes with even-odd fill.
Hatch segments are not geometrically intersected a second time; the SVG clip establishes the same
bounded visible result as AWT. After that component's hatch path, the hatch symbol's optional outline
paints exterior then holes with endpoint markers suppressed at every nested line/composite level;
the hatch symbol's opacity multiplies both strokes and the outline's own opacity exactly as in G2.
For multipolygons a hatch leaf is component-major (hatch then outline for each component), while a
fill composite remains child-major across every component. Clip and element counts are charged before
retaining IDs or commands.

The conservative count also defines the two-pass empty-result policy. A zero candidate count assigns
no ordinal, emits no clip, never calls `cover`, and emits no hatch path; the optional outline still
paints. A positive candidate count always assigns the next ordinal and emits its clip in the
definitions pass. The paint pass advances that same ordinal and calls `cover`. If every conservative
candidate is a corner-only intersection and the returned `HatchSegments.segmentCount()` is zero, it
emits no hatch path—never an empty `d`—but deliberately leaves the already emitted, unused clip in
`defs`. The clip elements and clip-geometry path commands count toward their respective writer
limits, while the absent hatch path contributes neither an element nor path commands. Its candidate
and wrapper byte charges are retained because the bounded core result was still requested. This
closed policy avoids a retained hatch plan or a third traversal while keeping clip IDs identical in
both passes.

All ordinary geometry is constrained by the root viewport clip. Coordinates outside the page are
legal if finite and within snapshot/accounting bounds; they are not silently quantized or clamped.
The SVG writer does not reuse G7's paint-only simplified geometry because capture already retained
authoritative screen geometry. The writer has no path cache, DOM, scene graph, or second geometry
optimizer.

### Labels and font policy

Only snapshot `Label` values derived from G11-002 `PlacedPointLabel` are emitted. All labels follow
all geometry in ascending ordinary paint ordinal, matching the approved AWT pass. Each becomes one
`text` element at its recorded baseline with:

```text
font-family="sans-serif"
font-size="<logical pixels>"
font-weight="normal|bold"
textLength="<measured advance>"
lengthAdjust="spacingAndGlyphs"
xml:space="preserve"
```

Fill color and opacity come from `LabelTextStyle`; no stroke/background/halo/shadow is emitted. Text
content is escaped as XML character data. Every Unicode scalar must be legal in XML 1.0; unpaired
surrogates, forbidden controls, CR/LF/line/paragraph separators, non-finite metrics, negative
advance, or a style outside G11's fixed family/weight/size profile is terminal. Tabs remain literal
text because G11 accepts them. The writer does not normalize, trim, wrap, truncate, format, or insert
font fallback metadata.

The generic family and recorded advance preserve the intended layout envelope without promising
identical glyph outlines on every viewer. There is no font embedding, local font lookup, glyph-to-path
conversion, AWT metric call, or external reference. Identical snapshots produce identical bytes.
Two captures of an equivalent live view on different platforms may differ in label baselines,
advances, or admitted collisions because G11 explicitly makes those metric inputs platform-dependent;
that is not represented as cross-platform byte determinism.

### Canonical SVG 1.1 serialization

The complete output grammar contains only `svg`, `defs`, `clipPath`, `rect`, `g`, `path`, and `text`.
It always writes a `defs` containing `v0`, then any hatch clips, and one paint `g` clipped to `v0`.
No empty/alternate syntax is chosen based on a serializer provider. The fixed outer form is:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="W" height="H" viewBox="0 0 W H">
  ...canonical fixed-order content...
</svg>
```

The exact attribute order and omission policy is:

| Construct | Attributes in order |
| --- | --- |
| root `svg` | `xmlns`, `version`, `width`, `height`, `viewBox` |
| any `clipPath` | `id`, `clipPathUnits="userSpaceOnUse"` |
| viewport clip `rect` | `x="0"`, `y="0"`, `width`, `height` |
| polygon clip `path` | `d`, `clip-rule="evenodd"` |
| paint `g` | `clip-path="url(#v0)"` |
| background `rect` | `x="0"`, `y="0"`, `width`, `height`, `fill`, optional `fill-opacity` |
| filled `path` | `d`, `fill`, `fill-rule="evenodd"`, optional `fill-opacity` |
| stroked `path` | `d`, `fill="none"`, `stroke`, `stroke-width`, `stroke-linecap="round"`, `stroke-linejoin="round"`, optional `stroke-opacity` |
| hatch stroked `path` | `d`, `fill="none"`, `stroke`, `stroke-width`, `stroke-linecap="round"`, `stroke-linejoin="round"`, optional `stroke-opacity`, then `clip-path="url(#cN)"` |
| label `text` | `x`, `y`, `fill`, optional `fill-opacity`, `font-family`, `font-size`, `font-weight`, `textLength`, `lengthAdjust`, `xml:space` |

`defs`, the outer paint group, and every `clipPath` use explicit start/end tags. `rect` and `path`
use `/>`; `text` always uses start/content/end tags. One indentation level is added per open
container, including content within `defs`, and an empty paint group is still emitted as two tags.
The background alpha and every leaf color alpha are exactly `rgbaAlpha / 255.0` multiplied by the
applicable symbol/composite opacity; the optional opacity attribute is omitted only when that result
is exactly `1.0`.

Serialization uses UTF-8 without BOM, LF only, two-space indentation, no trailing spaces, and one
final LF. Element and attribute order is specified by the tables in this section and never inherited
from a map, collection iteration, XML writer, or locale. The implementation writes directly to one
bounded byte sink; it does not use DOM, Transformer, provider lookup, reflection, service discovery,
or an XML output factory whose escaping/empty-element choices could vary. A secure JDK StAX parser is
used only in tests to assert the resulting structure.

Path data uses uppercase `M`, `L`, `Q`, `C`, and `Z`, absolute coordinates, single ASCII spaces, and
no commas, shorthand, relative commands, or redundant close coordinate. Every finite double is
canonicalized from negative to positive zero and then rendered by locale-independent
`Double.toString`; integer page dimensions/ordinals use ASCII decimal. There is no precision option,
rounding, exponent rewriting, or lossy quantization. Colors are lowercase `#rrggbb`. A leaf omits
`fill-opacity`/`stroke-opacity` exactly when effective alpha is one and otherwise writes the
canonical double in `[0,1]`; transparent leaves remain in traversal and serialize opacity zero.
Fixed defaults are never delegated to a viewer: path fill/stroke, fill rule, line cap/join/width,
text family/size/weight/fill, and clip rule are written explicitly where they apply.

XML escaping uses `&amp;`, `&lt;`, and `&gt;` in text and the fixed five XML attribute escapes in
attributes. It emits scalars directly as UTF-8 rather than numeric references. No source-derived ID,
class, style block, CSS, script, event attribute, animation, external/local reference, `<image>`,
data URL, DTD, entity declaration, processing instruction beyond the fixed declaration, CDATA,
comment, metadata, title, description, namespace prefix, arbitrary attribute, or arbitrary SVG
fragment can appear.

### Limits, diagnostics, cancellation, and file replacement

The API-owned `VectorExportSnapshotLimits.defaults()` contains only detached-snapshot concerns.
Complete immutable withers set any positive value at or below the corresponding hard maximum. The
factory and `MapView` capture accept one explicit value; convenience overloads use defaults. Capture
applies the tighter of these limits and the already effective G4 query/G11 label ceilings, never
widens a source operation, and charges its detached output independently of source staging.

| Snapshot limit | Default and hard maximum |
| --- | ---: |
| Page width or height | 16,384 |
| Layers | 1,024 |
| Feature primitives | 100,000 |
| Geometry coordinate pairs | 10,000,000 |
| Composite depth | 64 |
| Aggregate symbol nodes | 1,000,000 |
| Labels | 4,096 |
| Label Unicode code points | 262,144 |
| Conservatively owned snapshot bytes | 268,435,456 |

The SVG-owned `SvgExportLimits.defaults()` contains only serialization work that is not already a
snapshot invariant. Its complete immutable withers follow the same positive-at-or-below-hard-maximum
rule:

| Writer limit | Default and hard maximum |
| --- | ---: |
| Emitted SVG elements, including defs/clips | 1,000,000 |
| Emitted path commands | 10,000,000 |
| Hatch candidate segments | 1,000,000 |
| Encoded output bytes | 67,108,864 |
| Conservatively owned writer bytes | 268,435,456 |

There is no zero, negative, unlimited sentinel, system-property override, or mutable global in either
value. Writer-owned bytes use this exact semantic inventory:

| Writer-owned occurrence | Logical byte charge |
| --- | ---: |
| operation state and fixed counters | 64 |
| reconstructed `MapScreenBasis` | 64 |
| each returned `MarkerTransform` or `LineEndpointBearings` | 64 |
| each returned `HatchSegments` wrapper | 64 |
| each conservative hatch candidate represented by its packed result capacity | 32 |
| each prospective finite-double token | fixed 64-byte reservation |
| each output-sink chunk | its full byte capacity |
| final exact-length returned byte array | its length |

The sink uses deterministic chunks: each new capacity is
`min(8,192, effectiveOutputByteMaximum - alreadyAllocatedChunkCapacity)`. It charges a full chunk
before allocation, counts each emitted UTF-8 byte independently against `outputBytes`, then charges
and allocates one exact-length publication array while the chunks still exist. Fixed XML literals,
snapshot-owned text/geometry/symbols, primitive counters, and a `ByteBuffer` view over the final array
have no additional charge. XML text/IDs/integers are encoded directly without retained strings;
before invoking `Double.toString`, the writer charges the fixed 64-byte finite-double-token
reservation. The resulting canonical token must fit within 32 UTF-16 code units, is consumed
immediately, and receives no refund. Java 21's specified finite representation is shorter than that
ceiling; boundary-value tests pin the reservation. Thus the charge is prospective even though the
JDK formatter returns a temporary string. Marker/path coordinates are transformed and encoded one
command at a time.
`HatchLayouts.cover` is called once per positive-candidate hatch occurrence
in the paint traversal. G11-041 narrowly adds allocation-free
`HatchLayouts.candidateSegmentCount(...)`, which returns the exact conservative non-negative count
already computed by `cover` preflight (or `Long.MAX_VALUE` for arithmetic overflow); `cover` and the
new method share one private calculation and equivalence tests. The method adds no retained plan or
new type. During the definitions traversal, before emitting that hatch's clip, the writer checks the
count against the symbol, aggregate-segment, and owned-byte ceilings, then reserves one 64-byte wrapper
plus `32 * candidateCount`; zero candidates reserve no wrapper and emit neither clip nor hatch path.
The paint traversal recomputes and asserts the same immutable count, then calls `cover` with that exact
positive `int` maximum without charging again. Corner-only candidates remain charged even when fewer
segments are emitted; a zero-segment result follows the unused-clip policy above. The definitions
traversal otherwise uses existing polygon coordinates directly and allocates no hatch result.

Counts and charges are prospective and cumulative; releasing a core result or sink chunk does not
refund them. Snapshot accounting independently charges its reachable occurrences and is excluded from
writer-owned bytes. Checked overflow records `requested=Long.MAX_VALUE`. Equality succeeds; maximum
plus one fails with no externally returned partial bytes or file touched.

`VectorExportSnapshotException` carries `VectorExportSnapshotProblem`; construction/capture uses only
the following exact insertion-ordered shapes. There are no conditional keys:

| Code and case | Exact ordered context |
| --- | --- |
| `VECTOR_EXPORT_LAYER_UNSUPPORTED` | `layerIndex`, `kind` |
| `VECTOR_EXPORT_SYMBOL_UNSUPPORTED` | `layerIndex`, `featureIndex`, `symbolOrdinal`, `kind` |
| `VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID`, component/frame | `field`, `reason` |
| `VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID`, primitive | `field=geometry`, `reason`, `layerIndex`, `featureIndex` |
| `VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID`, label | `field` (`labelText` or `labelMetric`), `reason`, `labelIndex`, `ordinaryPaintOrdinal` |
| `VECTOR_EXPORT_SNAPSHOT_LIMIT_EXCEEDED` | `limit`, `maximum`, `requested` |
| `VECTOR_EXPORT_SNAPSHOT_CANCELLED` | empty |

Closed layer `kind` is `raster` or `elevation`; symbol `kind` is `rasterIcon`, `legacy`, `custom`,
`futureBuiltIn`, or `wrongDescendant`; component/frame `field` is `componentSize`,
`componentBackground`, or `viewFrame`; and `reason` is `missing`, `zero`, `nonOpaque`, `nonFinite`, `range`, or
`xmlScalar`. Snapshot `limit` is `pageAxis`, `layers`, `features`, `coordinates`, `compositeDepth`,
`symbolNodes`, `labels`, `labelCodePoints`, or `ownedBytes`. Valid field/reason pairs are
`componentSize/zero|range`, `componentBackground/missing|nonOpaque`, `viewFrame/nonFinite|range`,
`geometry/nonFinite|range`, `labelText/xmlScalar`, and `labelMetric/nonFinite|range`. `labelIndex` is
the zero-based index in ascending accepted-label output order and
`ordinaryPaintOrdinal` is its bounded snapshot value. Existing source, CRS, symbol-transform, and
label exceptions retain their own type/problem/report rather than being copied into this table.
Context never includes a Java class name or renderer key.

Capture precedence is public arguments, EDT/lifecycle, already-cancelled token, component/background
and page-limit checks, unsupported-layer preflight in layer order, then each layer's query/projection,
resolved-symbol/profile/accounting checks in feature order, label placement/accounting, final
cancellation, and publication. Raster/elevation rejection therefore occurs before source I/O. A
non-cancellation source/CRS/label failure already encountered is never overwritten because the token
is observed later.

`SvgExportException` carries one immutable `SvgExportProblem(code, context)` and an optional cause.
The writer likewise has no conditional context keys:

| Code and case | Exact ordered context |
| --- | --- |
| `SVG_EXPORT_VALUE_INVALID`, frame | `field=viewFrame`, `reason` |
| `SVG_EXPORT_VALUE_INVALID`, primitive/symbol | `field` (`geometry`, `symbolTransform`, or `hatchLayout`), `reason`, `layerIndex`, `featureIndex`, `symbolOrdinal` |
| `SVG_EXPORT_VALUE_INVALID`, label | `field` (`labelText` or `labelMetric`), `reason`, `labelIndex`, `ordinaryPaintOrdinal` |
| `SVG_EXPORT_LIMIT_EXCEEDED` | `scope`, `limit`, `maximum`, `requested` |
| `SVG_EXPORT_CANCELLED` | empty |
| `SVG_EXPORT_IO_FAILED` | `operation`, `reason` |
| `SVG_EXPORT_ATOMIC_MOVE_UNSUPPORTED` | empty |

Writer tokens are closed: `reason=nonFinite|range|xmlScalar`,
`scope=symbol|writer`, `limit=elements|pathCommands|hatchSegments|outputBytes|ownedBytes`,
`operation=preflight|temporary|write|force|close|move|delete`, and I/O
`reason=missing|accessDenied|alreadyExists|wrongKind|symlink|closed|other`. Every writer-owned limit
uses `scope=writer`; `scope=symbol` is valid only for one hatch node's G2 `maxSegments` ceiling.
Valid value field/reason pairs are `viewFrame/nonFinite|range`, `geometry/nonFinite|range`,
`symbolTransform/nonFinite|range`, `hatchLayout/nonFinite|range`, `labelText/xmlScalar`, and
`labelMetric/nonFinite|range`.
Context contains only these ASCII tokens and bounded decimal ordinals/counts. It never contains source
IDs, feature IDs, label text, XML, paths, catalog names, renderer keys, provider classes/messages, or
exception messages.

Writer precedence is public arguments/configuration, already-cancelled token, snapshot/effective
limits, the definitions traversal, the paint traversal, final cancellation, then file operations.
The writer supplies fixed bounded identifier `svg-export` to core algorithms that require a
diagnostic feature ID, catches their known `SymbolException`/basis failures, and maps transform/value
failures to `SVG_EXPORT_VALUE_INVALID` with the exact current snapshot ordinals above. The synthetic
identifier and core message never enter output context.

For each hatch symbol node, candidate counts accumulate across all polygon components of that one
primitive; another hatch child starts its own symbol counter. The writer also holds one operation-wide
candidate counter and the exact current writer-owned-byte count. After the allocation-free core count,
crossing checks occur in this order:

1. the hatch node's `maxSegments`: `scope=symbol`, `limit=hatchSegments`, `maximum` is that configured
   cap, and `requested` is the checked node-cumulative candidate count;
2. the SVG aggregate: `scope=writer`, `limit=hatchSegments`, `maximum` is the effective export limit,
   and `requested` is the checked operation-cumulative candidate count; then
3. the writer allocation: `scope=writer`, `limit=ownedBytes`, `maximum` is the effective owned-byte
   limit, and `requested` is current bytes plus 64 plus `32 * candidateCount`.

A tie therefore preserves the G2 per-feature symbol cap before export-only caps. Arithmetic/core
overflow records `requested=Long.MAX_VALUE`. Only after all three succeed does the writer charge and
call `HatchLayouts.cover`; later element/path-command checks cannot replace an earlier hatch crossing.

`encode` validates and traverses the complete snapshot and returns a fresh exact-length byte array.
After the initial argument/already-cancelled check, both definitions and paint passes poll before each
primitive, symbol node, geometry part, hatch count/layout, label, output-chunk allocation, and final
array allocation. One last poll immediately before publication is the operation's success
linearization point; cancellation after it does not retract a returned array. An observed cancellation
releases private buffers and returns no bytes. It never reports a partial document.

`writeAtomically` first runs the complete `encode` path and holds the validated canonical bytes before
examining the target. It then reuses G11-003's file policy: under `NOFOLLOW_LINKS`, require an existing
real parent directory and an absent or regular non-symlink target; create one unpredictable
same-directory sibling with `CREATE_NEW`; write every byte; call `FileChannel.force(true)`; close;
then move with `ATOMIC_MOVE|REPLACE_EXISTING`.
There is no non-atomic fallback, cross-directory temporary, pre-delete, in-place write, backup,
permission/owner copying, or directory force claim. After `encode` returns, file output polls the
same token at these exact checkpoints: before and after target preflight; immediately before and after
temporary creation; before and after every `FileChannel.write` invocation over slices of at most
65,536 bytes; immediately before and after `force(true)`; after the mandatory close; and immediately
before the atomic move. Close runs exactly once on every created channel even when a prior poll
cancels. The successful atomic move is the file operation's linearization point and there is no
post-move poll, so later cancellation cannot retract success.

At a poll with no prior failure, cancellation becomes primary, the channel is closed, and the
temporary is deleted once. A create/write/force/close/move failure already encountered remains
primary even if the token is then observed; cancellation and cleanup failures are suppressed rather
than replacing it. Conversely, close/delete failures after cancellation are suppressed onto
`SVG_EXPORT_CANCELLED`. Thus cancellation before creation leaves no temporary, cancellation during
bounded writing/force or after close deletes it and leaves an existing target unchanged, and
cancellation racing a move is decided by the immediately preceding poll versus successful move.

### Verification, decomposition, and checkpoint

Later API tests cover nested-value immutability, list/order/role validation, positive-zero/bearing
normalization, exact/one-over snapshot counts and accounting, unsupported recursive symbols, stripped
labels, and equal snapshot values. Core/SVG tests pin every semantic byte-inventory row, deterministic
sink-chunk/final-array charge, fixed pre-`Double.toString` token reservation and boundary lengths,
hatch candidate-count/cover equivalence (including positive-candidate/zero-segment omission and its
unused clip), and exact/one-over writer limits. Programmatic snapshots assert exact canonical
bytes, repeat/equal-snapshot identity, every path command and geometry family, multipart/component/
composite/endpoint/hatch/outline order and hatch clip attributes, map/screen units and rotations,
holes, opacity multiplication,
background/viewport clips, text escaping/advance, XML scalar policy, all limit/overflow/cancellation
points (including every post-encode file checkpoint), and every exact stable context shape. Secure
StAX tests assert only the closed grammar and no
external reference; they do not reuse the G10 marker importer as an oracle.

AWT integration tests capture snapshot, source, and editable bindings; assert one query and cursor
close per source layer; prove exact attribute projection, authoritative rather than G7 optimized
geometry, current portrayal, deterministic layer/feature order, label stripping/order, all-or-nothing
source/CRS/label failures, unsupported raster/elevation/custom/raster-icon/legacy content, EDT rule,
fixed paint/capture metric parity, limits, cancellation, and no retained view/source/cursor/AWT
object. A runnable example exports the
same viewport it displays and reports the output path plus structured failure without auto-opening a
browser.

There is no JDK SVG renderer and no second test-only dependency is justified. G11-043 therefore
requires a named manual comparison in current Firefox and Chromium: open one checked-in expected-case
export, compare page/background, broad color regions, geometry bounds/order, holes, markers,
arrowheads, hatches, and label envelopes against the live example, and record browser/OS versions.
The comparison is tolerant and visual, not a pixel hash or glyph-identity claim. Structural/exact-byte
tests remain the automated oracle.

Native evidence appends one literal, resource-free programmatic snapshot encode and temporary-file
write to the existing shared executable, asserting exact structural tokens and one
`VECTOR_EXPORT_SYMBOL_UNSUPPORTED` construction diagnostic on JVM and the required Linux Native
Image lane. AWT capture is not part of that headless native scenario. Publication extends the
existing SVG artifact contract and standalone consumer: it
uses the staged API/core/SVG artifacts to construct, encode, and write one snapshot. Architecture
tests update the SVG allowlist from API-only to exactly API plus core and continue to forbid AWT,
network, discovery, reflection, DOM/Transformer, and prohibited native mechanisms. No new artifact or
specialized Gradle lane is added.

Implementation is deferred into four reviewable vertical slices:

1. `G11-040` adds API snapshot/problem values and the SVG module's limits/problem/facade with exact
   canonical background, point/line/polygon solid-symbol encode and atomic write from a programmatic
   snapshot.
2. `G11-041` adds real AWT capture, portrayal/label handoff, all six geometry families, composites,
   endpoints, the allocation-free core hatch-candidate count plus hatches, and the runnable export
   example.
3. `G11-042` completes exact limits/accounting, cancellation, stable diagnostics, hostile values,
   injected file failures, and cleanup/atomic-replacement evidence.
4. `G11-043` completes Javadocs, architecture/render-structure checks, manual browser checkpoint,
   shared Linux Native Image scenario, publication, and staged offline consumer.

The exact graph is `G11-005 + G11-022 -> G11-040`; `G11-040 + G11-023 -> G11-041 -> G11-042`; and
`G11-042 + G11-024 -> G11-043`. G11-022 supplies the public label values used by the programmatic
snapshot, G11-023 supplies real placement/capture, and G11-024's native/publication closeout lands
before the shared final evidence. The SVG slices are internally serialized. They are not path-safe
with those label tasks when both touch API/AWT/native/publication/example/task/roadmap files, so one
integrator owns such overlaps. A module is not added: G11-040 extends `mundane-map-io-svg` only after
G10-001 exists.

The named HITL checkpoint is **G11 canonical static SVG vector-map export profile approval**. A
maintainer approves the target, snapshot/AWT/I/O ownership, supported/rejected matrix, text/font and
no-fallback policies, canonical grammar/numbers/IDs, limits/diagnostics/cancellation, atomic
replacement, manual comparison, native/publication scope, and four-slice graph before G11-040.

## G11 holistic simplicity closeout

G11 adds five independent capabilities only where an observable workflow requires them:

- editing is an application-owned immutable point-session with bounded history/snapping, not a
  mutation mode attached to read-only sources;
- thematic portrayal and one singular-point label pass replace parallel styling/label mechanisms
  without adding an expression language or layout engine;
- workspace v1 persists only portable local references/configuration through explicit application
  openers and does not serialize live edit, portrayal, source, registry, or cache graphs;
- only the two demonstrated SQLite format adapters are accepted, while JTS, PROJ, and GDAL reserve
  nothing until evidence changes; and
- vector export is one detached snapshot plus one canonical SVG writer in an existing module, not a
  renderer/document/plugin framework.

The boundaries do not overlap: edit snapshots are authoritative data state; portrayal resolves one
display symbol; a vector-map snapshot is a short-lived detached picture; workspace documents contain
references/configuration only; adapters supply ordinary format-neutral sources. None is used as a
generic persistence, command, scene, provider, or object-graph abstraction for another. Shared API
values are immutable and minimal, core owns JDK-only algorithms, AWT alone owns Java2D/Swing/live
capture, and format modules own bounded parsing/serialization. G11 therefore remains simple enough
for a small embeddable map library while preserving the exact extension seams already demonstrated.

## G0–G11 whole-design simplicity closeout

The completed top- and mid-level design still follows one directional model:

```text
immutable API contracts
        -> JDK-only core algorithms
        -> explicit AWT presentation and tools
        -> independent AWT-free format sources/writers
        -> explicit application composition/examples
```

The textual arrows describe use, while the enforced module graph remains API at the bottom, core over
API, AWT over API/core, and each I/O module over API plus only inventoried core algorithms. External
libraries exist only in named Level 2 adapters with non-leaking types and explicit platform/native
policies. Every production module appears with working behavior; deferred formats/projections/
adapters create no placeholders. Registries and source/opening choices are instance-owned and
explicit. Public values are immutable with defensive copies and packed primitive storage where it
materially reduces coordinate/sample overhead. Untrusted binary/text inputs have typed limits,
stable bounded diagnostics, exact lifecycle, deterministic hostile fixtures, and separate corpus
evidence where interoperability warrants it.

Native Image remains an architectural lane rather than a final retrofit: Level 1 closes on the pinned
Linux lane, each targeted Level 2 capability appends a direct scenario, and no Windows/macOS/optional-
adapter claim exists without separate evidence. Performance work remains evidence-first; G7 adds only
qualified indexes/paint optimizations/caches, preserves authoritative geometry, and adds no native
acceleration. Rendering regression, format corpus, performance, native, publication, and consumer
evidence remain separate lanes with one explicit owner each rather than becoming one opaque gate.

No simplification is currently justified by merging feature/raster/elevation sources, image/elevation
formats, map/edit/snapshot/workspace values, SVG import/export grammars, or JDK-only/optional-adapter
modules: each boundary protects a real lifecycle, semantic, security, toolkit, or deployment
difference. Conversely, no current consumer justifies a generic plugin system, scene graph, data
binding layer, geometry engine, projection framework, cache framework, background scheduler, or
custom native library. Later implementation should begin at G0-001 and preserve these approved
decisions, revisiting a profile through its named HITL checkpoint whenever evidence invalidates an
assumption rather than silently widening the design.
