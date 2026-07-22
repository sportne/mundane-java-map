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

## Approved design direction

### Explicit opt-in

Wrapping is disabled by default. One immutable horizontal-wrap value defines:

- the finite inclusive canonical X bounds;
- the positive period derived from those bounds;
- a configured maximum number of visible copies at or below a hard library ceiling; and
- the supported absolute world-copy-index bound used to retain numeric precision and checked
  arithmetic.

`MapView` receives an explicit optional display-wrap configuration. Each `MapLayerBinding` separately
chooses `NONE` or `REPEAT_X`; a local source never repeats merely because its CRS is EPSG:3857. The
Level 2 convenience profile uses `[-WebMercatorProjection.WORLD_LIMIT,
WebMercatorProjection.WORLD_LIMIT]`, whose period is approximately 40,075,016.69 metres.

G16-001 freezes names, exact limits, constructor/with-method shape, failure behavior, and whether the
value lives in API or core before production changes. No classpath discovery, automatic CRS
inference, or mutable global policy is permitted.

### Canonical coordinates and continuous navigation

Projection inputs and source records remain canonical. A viewport center may move continuously in an
unbounded-looking display X coordinate. Wrap planning decomposes a finite display X into:

```text
displayX = canonicalX + copyIndex * period
```

with checked arithmetic and one canonical half-open interval. The exact upper seam canonicalizes to
the lower seam in the next copy, eliminating two identities for one meridian. The planner uses a
checked integer copy index and rejects values beyond the approved precision bound rather than
silently losing low-order pixels.

`MapViewport` remains source-compatible. G16 does not add a field to its public record. The approved
copy-index bound must be large enough for effectively continuous human navigation while keeping a
screen-pixel delta representable at every supported scale. Programmatic viewports beyond the bound
produce one stable structured layer diagnostic; interaction does not pan into an unsupported state.

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
Logical paint order is layer order, source record order, then ascending visible copy index.

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
finite delta, with a deterministic policy for an exact half-period tie. A crossing is split at the
canonical seam before projection. Inserted seam coordinates interpolate all supported two-dimensional
geometry ordinates exactly enough to retain closure and finite projection inputs.

Lines become ordered multipart lines as needed. Polygon rings are unwrapped independently, clipped
against shifted canonical windows, closed, and packed into valid polygon parts. Hole fragments stay
associated with their original polygon and are retained only in a resulting exterior by deterministic
containment. Empty fragments are removed. Invalid input, excessive crossings, ambiguous containment,
or inability to preserve a valid ring yields a stable record-level diagnostic; no topology repair or
heuristic reassignment is attempted.

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
are identical. Global PNG/JPEG/world-file rasters must cover the declared canonical period within the
approved affine tolerance. Partial, rotated, sheared, or local rasters remain non-repeating unless a
later profile proves unambiguous behavior.

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

G16-001 freezes exact values. The initial recommendation is eight visible copies by default, a hard
ceiling of 64, and an absolute copy-index bound justified by double-precision analysis rather than an
arbitrary `long` maximum. Zoom interaction is clamped before it would exceed the configured visible-
copy count. Programmatic requests report failure without partially painting the repeating layer.

New stable diagnostic categories cover invalid wrap configuration, copy-index precision bounds,
visible-copy limits, aggregate wrapped-query limits, seam-split limits, unsupported projected seam
rewriting, and raster-extent incompatibility. Context remains bounded and never includes complete
geometry or hostile source text.

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
