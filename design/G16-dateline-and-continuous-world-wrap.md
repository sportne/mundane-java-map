# G16 — Dateline and continuous horizontal world wrap

## Purpose

G16 adds explicitly configured, bounded horizontal repetition for global maps. A user can drag or
zoom east or west across the Web Mercator dateline without encountering an empty edge, while source
coordinates, CRS operations, feature identities, input limits, and local projected layers remain
strict and unambiguous.

The first observable slice is the existing G15 Natural Earth/live-track viewer. The reusable result
then extends ordinary `MapView` feature and raster bindings, interaction, vector export, performance
evidence, and native-safe wrap math. This is not a request for arbitrary topology repair or a global
GIS abstraction.

## Current boundary

`MapViewport` accepts any finite projected center, so navigation can already move beyond the
canonical Web Mercator square. Content does not repeat: `WebMercatorProjection` deliberately accepts
longitude only in `[-180, 180]`, source queries clip to the registered CRS domain, and renderers paint
only the canonical geometry. G15 tracks and prepared Natural Earth land likewise occupy only the
base projected world. The result is empty space after a user crosses either horizontal edge.

This strictness is correct and remains. Projection is a coordinate operation; repetition is a
display policy.

## Approved profile

### Explicit opt-in

Wrapping is disabled by default. One immutable horizontal-wrap value defines:

- the finite canonical X minimum and exclusive maximum;
- the positive period derived from those bounds;
- a configured maximum number of visible copies at or below a hard library ceiling; and
- the supported absolute world-copy-index bound used to retain numeric precision and checked
  arithmetic.

`MapView` receives an explicit optional display-wrap configuration. Each `MapLayerBinding` separately
chooses `NONE` or `REPEAT_X`; a local source never repeats merely because its CRS is EPSG:3857. The
Level 2 convenience profile uses `[-WebMercatorProjection.WORLD_LIMIT,
WebMercatorProjection.WORLD_LIMIT]`, whose period is approximately 40,075,016.69 metres.

The public immutable value is `io.github.mundanej.map.core.HorizontalWrap`. Its public constructor
accepts `canonicalMinimumX`, `canonicalMaximumX`, `maximumVisibleCopies`, and
`maximumAbsoluteCopyIndex`; `webMercator()` returns the approved defaults. Construction requires
finite strictly ordered bounds, a finite positive period, `maximumVisibleCopies` in `1..64`, and
`maximumAbsoluteCopyIndex` in `1..1_048_576`. The Web Mercator defaults are the existing strict world
bounds, eight visible copies, and an absolute copy index of 1,048,576.

The shared public core boundary is deliberately small:

```text
HorizontalWrap
  canonicalize(double displayX) -> WrappedX
  translate(double canonicalX, long copyIndex) -> double
  plan(double displayMinimumX, double displayMaximumX,
       double worldUnitsPerPixel) -> HorizontalWrapPlan
  canonicalTileColumn(long displayColumn, long matrixWidth) -> long

WrappedX(double canonicalX, long copyIndex)
HorizontalInterval(double minimumX, double maximumX) // [minimum, maximum)
HorizontalWrapPlan(List<HorizontalInterval> canonicalIntervals,
                   long minimumVisibleCopyIndex,
                   long maximumVisibleCopyIndex,
                   boolean fullWorld)
HorizontalWrapProblem(String code, Map<String, String> context)
HorizontalWrapException extends RuntimeException
  problem() -> HorizontalWrapProblem
```

These types are immutable, public, and live in core so AWT, examples, and later tile adapters share
one implementation. Lists/maps are defensively copied and ordered. Public methods reject null,
non-finite, reversed, non-positive-scale, non-positive-matrix, and non-canonical inputs with ordinary
argument failures. Checked arithmetic, copy, visible-copy, and precision outcomes throw
`HorizontalWrapException`; AWT translates its stable problem code/context into the layer diagnostic.
The plan represents visible copies as one inclusive contiguous index range rather than allocating a
copy list, and contains one or two half-open canonical intervals (one full interval when
`fullWorld=true`).

`MapView` adds `horizontalWrap()`, `setHorizontalWrap(HorizontalWrap)`, and
`clearHorizontalWrap()`. Existing constructors and a newly constructed view remain non-wrapped.
Clearing is rejected without state change while any attached binding is `REPEAT_X`. Replacement is
transactional: the new profile must match the display CRS and validate the current viewport and every
attached repeating binding, including raster compatibility, before current work is cancelled and the
profile, caches, hover, and repaint state change. It never detaches a binding or changes its mode.
`HorizontalWrapMode` lives in AWT with the closed values `NONE` and `REPEAT_X`.
`MapLayerBinding.horizontalWrapMode()` reports the mode and
`setHorizontalWrapMode(HorizontalWrapMode)` may change it only while the binding is open and
unattached. The default is `NONE`; this avoids factory-overload growth and prevents copying an owned
binding merely to change presentation. Snapshot, feature, editable-feature, and raster bindings may
opt in. Elevation bindings reject `REPEAT_X` until a separate terrain-seam profile is approved.

Attaching or retaining a repeating binding without a view profile is a caller configuration error.
The Web Mercator convenience profile requires the view's exact registered EPSG:3857 display CRS. No
classpath discovery, automatic CRS/extent inference, mutable global policy, or `MapViewport` record
change is permitted.

### Canonical coordinates and continuous navigation

Projection inputs and source records remain canonical. A viewport center may move continuously in an
unbounded-looking display X coordinate. Wrap planning decomposes a finite display X into:

```text
displayX = canonicalX + copyIndex * period
```

with checked arithmetic and one canonical half-open interval. The exact upper seam canonicalizes to
the lower seam in the next copy, eliminating two identities for one meridian. The planner uses a
checked `long` copy index and rejects values beyond the approved precision bound rather than
silently losing low-order pixels.

`MapViewport` remains source-compatible. G16 does not add a field to its public record. The approved
copy-index bound is paired with a dynamic precision check: every translated visible edge must have
an ULP no larger than one quarter of `worldUnitsPerPixel`. Programmatic viewports beyond either bound
produce one stable structured layer diagnostic; interaction does not pan into an unsupported state.
The absolute Web Mercator default retains that precision through zoom 22 at its extreme copy.

Y never repeats. Web Mercator latitude and northing limits remain strict.

### Wrapped query planning

The visible display envelope is mapped onto unique canonical X intervals:

- a viewport narrower than one period produces one interval, or two intervals when it crosses a
  seam;
- a viewport spanning at least one complete period queries the canonical source extent once; and
- multiple visible copies reuse the same logical query result rather than reopening a cursor for
  every copy.

`FeatureQuery` and `Envelope` stay non-wrapping. The AWT binding layer owns this small query plan,
opens bounded cursors sequentially, merges reports deterministically, and deduplicates records by
stable feature ID. Conflicting duplicate IDs retain the existing source-contract failure behavior.
A plan contains at most two unique canonical intervals; a span of at least one period collapses to
one full-world query. Split queries consume one shared `FeatureQueryLimits` budget. Logical paint
order is layer order, source record order, then ascending visible copy index.

Planning checks cancellation and applies existing query accounting across the aggregate operation.
Copy, interval, feature, coordinate, and allocation limits are prospective. Exceeding any limit
produces a stable diagnostic and no partial repeated layer.

### Repeated rendering

One canonical record may have zero or more visual instances. The renderer selects every integer copy
whose translated envelope intersects the paint clip. It translates X by `copyIndex * period` before
screen projection and retains ordinary symbol units, map-relative rotation, opacity, label metrics,
and layer ordering.

Feature identity does not include the copy index. Selection, attributes, edits, and source reports
refer to the one logical feature. Copy index is operation-local presentation state and never leaks
into `FeatureRecord`, source attributes, persisted workspaces, or format adapters.

The G15 static-map backing store follows the same rule: its overscan viewport renders every
intersecting Natural Earth and track copy. Cached images remain in continuous display coordinates,
so covered pans still require only an affine composite. G15 stays example-local; its first proof does
not create a second generic cache.

### Dateline-crossing vector geometry

Repetition alone is insufficient for a geographic segment from `170` to `-170` degrees. For an
explicitly wrapped recognized geographic binding, adjacent longitudes are unwrapped to the shortest
finite delta normalized to `[-180, 180)`; an exact half-period tie therefore takes the negative,
westward path. A crossing is split at the
canonical seam before projection. Inserted seam coordinates interpolate all supported two-dimensional
geometry ordinates exactly enough to retain closure and finite projection inputs.

Lines become ordered multipart lines as needed. Polygon rings are unwrapped independently, clipped
against shifted canonical windows, closed, and packed into valid polygon parts. Hole fragments stay
associated with their original polygon and are retained only in a resulting exterior by deterministic
containment. Empty fragments are removed. Invalid input, excessive crossings, ambiguous containment,
or inability to preserve a valid ring rejects the complete record with one stable diagnostic; no
partial polygon, topology repair, or heuristic reassignment is attempted. Seam work permits at most
4,096 inserted crossings per logical geometry and remains subject to existing part/coordinate limits.

Literal non-wrapping format semantics remain unchanged unless the application explicitly opts the
binding into this geographic shortest-path profile. Already projected sources may repeat copies but
are not automatically rewritten across the seam because their intended path cannot be inferred.
GeoJSON's existing literal dateline contract therefore remains honest.

### Interaction and editing

Hit testing evaluates visual instances in reverse logical paint order and returns the existing
logical feature ID. The selected visual copy is retained only for the current hover/gesture so
overlays appear at the pointer's copy; persistent selection is logical and paints on all visible
copies.

Pointer coordinates are canonicalized before inverse CRS operations and source/edit commands. A
point dragged across the dateline remains one canonical feature. Undo/redo stores the same immutable
logical edit commands as today, not presentation-copy changes. Snapping considers visible repeated
instances but resolves to canonical target coordinates and deduplicates the logical target.

Measurement keeps its existing antimeridian-normalized geographic distance strategy. Its display
path follows the pointer's continuous copy, while committed geographic coordinates remain canonical.
Zoom anchors use continuous display coordinates so crossing a seam does not move the point beneath
the cursor.

### Raster and tile behavior

A raster repeats only through an explicitly repeating binding. The source is read in its canonical
extent; translated visual copies reuse the same detached decoded/request result where request keys
are identical. Global PNG/JPEG/world-file rasters must use zero rotation/shear and cover the declared
canonical period. Each expected horizontal edge may differ by at most the larger of eight ULPs at
that edge or `period * 1e-12`; vertical bounds remain ordinary source bounds. Partial, rotated,
sheared, or local rasters remain non-repeating unless a later profile proves unambiguous behavior.

The same canonical-column rule is reusable by future XYZ/MBTiles sources: display tile columns map
to canonical columns modulo the matrix width while row limits remain strict. G16 does not add an
empty tile module or make the open G10 HTTP/SQLite decisions. Their eventual implementations must
consume the tested wrap planner rather than inventing a second modulo policy.

### Labels, overlays, and export

Label candidates include visual copy position but retain logical feature identity. Collision order
is deterministic across copies, and aggregate label limits apply before paint. Hover, selection, and
edit previews use the same planned visual instance set as ordinary rendering.

Detached vector export captures the visible repeated instances because the exported page is a
picture, but it does not add copy indices to public feature identity. Existing export limits count
each emitted visual primitive and fail atomically when repetition exceeds them.

## Limits and diagnostics

| Concern | Default | Hard maximum / rule |
| --- | ---: | --- |
| Visible copies | 8 | 64 |
| Absolute copy index | 1,048,576 | 1,048,576 |
| Unique canonical query intervals | 2 | 2; a full period collapses to 1 |
| Inserted seam crossings per geometry | 4,096 | 4,096 |
| Raster horizontal-edge tolerance | `max(8 ULP, period * 1e-12)` | same |
| Display-coordinate precision | one-quarter screen pixel | same |

Existing feature, coordinate, label, snap, raster-request, optimization, export, diagnostic, and
owned-byte limits count aggregate wrapped output prospectively. Zoom interaction is clamped before a
copy or precision violation; programmatic requests fail the repeating layer atomically.

Invalid public values, missing view profiles, CRS mismatch, unsupported binding kinds, and mutation
of an attached binding are argument/lifecycle failures. Runtime layer outcomes use stable codes:

- `WORLD_WRAP_PRECISION_EXCEEDED` with numeric `copyIndex` and `maximum`;
- `WORLD_WRAP_COPY_LIMIT_EXCEEDED` with numeric `requested` and `maximum`;
- `SOURCE_LIMIT_EXCEEDED` with `scope=worldWrap` and
  `limit=features|coordinates|labels|parts|seamCrossings|ownedBytes`;
- `WORLD_WRAP_GEOMETRY_UNSUPPORTED` with
  `reason=projectedSeam|invalidRing|ambiguousHole`; and
- `WORLD_WRAP_RASTER_INCOMPATIBLE` with `reason=crs|extent|rotation|shear`.

Precedence is configuration/lifecycle, already-cancelled token, copy index/precision, visible-copy
planning, canonical query/output limits, source diagnostics in canonical query order,
geometry/raster compatibility, final cancellation, then atomic publication. Context remains bounded
and excludes complete geometry, source text, paths, and attributes.

## Verification strategy

Required deterministic cases include:

- identical views immediately west and east of the seam, modulo one world translation;
- repeated drags across several eastbound and westbound worlds without blank columns or jumps;
- zoom anchors on both sides of the seam and a view wider than one world;
- canonical interval splitting, full-world query collapse, deduplication, ordering, cancellation,
  and every copy/feature/coordinate limit;
- points, lines, multipart lines, polygons, holes, and multipart polygons touching and crossing the
  dateline;
- the same logical feature hit, selected, snapped, edited, measured, labelled, and exported through
  different copies;
- global raster repetition and explicit non-repetition of local/affine-incompatible rasters;
- cached G15 pan/zoom behavior at 10k and 1m without moving static rendering back onto the EDT;
- tolerant rendering regression rather than platform-specific pixel identity;
- performance evidence comparing wrapped and non-wrapped canonical workloads; and
- native smoke for toolkit-neutral canonicalization, query planning, seam splitting, diagnostics,
  and limit failures.

The normal quality gate remains timing-neutral. `performanceEvidence` records work and latency on
the existing reference lane; it does not establish a portable pan/zoom SLA.

## Task boundaries

- G16-001 approves the public profile, semantics, limits, and support wording.
- G16-002 proves continuous Natural Earth and track repetition in the G15 viewer.
- G16-003 adds the explicit periodic display model and first wrapped point-source `MapView` slice.
- G16-004 completes line/polygon seam handling, labels, portrayal, and vector export.
- G16-005 completes wrapped hit testing, selection, hover, measurement, snapping, and point editing.
- G16-006 adds explicitly global raster repetition and reusable canonical tile-column math.
- G16-007 closes hostile/boundary behavior, rendering/performance/native evidence, examples,
  Javadocs, staged consumers, and maintainer visual review.

G16-004 and G16-006 may proceed together after G16-003 because their production paths are vector
and raster specific. G16-005 waits for vector geometry completion. G16-007 is the convergence owner
for shared `MapView`, architecture, native, performance, task-index, and roadmap changes.

## Explicit exclusions

- No relaxation of EPSG:4326 or EPSG:3857 projection domains.
- No automatic repetition based only on CRS, source extent, or filename.
- No vertical/polar wrapping, globe, terrain seam, datum conversion, or arbitrary projection
  periodicity.
- No generic topology repair, self-intersection repair, geodesic densification, or arbitrary
  projected-seam inference.
- No new external dependency, JTS/PROJ/GDAL adapter, native code, GPU path, global cache framework,
  or empty future tile module.
- No change to literal format parsing or serialization unless an individual format task explicitly
  adopts this display policy.

## Completion rule

G16 is complete only when explicitly wrapped vector and compatible global raster layers pan
continuously across multiple datelines, default non-wrapped behavior remains byte-for-byte or
semantically unchanged, interaction resolves canonical logical features, malformed and excessive
work fails predictably, the G15 viewer remains responsive at every population tier, all verification
lanes pass independently, and the maintainer approves the visual seam behavior and public support
statement.

G16-001 completion record (2026-07-22): the maintainer approved core/AWT placement, pre-attachment
binding opt-in, transactional view changes, the public planner boundary, eight/64 copy limits, the
1,048,576 copy-index ceiling plus quarter-pixel precision check, westward half-period tie, atomic
polygon rejection, exact raster tolerance, diagnostic precedence, and optional-by-default support
wording. Later tasks may refine private implementation but require a new HITL decision to change
these public semantics or limits.

G16-002 completion record (2026-07-22): `HorizontalWrap` and its immutable public plan/problem values
implement the approved half-open canonicalization, checked copy/precision limits, query intervals,
translation, and canonical tile-column math in core. The G15 renderer uses that one planner to repeat
Natural Earth and canonical track positions; its continuous-coordinate overscan cache remains
coalesced off the EDT. Focused tests cover seams, full-world collapse, hostile limits, repeated land
and tracks, and the presentation probe records cached and multi-world navigation at all population
tiers without establishing a wall-clock SLA.
