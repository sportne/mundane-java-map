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
request using the current child graphics' `FontRenderContext`. It extracts only finite visual bounds
and advance into toolkit-neutral immutable placement input. AWT objects remain operation-local. Font
fallback and exact glyph outlines are JDK/platform behavior; deterministic ordering is promised for a
given input and metric set, not pixel-identical glyphs across operating systems.

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
