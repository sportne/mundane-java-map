# G4 — Sources and CRS design

Project index: [DESIGN.md](../DESIGN.md).

## Format-neutral sources

### Source contract and diagnostic profile (G4-001)

#### Synchronous source surface

Level 1 has two explicit pull contracts rather than a generic parser/source hierarchy, `Stream`,
reactive publisher, future, or implicit worker:

```text
FeatureSource extends AutoCloseable
  metadata() -> FeatureSourceMetadata
  limits() -> FeatureSourceLimits
  openingDiagnostics() -> DiagnosticReport
  openCursor(FeatureQuery query, CancellationToken cancellation) -> FeatureCursor
  isClosed() -> boolean
  close()

FeatureCursor extends AutoCloseable
  advance() -> boolean
  current() -> FeatureRecord
  diagnostics() -> DiagnosticReport
  isClosed() -> boolean
  close()

RasterSource extends AutoCloseable
  metadata() -> RasterSourceMetadata
  limits() -> RasterSourceLimits
  openingDiagnostics() -> DiagnosticReport
  read(RasterRequest request, CancellationToken cancellation) -> RasterRead
  isClosed() -> boolean
  close()
```

Methods execute synchronously on the caller's thread. Sources and cursors require external
serialization and create no executor, callback thread, prefetcher, or hidden cache. Immutable returned
values are thread-safe; only cancellation is deliberately cross-thread. `FeatureSource` permits at
most one live cursor, which is enough for viewport rendering and keeps sequential channels honest.
Opening a second is a lifecycle `IllegalStateException`, not an adapter-specific behavior.

The base `RasterSource` contract likewise makes no concurrent-call promise. G6-004's concurrent
identical-request requirement deliberately strengthens the concrete cached image source/decoder; it
does not retroactively make every synthetic, file, remote, or consumer source thread-safe. That task
must define read/read and read/close races for its implementation while ordinary callers continue to
serialize through the interface.

`advance()` moves from `NEW` or `CURRENT` to a new current record and returns true, or releases
operation resources, enters `EXHAUSTED`, and returns false. Repeated advance after exhaustion returns
false without I/O. `current()` is valid only after the latest true result and before another advance or
close; other calls are `IllegalStateException`. A yielded immutable record remains independently valid
when retained by the consumer. A terminal read failure releases operation resources, enters `FAILED`,
and throws `SourceException`; later advance/current calls are lifecycle failures. Early and repeated
close are safe, invalidate current, and never close the parent source. The first cursor close marks the
cursor `CLOSED`, invalidates current, and releases the source slot before attempting cleanup exactly
once. If cleanup fails, it throws `SOURCE_CLOSE_FAILED`; its report contains the cursor warnings and
terminal error. A repeated close is a no-op and never retries cleanup. The parent remains open and may
service a later cursor, even when direct cursor close reported a cleanup failure.

Exhaustion, terminal failure, cancellation, and the first early close each release the fixed
one-cursor slot exactly once. Repeated close or exhausted advance cannot release a later cursor's slot.

Source close is idempotent. It marks the source closed before cleanup, closes its live cursor first,
then its own handles exactly once. The first cleanup failure remains primary and later failures are
suppressed; a failed close is not retried. Metadata, effective limits, opening diagnostics, and cursor
diagnostics remain readable after close. Operational source calls and cursor use after explicit/source
close are `IllegalStateException`. There is no finalizer, `Cleaner`, shutdown hook, or implicit retry.
When source close invokes cursor close, it continues with its own cleanup after any cursor failure and
aggregates failures by the same primary/suppressed rule.

Raster reads are all-or-nothing. Success publishes one immutable `RasterRead`; cancellation or failure
discards the partial buffer and throws. Feature failure does not revoke records already yielded. A
cancelled/failed cursor releases the source's one-cursor slot, and the still-open source may service a
later operation. An ordinary `SourceException` from `RasterSource.read` likewise releases every
operation-owned resource and leaves an open source reusable; retry may fail with the same stable error,
but only explicit source close makes it permanently closed. After cleanup, an unexpected
`RuntimeException` or `Error` still propagates unchanged and provides no reuse guarantee, so the caller
closes the source.

#### Identity, metadata, and unstyled features

`SourceIdentity(id, displayName)` owns a non-blank exact logical ID and a display name that may be
empty. Both are limited to 256 UTF-16 characters. The ID is supplied by the opener, need only be
stable within the application, and is never required to contain an absolute path, URI, credential, or
other sensitive locator. Diagnostics use this ID; component locations identify sidecars/codecs
separately.

The metadata values are:

```text
FeatureSourceMetadata(
  SourceIdentity identity,
  Optional<Envelope> extent,
  OptionalLong featureCount,
  Optional<AttributeSchema> schema,
  Optional<CrsMetadata> crs)

RasterSourceMetadata(
  SourceIdentity identity,
  int width, int height,
  Optional<Envelope> mapBounds,
  Optional<CrsMetadata> crs)
```

Metadata is captured once at successful open and remains immutable after close. A feature extent may
be conservative; a present feature count is exact, so a source leaves it absent rather than exposing a
header estimate. It counts exposed `FeatureRecord` values, excluding physical null/deleted/skipped
format records. Missing CRS is distinct from G4-002's retained unknown CRS. Bounds are expressed in the
declared source CRS even when that CRS is unknown; without a recognized transform they remain
metadata, not permission to render. Raster dimensions are positive. The exact baseline raster mapping
is defined below; affine rotation/shear begins in G6-002 rather than appearing as an empty abstraction
here.

Format readers yield `FeatureRecord(id, name, geometry, attributes)`, not styled `Feature` values. The
record uses the existing exact non-blank ID rule, allows an empty display name, and owns immutable
geometry and ordered attributes. A source-backed layer in G4-003 supplies explicit caller-owned
marker/line/fill symbols by geometry role. Parsers therefore never choose cartography, and the same
record can be presented differently without mutation or synthetic parser options.

G4-003 seals `Geometry` to the source-listed API implementations while adding multipoint and multipart
values. Every permitted geometry is deeply immutable, owns packed primitive coordinates, has stable
value equality, and returns the same finite envelope for its lifetime. The current open extension
point is removed during `0.x`: an arbitrary geometry was never renderable through the closed geometry
dispatch and cannot satisfy immutable-record ownership mechanically.

Within one opened source, every exposed record ID is unique and stable for that source's lifetime;
repeated queries for the same underlying record return the same exact ID. Adapters derive that
property structurally where possible (for example from a stable record number) rather than scanning
the whole source. An encountered duplicate terminates before yielding the duplicate with
`SOURCE_DUPLICATE_FEATURE_ID`; an in-memory source validates its complete snapshot at construction.
Uniqueness is source-local—different layers/sources may reuse an ID because interaction identity also
contains the layer ID.

`Layer` and `InMemoryLayer` remain supported snapshot APIs through Level 1 and are not made to
materialize a lazy source. G4-003 adds an in-memory source and a source-backed rendering path while
retaining the existing layer path as a compatibility adapter. Deprecation, if justified, waits for the
G8 API review. Existing examples' string attributes remain valid.

#### Ordered bounded attributes and queries

`FeatureRecord` and the existing `Feature` preserve the public `Map<String,Object>` shape during
`0.x`, but their constructors canonicalize one explicit Level 1 value set into a defensive insertion-
ordered unmodifiable `LinkedHashMap`:

- `String`, `Boolean`, `Long`, finite `Double`, `BigDecimal`, and `LocalDate` are retained;
- byte, short, and integer become `Long`; finite float becomes `Double`; `BigInteger` becomes
  `BigDecimal`;
- `AttributeNull.INSTANCE` is retained and represents a present null independently of a missing key;
  and
- an existing `AttributeBytes` is retained, while raw `byte[]` becomes that immutable value-based
  wrapper, which clones input/output, provides length/index access, and never prints payload contents.

Keys are non-blank, at most 256 UTF-16 characters, preserved exactly, and retain caller iteration
order. Null values, non-finite floating values, arbitrary `Number` subclasses, every other array,
collection/map/optional value, mutable value, parser-library object, and external-adapter type are
rejected before storing anything. Allowed JDK numeric/date values use the exact listed runtime classes
rather than accepting arbitrary subclasses. Recursive attribute collections remain a later
GeoJSON-profile decision. `Map.copyOf` is not used because its iteration order is not the schema
contract.

`AttributeSchema` defensively owns an ordered unique list of
`AttributeField(name, AttributeType, nullable)`. The type enum exactly matches the canonical set
(`TEXT`, `LOGICAL`, `INTEGER`, `FLOATING`, `DECIMAL`, `DATE`, `BINARY`); nullability is separate. A
present schema requires every emitted value to be compatible and every attribute key to be declared.
Before query projection, an `ALL` record contains every declared field exactly once; a nullable field
uses `AttributeNull`, while a non-nullable field cannot be null or missing. `ONLY` contains every
selected field in request order under the same rule, and `NONE` is empty. Absence of a schema permits
dynamic scalar fields and `AttributeNull`.

```text
FeatureQuery(
  Optional<Envelope> sourceBounds,
  AttributeSelection attributes,
  Optional<FeatureQueryLimits> tighterLimits)

AttributeSelection = ALL | NONE | ONLY(ordered unique names)
```

An absent envelope means all records. A present envelope uses inclusive intersection with the feature
geometry envelope in source coordinates; this is a bounding-box predicate, not exact topology.
Results retain source record order and are never implicitly sorted. Attribute projection is not a
filter expression: `ALL` preserves source/schema order, `NONE` emits an empty map, and `ONLY` preserves
the requested unique name order for values that are present. An unknown requested field against a
known schema terminates the query with `SOURCE_QUERY_ATTRIBUTE_UNKNOWN`; dynamic schemas may omit a
requested value on a particular record. `ONLY` requires a non-empty list; callers use `NONE` rather
than relying on an ambiguous empty projection.

#### Raster window and pixel ownership

```text
RasterWindow(int column, int row, int width, int height)

RasterRequest(
  RasterWindow sourceWindow,
  int outputWidth, int outputHeight,
  Optional<RasterRequestLimits> tighterLimits)

RasterRead(
  RasterWindow sourceWindow,
  RgbaPixelBuffer pixels,
  DiagnosticReport diagnostics)
```

When map bounds are present, both spans are positive. Column edges increase east/right from
`minX` to `maxX`; row edges increase down from `maxY` to `minY`. For metadata width `W`, height `H`,
column edge `c` is the checked finite interpolation `lerp(minX,maxX,(double)c/W)`, and row edge `r` is
`lerp(maxY,minY,(double)r/H)`. Thus row zero is the north/top row and a pixel center is at half an edge
interval. A `RasterRead.sourceWindow()` equals the request window exactly, and its pixel-buffer
dimensions equal the requested output dimensions exactly.

Windows use non-negative integer grid coordinates and positive dimensions, denote half-open ranges,
and validate end-coordinate addition in `long`. A source accepts only a window wholly contained by
its metadata dimensions. It does not clip: out-of-range input terminates with
`RASTER_WINDOW_OUT_OF_RANGE`, avoiding ambiguous output georeferencing. MapView computes and clamps a
visible intersection before requesting it; empty intersection issues no request.

G4's baseline resampling is deterministic pixel-center nearest neighbor from the strict source window
to the positive requested output dimensions. For zero-based output index `o`, source size `S`, and
output size `D`, the relative source index is exactly
`floor(((2 * o + 1) * S) / (2 * D))` using checked `long` multiply/add; the half-open rule assigns an
exact tie to the greater/right-or-bottom cell and the result is necessarily in `[0,S)`. Add the source
window's column/row to obtain the sample. G6-003 later adds explicit nearest/bilinear control. Opacity
is a layer/render concern and is not part of a source request.

This explicitly refines stale wording in the G6-003 task card: that task adds only
`RasterInterpolation` to the immutable `RasterRequest`. Cancellation remains the per-invocation token
so one request value can be retried, and opacity remains immutable raster-layer/render state so decoded
pixels and decode/resample cache keys do not vary with composition. The card must be corrected before
its production task begins; the G4 implementation does not pre-add either field.

`RgbaPixelBuffer` owns positive dimensions and row-major unpremultiplied `0xRRGGBBAA` ints, matching
G2 raster icons. A copying factory and a single-use builder cover caller-owned and producer-owned
construction. The builder allocates only after request-limit validation, transfers its array exactly
once from `build()`, clears its own reference, and rejects all later access. The immutable buffer
offers bounded `rgbaAt` and a defensive array copy. A successful `RasterRead` and its buffer remain
valid after request/source close; the source never retains a consumer buffer.

#### Limits and cancellation

Limits are relevant typed values, not a string-key bag or one record containing every future format's
knobs. The one-cursor rule is a fixed lifecycle invariant, not a tunable limit.
`FeatureSourceLimits` owns a complete `FeatureQueryLimits`; `RasterSourceLimits` owns a complete
`RasterRequestLimits`. The Level 1 defaults are:

| Feature-query ceiling | Default |
| --- | ---: |
| Records examined | 1,000,000 |
| Records returned | 100,000 |
| Coordinates returned | 10,000,000 |
| Attribute values returned | 1,000,000 |
| Decoded text characters returned | 16,777,216 |
| Conservatively owned payload bytes | 268,435,456 |
| Retained warnings | 256 |

| Raster-request ceiling | Default |
| --- | ---: |
| Source-window pixels examined | 67,108,864 |
| Output width or height | 8,192 |
| Output pixels | 16,777,216 |
| Decoded/intermediate bytes | 268,435,456 |
| Conservatively owned payload bytes | 268,435,456 |
| Retained warnings | 256 |

All maxima are positive; there is no zero/negative/unlimited sentinel, system-property override, or
mutable global. A concrete open-options value captures its full effective ceiling and may explicitly
raise or lower documented defaults. Query/request overrides may only tighten every field; a purported
increase is `IllegalArgumentException`. Omission inherits the opened ceiling. Format modules compose
these values with their own typed open limits for input/record bytes, record/part/point/field counts,
field/text width, dimensions, channels, nesting when applicable, and aggregate allocation. G5/G6 pin
those format defaults rather than extending a generic map.

Records-examined includes filtered, deleted, null, and recoverably skipped records. Payload accounting
uses deterministic logical capacity rather than actual heap size, current live-set size, or a
garbage-collector-dependent estimate. An operation's counter is cumulative: a charge is never removed
when a value is yielded, filtered, released, or replaced. Each owned occurrence is charged without
identity deduplication under this fixed table:

| Owned value or slot | Logical byte charge |
| --- | ---: |
| `byte` or binary payload element | 1 |
| `short` or UTF-16 character | 2 |
| `int`, packed pixel, or part-offset element | 4 |
| `long`, `double`, or reference slot | 8 |
| geometry coordinate pair | 16 |
| map entry | 16 for its two reference slots, plus key/value contents |
| list entry | 8 for its reference slot, plus separately owned value contents |
| `AttributeNull` or `Boolean` value | 1 |
| `Long`, `Double`, or `LocalDate` value | 8 |
| `BigDecimal` value | 4 for scale plus `max(1, ceil(abs(unscaled).bitLength / 8))` |

Every record occurrence charges twice the UTF-16 length of its ID and name, its packed geometry
coordinates and part offsets, and each attribute map entry, key, and value. A `String` attribute
charges twice its UTF-16 length and `AttributeBytes` charges its exact length. A feature query charges
that complete owned content once for every record it constructs for publication, even if an
implementation happens to reuse an equal immutable value; previously yielded records continue to
count. Operation-owned containers add their reference-slot charges separately, so G4-003's AWT staging
list adds eight bytes per staged record but does not charge the record contents a second time merely
for retaining the same instance. Object headers, alignment, implementation collection capacity, and
diagnostics are deliberately excluded; diagnostics have independent hard bounds and warning caps.

Format-specific aggregate-allocation counters use the same primitive and reference-slot charges for
parser-owned arrays, buffers, strings, and containers, including intermediate values later filtered or
discarded. They are separate from query payload, so a format can bound hostile parsing work without
changing the format-neutral returned-value contract. For a raster read, decoded/intermediate bytes are
the cumulative logical capacity of every project- or decoder-owned primitive/reference buffer created
or transferred during that invocation, charged once immediately before allocation or ownership
transfer. This includes an injected decoder's Java2D backing array and the final RGBA array when it is
produced through decode/resample. Conservatively owned raster payload independently charges four times
the output pixel count for the published `RgbaPixelBuffer`; overlap between these two ceilings is
intentional. Metadata and open-time values are outside per-operation counters and remain constrained by
their typed open limits and fixed value bounds.

Every prospective charge and count uses checked multiplication and addition before seek, slice,
decode, builder creation, transfer, or publication. Arithmetic overflow records `requested` as
`Long.MAX_VALUE` and fails before the allocation or transfer. Otherwise the exact prospective
cumulative value is compared to the effective maximum; equality is accepted and maximum plus one is
rejected. Exceeding a ceiling discards the current partial result and terminates with
`SOURCE_LIMIT_EXCEEDED` and exactly `scope`, `limit`, `requested`, and `maximum` context. Previously
yielded feature records remain valid.

`CancellationToken` is a read-only functional value with `isCancellationRequested()` and singleton
`none()`. `CancellationSource` owns an `AtomicBoolean`, exposes one token, and has monotonic idempotent
`cancel()`. It has no listener, interrupt, future, or executor. Sources poll before any I/O or large
allocation, between records/stages, at least once per raster output row, within project-controlled
byte/text/coordinate/pixel loops at no more than 4,096 primitive units, and immediately before
publication. Already-cancelled input performs no I/O or allocation.

Opaque JDK operations such as one blocking channel read or `ImageIO` decode are checked immediately
before and after but cannot promise an internal 4,096-unit checkpoint or latency bound. G6's injected
decoder boundary exposes bounded stages/regions where the JDK API permits them; it does not replace a
JDK codec merely to fake polling. Cancellation observed after an opaque call still discards its result
before publication.

Cancellation terminates with `SOURCE_CANCELLED`, discards the current record/window, and releases
operation resources; it is never silent EOF. A feature cursor enters distinct `CANCELLED`, invalidates
current, and releases the one-cursor slot; later cursor operations are lifecycle failures. A raster
read returns no value. Unless explicitly closed, both parent sources remain reusable for a new
operation after cancellation. Cross-thread close is not cancellation. A custom token failure is
cleaned up and propagated unchanged rather than mislabeled.

#### Stable bounded diagnostics

`DiagnosticSeverity` has `WARNING` and `ERROR`: a warning records one documented skip, fallback, or
substitution and continues; an error terminates the current open/query/read/cursor/close operation.
Programmer input/lifecycle failures remain `NullPointerException`, `IllegalArgumentException`, or
`IllegalStateException` and do not become source diagnostics.

```text
DiagnosticLocation(
  Optional<String> component,
  OptionalLong recordNumber,
  OptionalInt partIndex,
  OptionalInt fieldIndex,
  Optional<String> fieldName,
  OptionalLong byteOffset)

SourceDiagnostic(
  String code, DiagnosticSeverity severity, String sourceId,
  Optional<DiagnosticLocation> location,
  String message, Map<String,String> context)

DiagnosticReport(List<SourceDiagnostic> entries, long omittedWarningCount)
```

Codes match `[A-Z][A-Z0-9_]*`; subsystem prefixes such as `SOURCE_`, `CRS_`, and `SHAPEFILE_` are part
of the stable contract. Record numbers preserve a positive on-disk number; part/field indexes and byte
offsets are zero-based. Component is a stable token such as `shp`, `dbf`, `prj`, or `decoder`, never an
implicit path. Exact code and documented context keys are stable; message text and chained causes are
debugging aids, not matching contracts.

Hard value bounds are code 64, source ID 256, component 32, field name 256, message 1,024 UTF-16
characters, and 16 context entries with 64-character keys and 256-character values. Context keys are
canonicalized lexicographically. Diagnostics contain no raw bytes, credentials, unbounded untrusted
text, localized cause message, or adapter object. A documented bounded `causeKind` token may summarize
a failure.

`DiagnosticReport` preserves encounter order, retains the first `N` warnings under the effective
limit, never sorts or deduplicates entries, and saturates its omitted-warning count. A successful
source/cursor/read report contains warnings only. Final unchecked `SourceException` carries a report
whose terminal `ERROR` is
always appended last outside the warning cap, exposes that error directly, and may chain the original
cause. The terminal diagnostic can therefore never be truncated. Opening warnings belong to the
source snapshot, cursor warnings to that cursor, and raster warnings to `RasterRead`; no global or
asynchronous sink duplicates them.

Public report validation requires all non-empty entries to use one exact source ID; a successful report
has warnings only, while an exception report has exactly one `ERROR`, last, after zero or more
warnings. A cursor's
`diagnostics()` returns a fresh immutable warning-only snapshot observed so far: empty in `NEW`, growing
after successful/recoverable advances, then frozen unchanged after exhaustion, failure, cancellation,
or close. Its thrown `SourceException` alone adds the terminal error. Opening diagnostics are fixed at
source construction, and `RasterRead` contains the final successful request snapshot.

Only explicitly approved recovery emits a warning and continues. Underlying JDK I/O or parser errors
map once to a stable terminal code; optional adapters perform the same mapping inside their boundary.
Unexpected implementation `RuntimeException` and every `Error` propagate after deterministic cleanup
and are not disguised as hostile input. Close failure uses `SOURCE_CLOSE_FAILED`; cleanup after an
existing failure is suppressed under the existing primary.

#### AWT report and failure boundary

Sources have no callback sink. G4-003 instead adds one explicit AWT-host observation surface using
toolkit-neutral API values:

```text
MapSourceReportEvent(
  String layerId,
  Optional<DiagnosticReport> previous,
  Optional<DiagnosticReport> current)

MapSourceReportListener.onMapSourceReportChanged(MapSourceReportEvent event)
```

The optionals are non-null and unequal. `MapView.sourceReports()` returns a defensive map in current
installed layer order containing at most one non-empty report per source-backed layer; its size is
therefore bounded by the already installed layer snapshot, and every report has its own warning cap.
Opening warnings become the initial report at successful attachment. Each later cursor/read operation
replaces that layer's report with its warnings or terminal report, or clears it after a clean operation.
Repeated equal state is silent. Removal with clean close clears the retained entry. If closing a
removed owned binding fails, listeners receive the terminal transition followed by its removal-to-empty
transition before the failure is thrown; no detached layer report remains in `sourceReports()`.
Listener registration, identity duplicates, snapshot mutation, EDT confinement, failure aggregation,
and post-graphics delivery follow G3-003's event rules. Report changes do not themselves repaint
for presentation, because G4 adds no status overlay. MapView separately tracks each installed source
layer's last visual availability: a successful operation with or without warnings is `AVAILABLE`, and
a terminal report is `UNAVAILABLE`. Either availability change requests one full-component repaint.
Thus a narrow success-to-failure pass cannot retain old pixels, and a narrow recovered pass cannot
leave the rest of the layer absent. Report changes within the same availability state do not repaint;
the full follow-up observes the same state and cannot loop.

A source-backed feature layer drains its bounded cursor into an operation-owned list before painting
any of that layer; list/reference capacity counts against the query's conservative allocation budget.
Only successful exhaustion and cursor close publish the list to the renderer. A `SourceException`,
including close or cancellation, discards the list, skips that layer for the current pass, records its
terminal report, and allows later layers to render. Raster reads are already all-or-nothing and follow
the same skip/continue policy. `super.paintComponent` clears the current clip, and an availability
transition's full follow-up either clears prior pixels or restores a recovered layer across the whole
component. A failed layer therefore leaves no partial current or previous rendering after the bounded
follow-up pass.

Source warnings do not skip successful data. The report state commits during the pass, but listeners
run only after the child graphics is disposed. An unexpected source implementation/runtime failure or
symbol renderer failure retains the existing G2 behavior and aborts the whole pass; it is not converted
to a source report. If such a failure follows a queued report event, disposal and best-effort event
delivery still occur, with the original failure primary and notification failure suppressed.

#### Verification boundary

The G4-001 decision change runs the existing API/core tests and checks the sketches above; contract
implementations land only with the G4-003/G4-004 vertical slices. Their API tests cover sealed geometry,
source-wide duplicate IDs, exact feature counts, canonical attributes/schema projection, raster edge
orientation, the integer nearest formula, strict windows, result dimensions, buffer/builder ownership,
limit arithmetic, diagnostic bounds/report validation, and cancellation values.

Core/source tests cover every cursor state and slot release, partial iteration, early/double/source
close, cursor-close failure state/no-retry/source reuse, raster failure cleanup/reuse, reuse after
cancellation, already-cancelled zero-work behavior, 4,096-unit controlled-loop polling, opaque-stage
before/after checks, tighter-limit validation, stable order, warnings, terminal cleanup, and
metadata/results surviving close. Limit tests exercise one less than, exactly, and one greater than
every primitive/content/reference charge class, cumulative repeated allocations, independent
format/query/raster counters, and checked-arithmetic overflow. A concrete-image concurrency test
belongs to G6-004, not the base-source conformance suite.

AWT integration tests buffer-before-paint, skip one failed/cancelled layer while later layers render,
publish warnings, clear equal/clean status, defer report listeners until graphics disposal, preserve
runtime/render failure precedence, issue exactly one full follow-up in each availability direction,
reject a second owned-view attachment, distinguish remove/re-add from permanent close, and exercise
successful/failed replacement plus reverse-order aggregated close.
Architecture tests reject AWT, parser, codec, format, and external-adapter types from every source,
metadata, query, record, pixel, diagnostic, cancellation, and limit signature.

#### Ownership and HITL checkpoint

A source owns every handle it opens; the opener owns the source. Attaching it to a source-backed layer
borrows by default. G4-003/G4-004 expose named `borrowed(...)` and `owned(...)` binding factories rather
than a boolean. Factory validation occurs before transfer. A successful `owned` factory transfers
exclusive responsibility to the returned closeable binding; a failed factory leaves it with the
caller. If a later MapView attachment fails, the unattached binding still owns the source and the
caller closes that binding. An owned binding may be installed in at most one live view; a second
attachment is a lifecycle failure. Borrowed bindings never close their source. Returned records,
metadata, reports, and pixels contain no live handle.

G4-003 makes `MapView` explicitly `AutoCloseable`. `close()` is an idempotent, permanent EDT operation:
it marks the view closed, clears installed content/state, then closes installed owned bindings in
reverse prior layer order and reports every failure. The first `SourceException` remains primary and
later close failures are suppressed; report listeners drain before it is thrown. Subsequent close and
read-only report access remain valid, while content/viewport/tool/source mutation is
`IllegalStateException` and painting produces only the ordinary component background. Swing
`removeNotify()` remains a reversible detach and never masquerades as disposal or closes a source.
Examples/windows call `MapView.close()` explicitly from their permanent teardown path.

Layer/source replacement validates and snapshots the complete candidate first. Failure leaves the
installed view unchanged and does not acquire a candidate binding. Success commits the new snapshot
and repaint before closing removed owned bindings exactly once. A removed-source close failure is
recorded/notified and thrown after commit; the new snapshot remains installed, and no rollback can
resurrect a resource whose close was already attempted. A binding retained by identity in the new
snapshot is not closed.

The named maintainer checkpoint is **G4 source-contract approval**. Before G4-003 begins, the
maintainer records approval of: advance/current pull semantics; unchecked structured terminal
failures; sealed immutable geometry and source-wide IDs; the fixed one-cursor rule and source/child
close ownership; borrowed-by-default attachment and explicit `MapView.close()`; cancellation's
4,096-unit project-loop interval plus opaque-JDK checkpoints; the canonical attribute set; strict
raster-window rejection and exact nearest baseline; typed limit values/defaults/tightening; and the
diagnostic/location/report plus AWT observation shape. The compile sketches cover an in-memory feature
source, synthetic raster source, early cursor close, pre/mid-operation cancellation, one warning, one
terminal error, source-report delivery, owned/borrowed teardown, and values surviving source close. No
production format module is created by this decision.

### CRS boundary and projection hardening (G4-002)

#### Coordinate-reference values and tuple convention

Missing, unknown, and recognized coordinate reference systems are three different states. A missing
CRS remains `Optional<CrsMetadata>.empty()` in G4-001 metadata. Present metadata has this logical
shape; factories enforce the state invariants rather than exposing an invalid all-optional record
constructor:

```text
CrsMetadata
  recognized(
      CrsDefinition definition,
      Optional<String> declaredIdentifier,
      Optional<String> retainedDefinition)
  unknown(
      Optional<String> declaredIdentifier,
      Optional<String> retainedDefinition)

CrsDefinition(
    String canonicalIdentifier,
    CrsKind kind,
    CrsAxis xAxis,
    CrsAxis yAxis,
    Envelope coordinateDomain)

CrsAxis(CrsAxisMeaning meaning, CrsUnit unit)
CrsKind = GEOGRAPHIC | PROJECTED | UNKNOWN
CrsAxisMeaning = LONGITUDE | LATITUDE | EASTING | NORTHING
CrsUnit = DEGREE | METRE
```

A recognized definition is only `GEOGRAPHIC` or `PROJECTED`; `UNKNOWN` describes metadata with no
recognized definition, axes, unit, or coordinate domain. Recognized metadata derives its canonical
identifier, kind, axes, and domain from its definition. Unknown metadata must retain at least one
non-blank declared identifier or original definition. A declared identifier is exact, case-sensitive,
at most 256 UTF-16 characters, and need not be canonical. A retained definition is the exact input,
including case and whitespace, is non-blank when present, and is limited to 16,384 UTF-16 characters.
It is never trimmed, normalized, parsed by the value, or included raw in a diagnostic. Format readers
check their byte and character limits before construction and map an oversize value to
`CRS_RETAINED_DEFINITION_TOO_LONG`; direct invalid construction is an ordinary argument failure.

Definitions and metadata are immutable values with defensive optional/collection ownership. A
canonical identifier is a non-blank exact registry key of at most 256 characters. Axis/domain
validation is kind-specific: the Level 1 geographic definition uses longitude/degree then
latitude/degree, and the projected definition uses easting/metre then northing/metre. Domain bounds
are finite with positive spans. Additional axis/unit profiles require the evidence and contract review
in G10-007 rather than arbitrary strings in Level 1.

All public `Coordinate` and `Envelope` tuples use the library's explicit **x/y visualization
convention**. For the EPSG:4326 profile, x is longitude east and y is latitude north in degrees; for
EPSG:3857, x is easting and y is northing in metres. This preserves the existing map behavior and is
an explicit tuple normalization, not a claim that EPSG's authoritative axis ordinal for 4326 is
longitude first. The [EPSG 4326 definition](https://epsg.org/crs_4326/WGS-84.html) lists latitude then
longitude, while EPSG 3857 is x/y easting/northing in the
[EPSG 3857 definition](https://epsg.org/crs_3857/WGS-84-Pseudo-Mercator.html). The registry therefore
does not alias OGC CRS84 to EPSG:4326; an identifier or format profile with different tuple semantics
must normalize explicitly at its own boundary.

`CrsMetadata.equals` includes the declared/retained provenance, but transform compatibility does not.
The registry first validates that a recognized definition exactly equals its canonical registered
definition, including axes and domain, then compares canonical identifiers. Retaining a different
alias or WKT spelling therefore cannot create a false mismatch, while fabricating the same canonical
identifier with different semantics produces `CRS_DEFINITION_MISMATCH`. Two unknown definitions are
never transform-compatible merely because their retained text happens to match.

The existing `Envelope` invariant is strengthened so each finite max-minus-min span is also finite.
Its center uses an overflow-safe midpoint once that invariant holds. A union that would create an
unrepresentable span fails rather than returning an envelope whose later width or center is infinite.
Point and one-axis-degenerate envelopes remain valid.

#### Projection and directed-operation boundary

`Projection.id()` is removed during `0.x`: a target CRS identifier is not an operation identity, and
the ordered endpoint definitions already identify the Level 1 operation. The replacement contract is
explicit and reversible:

```text
Projection
  sourceCrs() -> CrsDefinition
  targetCrs() -> CrsDefinition
  sourceDomain() -> Envelope
  targetDomain() -> Envelope
  project(Coordinate source) -> Coordinate
  unproject(Coordinate target) -> Coordinate
  projectEnvelope(Envelope source) -> Envelope
  unprojectEnvelope(Envelope target) -> Envelope
```

Operation domains may be narrower than their CRS domains: EPSG:4326 includes the poles, while the
Web Mercator forward operation does not. Both operation domains must be contained by their endpoint
CRS domains. Coordinate and envelope methods are strict and required; there is no unsafe default that
projects four corners for an arbitrary consumer projection. A projection instance is immutable and
deterministic for equal input; it cannot consult mutable ambient state. An implementation must
validate the complete input against the applicable operation domain, validate every intermediate and result as
finite and within the target domain, and then construct the public value. Null and structurally
invalid arguments use the ordinary Java argument failures. A domain or numeric operation failure
throws an unchecked `CrsException` carrying one bounded `CrsProblem(code, message, context)` but no
source identity or raw definition. Its code, message, context count/key/value limits, ordering, and
character rules are exactly the G4-001 diagnostic limits, allowing lossless source-boundary mapping.
An unexpected implementation runtime failure is not relabeled.

Core exposes an immutable directional `CrsOperation`, obtained only from a registry, with:

```text
CrsOperation
  sourceCrs() / targetCrs()
  sourceDomain() / targetDomain()
  transform(Coordinate) -> Coordinate
  transformEnvelopeStrict(Envelope) -> Envelope
  transformQueryEnvelope(Envelope) -> QueryEnvelopeTransform

QueryEnvelopeTransform(
    QueryEnvelopeStatus status,
    Optional<Envelope> transformedEnvelope)

QueryEnvelopeStatus = COMPLETE | CLIPPED | OUTSIDE
```

The operation is a forward, inverse, or identity view over a validated projection; callers never
branch on direction. Strict envelope transforms are used for feature geometry, source extents, fit,
and raster placement. They reject any partially out-of-domain input. Query transforms first intersect
the complete input with the directional source domain using inclusive edges. A disjoint input returns
`OUTSIDE` and no envelope; a proper intersection returns `CLIPPED` and the transformed intersection;
an already-contained input returns `COMPLETE`. Touching at one edge or point is a valid degenerate
intersection, not outside. Clipping is therefore explicit query planning, never repair of a feature,
pixel coordinate, or direct projection call.

The two Level 1 operations are axis-separable and monotone over their accepted domains, so their
envelope extrema are exactly the transformed axis bounds. Identity copies the validated envelope;
Web Mercator transforms the two x bounds and two y bounds independently. No generic densifier,
sampling tolerance, longitude wrap, or antimeridian split is introduced. G10 projections must provide
their own conservative envelope rule rather than inheriting four-corner behavior accidentally.
`ProjectionEnvelopes` is removed once callers use the operation-owned methods.

#### Explicit immutable CRS registry

`CrsRegistry` is an instance-owned immutable core value with a consumed single-use builder:

```text
CrsRegistry.builder()
CrsRegistry.builderWithLevel1()
CrsRegistry.level1()

Builder.registerDefinition(CrsDefinition definition, List<String> exactAliases)
Builder.registerProjection(Projection projection)
Builder.build() -> CrsRegistry

CrsRegistry.resolve(String exactKey) -> CrsDefinition
CrsRegistry.operation(CrsDefinition source, CrsDefinition target) -> CrsOperation
```

`level1()` returns a fresh immutable registry; immutable built-in definition constants are not a
mutable registry. The builder defensively copies declaration order and is unusable after `build()`.
Canonical keys and aliases share one exact case-sensitive namespace with no trimming, case folding,
Unicode normalization, URI rewriting, or last-wins replacement. A collision is a failure even when
both entries would resolve to the same definition. Projection registration requires both exact
endpoint definitions already registered, distinct endpoint IDs, operation domains within the CRS
domains, and no prior forward or inverse ordered pair. One registered projection supplies its two
directed operations. The registry synthesizes the one canonical strict identity operation for every
registered definition; identity is selected by an equal source/target definition and is never modeled
as a fake `Projection`, registered, or overridden.

The registry resolves only an exact identity or one directly registered projection direction. It
does not search a graph, chain operations, select by numeric coordinate range, parse WKT, inspect a
filename, consult a database, load a service, scan the classpath, reflect, or invoke an optional
adapter. G10-007 must justify another direct projection or isolated adapter. G5-007's explicit PRJ
recognizers are separate bounded format logic that map only approved WKT profiles to one of these
canonical definitions; raw WKT strings do not become registry aliases.

The exact built-in keys are:

| Canonical key | Exact aliases |
| --- | --- |
| `EPSG:4326` | `urn:ogc:def:crs:EPSG::4326`, `http://www.opengis.net/def/crs/EPSG/0/4326` |
| `EPSG:3857` | `urn:ogc:def:crs:EPSG::3857`, `http://www.opengis.net/def/crs/EPSG/0/3857` |

`CRS:84`, `OGC:CRS84`, `WGS84`, `EPSG:900913`, `ESRI:102100`, HTTPS/case/whitespace variants, and WKT
spellings are not aliases. An unknown direct lookup fails with `CRS_REGISTRY_KEY_UNKNOWN`; it never
manufactures recognized metadata. A format can instead retain the input through
`CrsMetadata.unknown(...)` after its own bounded recognition attempt.

Registry/configuration failures are `CrsException`, not a fabricated `SourceException`: duplicate
canonical/alias keys use `CRS_REGISTRY_KEY_DUPLICATE`, duplicate directional pairs use
`CRS_REGISTRY_TRANSFORM_DUPLICATE`, an unregistered or mismatched endpoint uses
`CRS_DEFINITION_MISMATCH`, and an absent direct pair uses `CRS_TRANSFORM_UNAVAILABLE`. Context contains
only bounded exact keys, ordered endpoint IDs, and the conflicting declaration indexes. Two builders
and their built registries remain isolated, and a component never mutates a supplied registry.

#### Strict EPSG:4326 and Web Mercator behavior

The built-in definitions and projection use these exact closed domains:

| Boundary | Minimum | Maximum |
| --- | ---: | ---: |
| EPSG:4326 longitude | -180 degrees | 180 degrees |
| EPSG:4326 latitude | -90 degrees | 90 degrees |
| Web Mercator forward latitude | -85.0511287798066 degrees | 85.0511287798066 degrees |
| EPSG:3857 easting/northing | -20,037,508.342789244 m | 20,037,508.342789244 m |

The radius is exactly 6,378,137 metres and the projected world limit is the stored result of
`PI * radius`. Forward projection rejects longitude outside `[-180,180]` and latitude outside the
narrower Mercator domain; it neither wraps longitude nor clamps latitude. Inverse projection rejects
either projected ordinate outside the conventional square. EPSG:4326 identity accepts its full CRS
domain, including both poles, and EPSG:3857 identity accepts the full projected square.

Forward x is `radius * radians(longitude)` and forward y is
`radius * log(tan(PI / 4 + radians(latitude) / 2))`. Inverse longitude is
`degrees(easting / radius)` and inverse latitude is
`degrees(atan(sinh(northing / radius)))`. Exact accepted longitude/latitude and projected boundary
constants map to their exact corresponding stored boundaries; this edge special case avoids a
one-ULP formula escape and is numeric normalization, not clipping. Other calculated results outside
the target domain fail. Zero inputs/results are canonicalized to positive zero.

`Math.nextDown(minimum)` and `Math.nextUp(maximum)` fail at every domain edge. Poles fail only for the
Mercator projection, not 4326 identity. Ordinary forward/inverse geographic round trips are within
`1e-9` degree per axis, and ordinary inverse/forward projected round trips are within `1e-6` metre per
axis; exact domain edges are tested separately rather than hidden by those tolerances. Every accepted
result is finite and within its declared domain.

`MapViewport` remains entirely CRS-neutral, but its numeric invariant becomes strong enough to build
queries safely. Construction requires the half-width/half-height world spans and all four visible
world edges to be finite. Screen/world conversions require finite inputs and results. Pan, zoom, fit,
resize, and visible-envelope calculation validate multiplications, additions, scale, stable midpoint,
and resulting edges before returning a new value. An unrepresentable navigation/programmer input
fails before state mutation; it is not silently saturated or misreported as source data. The new
`visibleWorldEnvelope()` is the only query-bound source and cannot return a non-finite envelope.

#### Map, source, display, and raster boundaries

The three coordinate roles are explicit:

```text
legacy layer: map-coordinate CRS -> map-to-display operation -> world/display CRS -> MapViewport -> screen
source feature: source metadata CRS -> direct registry operation -> world/display CRS -> screen
pointer/tool: screen -> MapViewport -> display-to-map operation -> optional map coordinate
raster: source metadata CRS == world/display CRS -> MapViewport -> screen
```

The **map-coordinate CRS** is used by legacy `Layer` values and every present
`MapPointerEvent.mapCoordinate`/`MapToolEvent.mapCoordinate`. The **world/display CRS** is used by
`MapViewport`. A source-backed feature layer has its own fixed **source CRS** and transforms directly
to the display CRS; it is not routed through the map-coordinate CRS. No operation is inferred from a
coordinate range.

The canonical full view configuration is:

```text
MapView(
    CrsRegistry crsRegistry,
    CrsDefinition mapCrs,
    CrsDefinition displayCrs)
```

The constructor resolves and stores immutable map-to-display and display-to-map `CrsOperation`
snapshots. Equal map/display definitions select the registry's synthesized identity in both
directions, which is the sole identity-view path used by geographic/projected raster examples. Symbol
rendering composes orthogonally: once G2-005 is present, the corresponding four-argument overload adds
`SymbolRendererRegistry`, and this three-argument overload uses its built-in value. G4's coordinate
configuration therefore does not introduce a task dependency on G2.

`MapView(Projection)` remains the `0.x` convenience constructor for the existing non-identity first
slice; G2-005 adds the parallel symbol-registry overload when present. They require distinct exact
endpoints, build a private registry containing those definitions and the explicitly supplied
projection, then delegate to the canonical constructor. They do not accept or manufacture an identity
projection. The ambiguous
`projection()` accessor is removed with `Projection.id()`; `mapCrs()` and `displayCrs()` replace it.
Internal rendering/context snapshots use the resolved directional operation, so the full constructor
never has to compare an arbitrary projection object with a synthesized identity. G4-003 adds
source-backed bindings against the view registry without changing toolkit-neutral source contracts.

`MapView.screenToMap`, `MapView.mapToScreen`, the matching `MapToolContext` methods, and the inverse
conversion used to construct pointer/tool events return `Optional<Coordinate>`. The value is empty
when the finite viewport world sample lies outside the display-to-map source domain or a finite
screen/map result is not representable. The map-to-screen direction is symmetric. A known domain miss
is not a source diagnostic
and does not emit repeated warnings as the pointer moves over ordinary blank margin; direct
`CrsOperation` calls remain strict for callers that require the detailed failure. Unexpected
projection failures still propagate. Map and display definitions are exposed by the tool context so
G3-004 can choose a distance strategy explicitly.

Every screen-space event still reaches the tool/router with its finite screen coordinates and optional
map coordinate. Passed `MOVE`/`CLICK` events reach compatibility observers with the same optional
value. Capture, release, cancellation, hover clearing, and default pan/zoom therefore continue outside
the CRS domain; no coordinate is clamped to a world edge and no event is silently suppressed. A tool
that requires a map coordinate does not add/update a coordinate-dependent value while it is empty but
must still handle lifecycle events.

Binding validation resolves both source-to-display rendering and display-to-source query operations
before the view acquires an owned feature source. Missing metadata fails with `CRS_METADATA_MISSING`;
unknown metadata with `CRS_DEFINITION_UNKNOWN`; a canonical definition unequal to the registered one
with `CRS_DEFINITION_MISMATCH`; and a recognized pair without a direct operation with
`CRS_TRANSFORM_UNAVAILABLE`. Direct callers may still query a source in its own coordinate system
without any CRS; only a view/map operation requires resolution. Retained definition text is
inspectable metadata but never transform permission.

G4-003 derives each feature query envelope by passing the finite visible display envelope to the
display-to-source operation's query transform. `COMPLETE` queries normally. `CLIPPED` queries the
transformed intersection and publishes `CRS_QUERY_ENVELOPE_CLIPPED`; `OUTSIDE` opens no cursor and
publishes `CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN`. Both are successful warnings, not terminal failures.
Feature extents, fit, and every rendered coordinate use strict source-to-display operations. A source
coordinate or extent outside the operation domain fails that layer operation; it is never projected
from a clamped substitute.

Level 1 does not pretend that a geographic raster becomes Web Mercator by stretching its transformed
corner envelope into one Java2D rectangle. That is a nonlinear raster warp, not the point projection
or affine placement provided by G4/G6. A renderable raster must have bounds and a recognized source CRS
whose canonical definition equals the display CRS. EPSG:4326 rasters are exercised in a 4326 identity
view and EPSG:3857 rasters in a 3857 identity display. A different recognized source/display pair uses
`CRS_RASTER_WARP_UNSUPPORTED` even when a point projection exists; missing/unknown metadata uses the
same failures as feature binding. World-file rotation/shear remains valid within one CRS. Raster
warping requires a later explicit bounded vertical slice and is not implied by adding a point
projection in G10.

This refines two G4-004 phrases before implementation: its geographic/projected placement cases use
separate matching-CRS views, and source windows remain strictly rejected as G4-001 already decided;
only MapView's visible query envelope can be clipped. G6-002 follows the same matching-display rule.
A world file supplies an affine placement but no CRS by itself.

#### Stable diagnostics and verification boundary

Core translates a known `CrsProblem` at a source boundary into exactly one G4-001 diagnostic, adding
the caller's source ID and optional bounded location while preserving the stable code/context. It does
not translate nulls, invalid application construction, or unexpected runtime failures. CRS contexts
use only applicable members of `operation`, `sourceCrs`, `targetCrs`, `expectedCrs`, `actualCrs`,
`axis`, `value`, `minimum`, and `maximum`; numeric values use locale-independent `Double.toString`.
Retained definitions and unbounded source text never appear.

The stable source codes are `CRS_METADATA_MISSING`, `CRS_DEFINITION_UNKNOWN`,
`CRS_DEFINITION_MISMATCH`, `CRS_TRANSFORM_UNAVAILABLE`, `CRS_COORDINATE_OUT_OF_DOMAIN`,
`CRS_ENVELOPE_OUT_OF_DOMAIN`, `CRS_TRANSFORM_NON_FINITE`, and
`CRS_RASTER_WARP_UNSUPPORTED`. Successful clipped/outside query warnings use
`CRS_QUERY_ENVELOPE_CLIPPED` and `CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN`. Registry and retention codes are
the configuration/value codes named above; a format source that detects retained text over the bound
uses `CRS_RETAINED_DEFINITION_TOO_LONG` as its opening error before constructing metadata. A terminal
projection problem follows G4-001's layer-skip, report, availability, cleanup, and later-layer
rendering behavior.

The G4-002 implementation runs API/core tests. API tests cover every metadata state and invariant,
exact retained provenance, maximum/max-plus-one bounds, recognized equality versus compatibility,
axis/unit/domain profiles, finite envelope spans, stable midpoints, and the bounded `CrsProblem`/
`CrsException` shape. Registry tests cover both canonical keys and every listed alias, exact
case/whitespace rejection, unknown keys, all duplicate/collision cases, endpoint validation,
forward/inverse/identity resolution, direct-only behavior, builder consumption, and isolation.

Projection tests cover ordinary and exact-edge values, each adjacent out-of-domain ULP, poles,
non-wrapped longitude, huge finite projected input, positive zero, both round-trip tolerances, faulty
non-finite implementations, strict ordinary/point/one-axis/full-world envelopes, partial/whole domain
failures, and query `COMPLETE`/`CLIPPED`/`OUTSIDE` including a touching edge. Viewport tests cover
finite visible envelopes plus overflow in construction, conversion, pan, zoom, fit, resize, and
midpoint/span arithmetic. Diagnostic tests assert exact code/context and prove raw retained text is
absent. Architecture tests retain API/core JDK-only purity and reject static mutable CRS registries,
discovery mechanisms, AWT/parser/adapter types, and an unsafe default envelope projection.

AWT compatibility integration in G4-002 covers canonical 4326 and 3857 identity-view construction
without a `Projection`, retained non-identity projection convenience construction, exact map/display
accessors, and present map coordinates inside the inverse domain. It also starts from the existing
800-by-600, 100,000-units-per-pixel viewport and routes moved/clicked pointer samples plus legacy
pan/wheel navigation at points outside each display-domain edge with an empty map coordinate. Those
samples retain finite screen coordinates, continue passed screen-space navigation, and reach
compatibility observers without a source report, clamped coordinate, or callback failure. G3-001
later applies the same optional conversion contract to its new press/drag/release/cancel tool events;
G4-003 and G4-004 extend the evidence to feature- and raster-source bindings.

Strict inverse domains cannot be introduced while leaving the current always-present pointer value in
AWT. G4-002 therefore owns the affected existing `MapPointerEvent`, `MapView`
constructors/conversions, AWT tests, and AWT test validation. G3-001 depends on that completed boundary
and owns its not-yet-created tool/context contracts. This is necessary integration work, not a feature
expansion into G4-003 source rendering.

Arbitrary WKT, authority-axis negotiation, datum shifts, operation chaining, non-monotonic
densification, antimeridian splitting, additional projections/units, raster warping, and PROJ remain
out of scope. Native behavior remains ordinary JDK math and explicit object construction with no
resource/database discovery.

### Feature source query and rendering slice (G4-003)

#### Feature-half contract delivery

G4-003 is the first production consumer of the G4-001 decision. It implements the common source
identity, diagnostic, exception, cancellation, and limit values plus the feature metadata, schema,
record, query, source, and cursor contracts exactly as approved above. It also applies the canonical
attribute rules to existing `Feature` construction. Raster-specific metadata, windows, requests,
pixels, reads, and `RasterSource` remain G4-004 work; that task reuses, rather than duplicates, the
common values delivered here. No I/O module is created by this in-memory slice.

The reusable core boundary contains one public, final, operation-local `FeatureQueryAccounting`
helper. It is externally serialized, has no global state, and is constructed from the exact source ID
and already-resolved effective `FeatureQueryLimits`. Its only state-changing operations are:

```text
recordExamined()
recordReturned(FeatureRecord record, int retainedReferenceSlots,
               CancellationToken cancellation)
```

Both methods perform checked prospective arithmetic and throw the already specified
`SOURCE_LIMIT_EXCEEDED` failure before accepting the charge. `recordReturned` derives coordinate,
offset, attribute, text, and logical-payload charges by exhaustive dispatch over the sealed geometry
and canonical attribute values; it uses no reflection, object-size estimate, or caller-supplied byte
claim. While traversing the record, it checks the non-null token before work, at no more than 4,096
primitive/attribute units, and immediately before accepting the charge. Cancellation therefore fails
with `SOURCE_CANCELLED` before the record or staging reference is retained. `retainedReferenceSlots`
is non-negative and charges eight bytes each. A source cursor uses
zero because it publishes one current value without retaining a result container. MapView uses a
separate accounting instance with one slot for every record it stages, thereby independently enforcing
`sum(record logical payload) + 8 * staged record count` without exposing a mutable source counter.
G5 format modules reuse the same helper after their independent parser-allocation accounting.

Every offset entry and repeated ring-closing coordinate is charged. The helper retains neither token
nor record; cursor implementations still own checkpoints around filtering, projection, I/O, and
publication. It is a correctness utility, not a profiler, allocator, query executor, or generic
budget framework.

#### Packed multipart geometry

`Geometry` becomes sealed to exactly the three existing singular values plus
`MultiPointGeometry`, `MultiLineStringGeometry`, and `MultiPolygonGeometry`. These remain geometry
values rather than feature collections; empty geometry and null format records are not manufactured
as geometry instances.

The packed public shapes are:

```text
MultiPointGeometry(CoordinateSequence coordinates)

MultiLineStringGeometry
  of(CoordinateSequence coordinates, int[] partOffsets)
  ofParts(List<CoordinateSequence> parts)
  coordinates() -> CoordinateSequence
  partCount() -> int
  partOffset(int fenceIndex) -> int
  partOffsets() -> defensive int[]

MultiPolygonGeometry
  of(CoordinateSequence coordinates,
     int[] ringOffsets,
     int[] polygonRingOffsets)
  ofPolygons(List<PolygonGeometry> polygons)
  coordinates() -> CoordinateSequence
  ringCount() / polygonCount() -> int
  ringOffset(int fenceIndex) / polygonRingOffset(int fenceIndex) -> int
  ringOffsets() / polygonRingOffsets() -> defensive int[]
```

Offsets are fencepost indexes, not byte offsets: line part and polygon ring offsets index coordinate
pairs, while polygon-ring offsets index rings. Each array begins at zero, is strictly increasing, and
ends at the exact referenced count. A multiline has at least one part and two coordinates per part. A
multipolygon has at least one polygon and one ring per polygon; within each polygon the first ring is
the exterior and later rings are holes. Every ring has at least four coordinates and exact first/last
closure. The packed factory clones caller arrays before validating the owned copies. Natural-list
factories check aggregate integer arithmetic before allocation and flatten in declaration order.

All values retain primitive indexed access, immutable value equality, defensive array copies, and one
precomputed envelope over every coordinate, including hole coordinates. They preserve point, part,
polygon, and ring order. Orientation, containment, intersection, island inference, repair, and format
ring classification remain G5 responsibilities. Independently declared multipolygon components paint
as separate even-odd exterior-plus-hole units; overlap may therefore composite twice and is not
silently unioned.

Role mapping follows component type: multipoint is `MARKER`, multiline is `LINE`, and multipolygon is
`FILL`. Marker leaves visit point coordinates in order. Line leaves visit parts without bridging and
apply start/end markers independently to every part. Fill leaves visit polygons in order and suppress
line endpoints on every exterior and hole ring. Composite symbols remain child-major across all
components, so all casing components paint before all inner-line components. Hit traversal reverses
child and component order but emits at most one `(layerId, featureId)` hit for the record. Logical
paint presence is the union of component results. The G1 convenience label remains single-point-only;
G4 does not invent a multipoint label anchor.

#### Immutable in-memory feature source

Core supplies the one concrete source needed for this vertical slice:

```text
InMemoryFeatureSource.open(
    SourceIdentity identity,
    List<FeatureRecord> records,
    Optional<AttributeSchema> schema,
    Optional<CrsMetadata> crs,
    FeatureSourceLimits limits) -> InMemoryFeatureSource

InMemoryFeatureSource.open(SourceIdentity identity,
                           List<FeatureRecord> records)
```

The convenience uses absent schema/CRS and the source-listed Level 1 limits. `open` snapshots the
ordered immutable record references, validates a known schema against every record, rejects a duplicate
ID before returning a source, computes exact count and union extent once, and exposes empty opening
diagnostics. A duplicate is the terminal opening failure `SOURCE_DUPLICATE_FEATURE_ID` with exactly
zero-based `firstIndex` and `duplicateIndex` context. The unbounded-but-valid exact feature ID is not
copied into bounded diagnostic context or message; the caller already owns the indexed records. Other
invalid direct schema/value construction is an ordinary field-naming application failure, not
simulated malformed format input.

The source implements the complete one-live-cursor and close state machine from G4-001 even though it
owns no external handle. It releases its cursor slot on exhaustion, failure, cancellation, early
close, or source close exactly once. Returned metadata, records, and reports survive close. Production
in-memory close cannot fail; explicit conformance fixtures exercise cleanup failure without adding a
configurable fake-failure switch to the source.

Before acquiring the cursor slot, `openCursor` checks source/query lifecycle, an already-cancelled
token, tighter-limit validity, and every `ONLY` field against a known schema. Its cursor linearly scans
the immutable snapshot, calls `recordExamined` before each envelope decision, uses inclusive geometry-
envelope intersection, projects attributes to `ALL`, `NONE`, or requested order, then calls
`recordReturned` before publishing the projected record. Dynamic schemas may omit requested fields as
approved; known schemas cannot. Source order is unchanged. The implementation polls cancellation
between records, within its controlled coordinate/attribute loops at the 4,096-unit interval, and
immediately before publication. G7 may replace the scan, but not ordering, accounting, query, or cursor
semantics.

#### One explicit AWT layer binding

A lazy source is not an implementation of the existing `Layer`: `Layer.features()` means an already
available snapshot and must never hide a cursor query, I/O, cache, or viewport dependency. AWT instead
adds one final tagged host object:

```text
MapLayerBinding implements AutoCloseable
  snapshot(Layer layer) -> MapLayerBinding
  borrowedFeature(String layerId, String name, FeatureSource source,
                  MarkerSymbol marker, LineSymbol line, FillSymbol fill)
  ownedFeature(String layerId, String name, FeatureSource source,
               MarkerSymbol marker, LineSymbol line, FillSymbol fill)
  id() / name()
  cancelCurrentOperation() -> boolean
  isClosed() -> boolean
  close()

MapView
  setLayerBindings(List<MapLayerBinding> bindings)
  layerBindings() -> immutable ordered List<MapLayerBinding>
```

Direct role parameters avoid a second marker/line/fill bundle beside G3's semantically distinct
overlay bundle. Factories validate exact IDs/names, source openness, non-null role-correct immutable
symbols, and factory-local state before ownership transfer. View attachment later resolves metadata
and renderer/CRS compatibility because those depend on that view. G4-004 adds working raster factory
variants to this same closed host type only when raster behavior exists; consumers cannot implement an
unrenderable binding variant.

`MapView.setLayers` maps each existing `Layer` through `snapshot` and delegates to the canonical
setter. Existing set/get behavior is unchanged for that path. `layers()` returns the immutable legacy
snapshot entries, in relative order, when mixed bindings were installed; new code uses
`layerBindings()` for the complete ordered stack. Layer IDs are unique across every binding kind.
Feature IDs remain source-local. Candidate validation rejects one source instance appearing more than
once in a view, avoiding one-cursor and report ambiguity.

Every binding instance is attachable to at most one live MapView at a time. Candidate list, binding
state, unique IDs/source identities, source metadata, complete recursive renderer availability for all
three role symbols, and both direct CRS operations are validated before any new claim. Claims are then
acquired transactionally; failure releases only claims acquired for that candidate and leaves the old
view unchanged. A binding retained by object identity is neither reclaimed nor closed. Applications
that show one borrowed source in two views construct two borrowed bindings and externally serialize
the source as its contract requires.

Successful replacement commits the new immutable list, reconciles interaction/report state, and
requests repaint before releasing removed snapshot/borrowed claims and closing removed owned bindings
in reverse old order. Borrowed/snapshot removal never closes its underlying object and permits later
reattachment. Removed owned bindings become permanently closed and close their source once. A close
failure is reported and thrown after commit without rollback. Calling `close()` on an attached binding
is a lifecycle error; an unattached owned binding remains caller-closeable after failed attachment.

For a source operation, a binding atomically publishes one fresh `CancellationSource` before query
planning and clears that exact instance in `finally`. `cancelCurrentOperation()` is the sole method in
this AWT surface permitted from another thread: it returns true and idempotently signals the currently
published token, or false when no operation is active. It never cancels a future operation and mutates
no Swing state. Snapshot bindings always return false. All other binding/view lifecycle follows the
EDT and external-serialization rules. This is a bounded cancellation handoff, not an executor,
background loader, callback, or public scheduling framework.

G4-003 makes MapView's approved `AutoCloseable` behavior concrete. Permanent idempotent close releases
borrowed/snapshot claims, closes owned bindings in reverse order, clears tool/interaction/report state,
and leaves background-only painting. `removeNotify` remains reversible and does none of that work.

#### One query transaction per source and operation

The private `ViewContentSnapshot` becomes an ordered tagged list of legacy snapshots and successfully
staged source snapshots. It never constructs a public `Feature` from a `FeatureRecord`. A source entry
retains the original immutable record, binding role symbols, and resolved source/display operations
only for that MapView operation. The binding list, viewport, registry, CRS operations, selection, and
hover state are captured before traversal.

Paint, public hit test, hover-producing move, and click selection each query a source binding at most
once and reuse the result for every later step in that same operation. A consumed tool event that does
not require a hit opens no cursor. For each required source entry, MapView performs:

```text
visible display envelope
  -> display-to-source transformQueryEnvelope
  -> FeatureQuery(transformed source bounds, AttributeSelection.NONE, no tighter limits)
```

`COMPLETE` opens normally. `CLIPPED` records `CRS_QUERY_ENVELOPE_CLIPPED` before opening on the
intersection. `OUTSIDE` opens no cursor and publishes an empty successful/available layer with
`CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN`. This is geometry-envelope visibility. A symbol anchored just
outside the viewport is not queried merely because an offset or wide screen mark could bleed inward;
G4 adds no guessed custom-renderer padding or full-source fallback. G7 may add evidence-backed query
padding together with its index/query work without changing the source contract.

MapView requests no attributes, opens exactly one cursor for that source in that operation, drains
records in source order, and closes the cursor before using the list. Before appending each record it
uses its independent accounting instance with one retained slot. Query-planning warnings precede
cursor warnings; a bounded report merge preserves encounter order, the effective warning cap, and
omitted count. Only successful exhaustion, close, accounting, and a final cancellation checkpoint make
the staged list available.

Before any pixels or hits from that source layer are evaluated, MapView preflights every staged
coordinate through the strict source-to-display operation without retaining a projected cache. The
operation contract is immutable and deterministic for equal input. A known CRS failure is translated
at this source boundary, discards the whole source entry, and leaves later bindings available. A
successful renderer may repeat the transform; avoiding that linear work requires G7 evidence rather
than a premature projected-geometry cache.

Cancellation, `SourceException`, cursor-close failure, staging-limit failure, or known CRS failure
discards that whole source entry, records its terminal report, marks the layer unavailable, and lets
later layers continue. No partial source pixels, hits, hover, selection overlay, or retained query list
escape. Unexpected source runtime failures and symbol-renderer failures keep the existing whole-pass
failure behavior. Report state commits with the operation; paint callbacks wait until child graphics
disposal, while non-paint callbacks drain at transaction end.

The complete base stack paints in binding order. Source records paint in cursor order. A raw record is
dispatched with the role symbol selected from its geometry and original source-to-display operation;
no parser style, synthetic `Feature`, or mutated record is involved. Hover, selection, and measurement
remain later global passes. Hit traversal reverses binding and record order. Source overlays reuse the
original staged geometry and source operation, do not hit-test, and require `PRESENT` source paint just
like legacy overlays.

#### Interaction, fitting, and report refinements

Viewport-bounded absence is not source-wide absence. A clicked source record uses the binding ID and
record ID normally. Once selected, that pair persists while the same binding instance remains
installed even when the record is offscreen or a source query fails; only a currently staged matching
record can paint its overlay. Removing or replacing the binding clears that source selection, even if
the replacement reuses both strings. Legacy same-layer/same-feature reconciliation remains unchanged.

Programmatic `setSelection` for a source binding validates the installed binding ID and accepts the
feature ID as the caller's source-local assertion; it performs no hidden full scan and paints only if a
later bounded query returns that exact ID. Legacy programmatic selection still requires exact snapshot
existence. Hover is never asserted: it reflects only the current successful query/hit snapshot, and a
failed source is skipped so a lower visible record may win. Recovery does not synthesize hover without
a later passed pointer move.

G3-001's proposed `MapToolContext.layers()` is removed before implementation. A `List<Layer>` cannot
represent lazy or raster content without hidden I/O, while a legacy-only subset would be misleading;
none of the designed Level 1 tools consumes it. A future editing slice may add a bounded content-query
service when it has an actual consumer. The context retains exact map/display CRS, coordinate
conversion, and repaint capabilities.

`fitToData` snapshots the same binding stack but opens no cursor. Legacy extents transform strictly
from map to display; present source metadata extents transform strictly from source to display.
Missing extents are ignored. A known source extent/CRS failure updates that source report and skips its
extent while other valid bindings still contribute. With no valid extent, fit remains a no-op. Report
and availability transitions follow the same delivery/repaint rules as query operations.

Opening warnings become a source binding's initial report only after successful attachment. A later
operation replaces them with its bounded warnings/terminal report or clears them after clean success.
`sourceReports()` stays ordered by the complete binding stack but contains entries only for source
bindings. An `OUTSIDE` operation is available empty content with a warning. Every later operation
retries an unavailable source, so recovery can restore it without a separate reset API.

#### Verification boundary and task corrections

API tests cover the complete common/feature contract set from G4-001, sealed permitted geometry types,
packed/list multipart factories, offset fences, cardinality, exact closure, order, defensive copies,
value equality, envelopes including holes, canonical attributes, and every query/diagnostic/limit
invariant. Core tests cover shared accounting at every boundary/overflow, in-memory open metadata,
duplicate IDs, schema validation/projection, inclusive filtering, stable order, one-cursor state,
early/exhausted/failed/cancelled/source close, source reuse, tighter limits, and cancellation polling.

AWT tests cover transactional mixed legacy/source attachment, role/registry/CRS validation, duplicate
IDs/source instance, claim rollback, borrowed reuse, owned reverse close, failed close after commit,
per-operation cancellation, `MapView.close`, remove/re-add, opening/query/terminal report ordering,
availability follow-up, extent fit, and no cursor for missing extent or outside query. Operation tests
assert one cursor per source per paint/hit/move/click transaction, cursor close before delivery,
independent staging charges, all-or-nothing CRS preflight, later-layer continuation, no retained
viewport cache, source selection persistence, programmatic assertion, hover recovery, mixed reverse
hit order, and source-before-hover-before-selection-before-measurement painting. Offscreen-image tests
exercise every singular/multipart family, holes, component and composite order, endpoint-per-part,
single-record hit identity, overlay presence, and the documented symbol-bleed boundary.

Architecture tests keep all common/source/geometry/accounting contracts JDK-only, keep bindings and
MapView composition in AWT, and reject parser/format/codec/external types, executors, prefetch,
discovery, retained query caches, or an empty format module. The implementation command includes the
architecture-test module, followed by the normal quality gate and whitespace check. This task is at
the upper end of the intended size because it delivers the complete feature half of the already
approved G4 decision through one real render/hit/lifecycle slice; splitting it into class cards would
delay observable behavior without creating a safer ownership boundary.
