# G9 — Elevation and DTED design

Project index: [DESIGN.md](../DESIGN.md).

## Format-neutral elevation model (G9-001)

### A distinct sampled-data boundary

Elevation is numeric terrain data, not a color image. G9 therefore adds one third, deliberately
independent source contract rather than making elevation implement `RasterSource` or introducing a
generic source superclass:

```text
ElevationSource extends AutoCloseable
  metadata() -> ElevationSourceMetadata
  limits() -> ElevationSourceLimits
  openingDiagnostics() -> DiagnosticReport
  sample(int column, int row) -> OptionalDouble
  isClosed() -> boolean
  close()
```

Methods are synchronous on the caller's thread, and a source requires external serialization just
like the G4 source contracts. It creates no executor, callback, prefetcher, cache, mapping, or retry
policy. Immutable metadata, limits, and diagnostics are safe to retain and remain readable after
close. `sample` after close is `IllegalStateException`; a negative or out-of-range index is
`IndexOutOfBoundsException`. A successfully published source provides failure-free random sample
access apart from caller, lifecycle, or unexpected implementation failures: a file reader must finish
and diagnose decoding before publication. G9-007 must define a new operation failure/report contract
if evidence later justifies on-demand I/O rather than quietly weakening `sample`.

Close inherits G4's complete source-close contract. It is idempotent, marks the source closed before
releasing storage or handles exactly once, keeps the first cleanup failure primary with later failures
suppressed in encounter order, and maps cleanup failure to `SOURCE_CLOSE_FAILED`. A repeated close is
a no-op and never retries failed cleanup. Metadata, limits, and opening diagnostics survive either
successful or failed close. No implementation uses a finalizer, `Cleaner`, shutdown hook, or implicit
retry.

The separate interface is intentional. Elevation bounds locate the first and last sample posts,
whereas G4 raster bounds locate the outer edges of pixel cells. Subtyping or a public adapter would
create a half-cell ambiguity and incorrectly make colorization part of the data source. G9-002 owns
the temporary elevation-to-RGBA rendering operation; G9-005 owns coordinate interpolation; G9-007 may
later justify a windowed or lazy access shape from evidence. Neither `FeatureSource`, `RasterSource`,
nor their limits gain elevation fields.

### Metadata, coordinate convention, and units

The API values are:

```text
ElevationSourceMetadata(
  SourceIdentity identity,
  int columnCount,
  int rowCount,
  Envelope sampleBounds,
  CrsMetadata crs,
  ElevationUnit elevationUnit)

ElevationSourceLimits(
  int maximumColumns,
  int maximumRows,
  long maximumSamples,
  long maximumRetainedSampleBytes,
  int maximumRetainedWarnings)

ElevationUnit = METRE | INTERNATIONAL_FOOT | US_SURVEY_FOOT
```

`ElevationSourceMetadata` is immutable and requires at least two columns and two rows. The finite
`sampleBounds` has a positive x and y span and is expressed in the source CRS. CRS metadata is
required: a recognized geographic or projected definition is directly useful, while a retained
unknown definition is still permitted so a format can expose index-addressable samples honestly.
Unknown CRS data cannot be rendered or coordinate-queried until a caller supplies an explicitly
supported CRS boundary; equal retained text never implies transform compatibility. Missing CRS is
not representable because this model promises positioned terrain rather than an unlocated numeric
matrix.

Bounds identify sample centers, also called posts, not the outside edges of raster cells:

```text
column 0                 -> sampleBounds.minX
column columnCount - 1   -> sampleBounds.maxX
row 0                    -> sampleBounds.maxY
row rowCount - 1         -> sampleBounds.minY
```

Columns increase east/right and rows increase south/down. Storage and access are row-major, with
linear index `row * columnCount + column`. This one ordering is documented rather than represented by
a speculative ordering enum. Axis-aligned, non-wrapping regular grids are the complete model.
Rotated affine grids, antimeridian wrapping, irregular or curvilinear samples, multiple bands,
triangulated terrain, and temporal dimensions require later evidence and cannot be encoded by
overloading these values.

Metadata derives, rather than stores independently:

```text
sampleCount() -> checked rowCount * columnCount
columnSpacing() -> (maxX - minX) / (columnCount - 1)
rowSpacing() -> (maxY - minY) / (rowCount - 1)       // positive magnitude
sampleCoordinate(column, row) -> Coordinate
```

Coordinates use the same overflow-safe convex interpolation as the G4 grid boundary, with stored
bounds returned exactly at the first and last column/row. Construction rejects non-finite or
non-positive spacing and a grid for which either first or last adjacent post collapses to the same
floating-point coordinate. That gives G9-005 a usable monotone grid without an O(sample-count)
metadata validation pass. `sampleCoordinate` validates indices exactly like `sample`; no inverse
coordinate lookup, nearest-post choice, interpolation, clamp, or tolerance appears before G9-005.

`ElevationUnit` stores the sample's declared vertical unit and exposes an exact fixed
`metresPerUnit()` conversion: `1.0` for metre, `0.3048` for international foot, and
`1200.0 / 3937.0` for US survey foot. Samples remain in the declared unit. The model never silently
normalizes values or horizontal coordinates, and it does not infer units from CRS axes.

### One packed in-memory implementation

Core adds one final implementation for synthetic data, eager readers, and tests:

```text
PackedElevationGrid.copyOf(
    ElevationSourceMetadata metadata,
    double[] rowMajorElevations,
    BitSet noDataCells,
    ElevationSourceLimits limits,
    DiagnosticReport openingDiagnostics) -> PackedElevationGrid

PackedElevationGrid.copyOf(metadata, rowMajorElevations, noDataCells)
    -> defaults, empty opening diagnostics
```

The canonical factory checks nulls without retaining anything, then performs metadata-based resource
preflight before inspecting content. Only after that preflight does it require the elevation array
length to equal `metadata.sampleCount()` and reject a no-data bit at or beyond that count. It
defensively copies both inputs into exactly one row-major `double[]` and one fixed
packed `long[]` mask with one bit per sample. There is no boxed sample collection, tile object graph,
precision/storage hierarchy, direct/native buffer, memory mapping, or builder. A reader that needs a
different storage strategy must first supply the G9-007 evidence and a separately reviewed source
implementation; it does not change this value's contract.

Every unmasked sample must be finite. A masked input payload is ignored and stored as canonical
positive zero; unmasked negative zero is likewise canonicalized. Consequently no-data is structural,
never a magic numeric sentinel, and every finite elevation including an otherwise tempting sentinel
remains representable. `sample` returns `OptionalDouble.empty()` for a set mask bit and the exact
stored finite value otherwise. It never exposes an array, mask word, mutable view, stream, or bulk
format-specific accessor. A defensive-copy test mutates both caller inputs after construction.

The successful factory validates that `openingDiagnostics` uses the metadata source ID, contains
warnings only under G4's `DiagnosticReport` invariants, and retains no more entries than
`maximumRetainedWarnings`. Exceeding that retained-entry count is `IllegalArgumentException` because
the report is a caller-owned already-constructed value; an omitted-warning count remains observable
and does not count as another retained entry. Format readers use a limit-aware bounded report builder
before calling the factory. The convenience factory creates an empty report with that source ID.
Construction performs no coordinate transformation or format recognition.

`PackedElevationGrid.close()` releases both owned arrays after setting the closed state. Metadata,
limits, and the opening report survive close, while `sample` fails without reading released storage.
The implementation has no mutable static state and makes no concurrent sample/close guarantee beyond
the interface's external-serialization rule.

### Limits and stable failure behavior

The immutable limit value requires every maximum to be positive and has these defaults:

| Ceiling | Default |
| --- | ---: |
| Columns | 4,096 |
| Rows | 4,096 |
| Samples | 16,777,216 |
| Retained sample bytes | 136,314,880 |
| Retained warnings | 256 |

Retained bytes conservatively charge eight bytes for every `double` and one packed bit for every
sample, rounded up to a whole `long`. The default is therefore exactly
`8 * 16,777,216 + 8 * ceil(16,777,216 / 64)`. The full mask charge applies even if all samples are
valid, so acceptance never depends on content and a later reader can reserve storage before decoding.
Object headers and metadata are excluded consistently with G4 logical accounting.

Before content validation, copy, or allocation, the factory checks columns, rows, checked sample
product, and checked retained bytes in that order. The packed implementation's effective sample
ceiling is `min(limits.maximumSamples(), Integer.MAX_VALUE)`, because both owned primitive arrays use
ordinary Java `int` indices. A product above that effective ceiling fails as `limit=samples` before
array-length comparison; the diagnostic's `maximum` is the exact effective ceiling. This preserves a
long checked product and lets G9-007 reconsider a different access shape without pretending the eager
implementation can address it. Equality with a ceiling is accepted; maximum plus one is rejected.
Arithmetic overflow uses `Long.MAX_VALUE` as the requested value. Directly invalid metadata, array
length, mask range, report retention, limit construction, or non-finite unmasked value is a
parameter-named
`NullPointerException` or `IllegalArgumentException`, because it is a programmer-owned value defect.
A structurally valid grid exceeding an effective resource ceiling throws the existing unchecked
`SourceException` with terminal `SOURCE_LIMIT_EXCEEDED` and exactly this context:

```text
scope=elevationOpen
limit=columns | rows | samples | retainedSampleBytes
requested=<exact decimal value, or Long.MAX_VALUE on overflow>
maximum=<exact decimal effective maximum>
```

For columns, rows, and retained bytes the effective maximum is the configured value. For samples it
is the lower of the configured value and `Integer.MAX_VALUE`, as defined above.

The diagnostic uses the metadata identity and no location. G9-001 introduces no `ELEVATION_*` code,
format exception, logger, callback, global policy, or new diagnostic framework. A future format reader
may add its own bounded open limits and warnings while returning this same source contract.

### Module placement and verification boundary

`ElevationSource`, metadata, limits, unit, and their complete Javadocs live in
`mundane-map-api`. `PackedElevationGrid` and its checked allocation/grid math live in
`mundane-map-core`. Architecture tests keep both modules JDK-only, forbid AWT and format-specific
types in the API, and prove that elevation is neither a raster subtype nor a reason to add a generic
source base. No `mundane-map-io-dted`, example, renderer, color ramp, hillshade, native resource,
cache, or new Gradle verification lane is created by this task.

Focused tests cover every unit conversion, metadata bound and derived coordinate, row-major
orientation, exact endpoints, adjacent-coordinate collapse, dimension/product/byte boundaries and
overflow, the `Integer.MAX_VALUE` packed-address ceiling, array/mask validation, finite/no-data/
negative-zero behavior, defensive copies, source-ID and warning-count report validation, index
errors, idempotent release, close-failure primary/suppressed order and no-retry behavior, retained
metadata/report access, and post-close sample failure. Architecture evidence covers dependency
purity, prohibited mechanisms, and absence of an I/O module or raster inheritance. The narrow
implementation command is the API, core, and architecture checks, followed independently by the
normal quality gate and whitespace.

One source interface, two metadata/limit values, one three-value unit enum, one packed implementation,
and reuse of G4 identity/CRS/diagnostics are sufficient. Anything concerned with appearance, file
layout, geographic interpolation, random windows, or performance remains in the task that can prove
its need.

## Elevation raster layer (G9-002)

### Styling remains numeric and toolkit-neutral

Elevation rendering adds four immutable API values and no renderer SPI:

```text
ElevationColorStop(double elevation, Rgba color)

ElevationColorRamp(ElevationUnit unit, List<ElevationColorStop> stops)
  colorAt(double elevation) -> Rgba

ElevationHillshade(double azimuthDegrees,
                   double altitudeDegrees,
                   double verticalExaggeration)
  defaults() -> 315.0, 45.0, 1.0

ElevationRasterStyle(ElevationColorRamp colorRamp,
                     Rgba noDataColor,
                     Optional<ElevationHillshade> hillshade)
  of(colorRamp) -> transparent black no-data, hillshade disabled
  withNoDataColor(...)
  withHillshade(...)
  withoutHillshade()
```

A ramp requires 2 through 256 stops, owns an unmodifiable defensive copy, and requires non-null colors
plus finite strictly increasing elevations whose adjacent difference is finite and positive. Stop
signed zero is canonicalized. It performs a binary search. `colorAt` rejects a non-finite argument;
values below or above the range clamp to the first or last color, and an exact stop returns its exact
color. Between stops, straight unpremultiplied sRGB red, green, blue, and alpha are interpolated
independently with `(value - low) / (high - low)` and round-half-up to an integer channel. This is a
specified display mapping, not a claim of perceptual color interpolation. There is no automatic
min/max scan, histogram, normalization, palette discovery, categorical styling, or format-provided
cartography.

The ramp's unit must equal the source's `ElevationUnit` at binding and style replacement. Thresholds
are never converted silently. `noDataColor` may be any `Rgba`; transparent black is only the
convenience default. No-data is ordinary sample state and emits no warning.

Hillshade azimuth is finite in `[0,360)` degrees clockwise from north, altitude is finite in
`(0,90]`, and vertical exaggeration is finite in `(0,100]`. Accepted signed zero is canonicalized.
These deliberately bounded parameters are enough for one useful deterministic relief effect; ambient
light, multidirectional illumination, cast shadows, slope/aspect products, and blend modes remain
out of scope. Layer opacity and nearest/bilinear resampling reuse AWT's existing immutable
`RasterRenderOptions` rather than appearing in `ElevationRasterStyle`.

### One core plan and rasterization operation

Core adds one stateless final utility, `ElevationRasterization`, with one immutable nested plan. Its
logical surface is:

```text
plan(ElevationSourceMetadata metadata,
     Envelope visibleDisplayBounds,
     double displayUnitsPerPixel,
     RasterInterpolation interpolation,
     RasterRequestLimits effectiveLimits) -> Optional<Plan>

Plan(ElevationSourceMetadata metadata,
     RasterRequest request,
     RasterRequestLimits effectiveLimits,
     Envelope imageMapBounds,
     Envelope clipMapBounds)

rasterize(ElevationSource source,
          Plan plan,
          ElevationRasterStyle style,
          CancellationToken cancellation) -> RasterRead
```

The plan and read reuse `RasterWindow`, `RasterRequest`, `RasterRead`, `RgbaPixelBuffer`,
`RasterInterpolation`, `RasterRequestLimits`, and the fixed G6 `RasterResampling` math. Reuse here
means a bounded temporary RGBA result; it does not make `ElevationSource` extend `RasterSource`, add a
public source adapter, or change post-center metadata into pixel-edge metadata. The plan validates the
style-independent operation shape, and `rasterize` verifies that the supplied source metadata equals
the plan metadata snapshot before sampling so a plan cannot be applied to another grid.

`Plan` is a final nested value with a private constructor and read-only accessors; only `plan(...)`
can create it. It defensively owns the immutable metadata/request/limits/bounds references and has no
builder, public copying constructor, or subclass. Its embedded `RasterRequest` always has an empty
`tighterLimits`: `Plan.effectiveLimits` is the one complete rendering ceiling supplied to `plan`, not
a second base/override pair. Construction proves the window is inside the metadata dimensions,
output dimensions/interpolation agree with the plan, image and clip bounds are positive and
consistent, and all arithmetic preflight has passed. `rasterize` relies on those factory invariants,
then checks source metadata equality, source openness, style unit equality, and cancellation before
work.

`RasterRequestAccounting` gains only a source-compatible canonical overload that validates explicit
`columnCount` and `rowCount`; its existing `RasterSourceMetadata` overload delegates. Elevation
rasterization uses the same output-dimension, output-pixel, intermediate-byte, published-byte,
cancellation, overflow, equality-at-limit, and `scope=rasterRead` behavior. Its
source-pixels-examined counter retains G4's unique-window meaning as specified below; resampling and
shade taps are separately checked derived work, exactly as in G6. No duplicate elevation render-limit
value or accounting framework is introduced.

#### Post support and visible-window planning

The grid's represented terrain domain remains exactly `metadata.sampleBounds()`. For rendering only,
each post has a half-spacing support rectangle: adjacent supports meet at their midpoint, while the
first and last supports extend one half-spacing beyond the first and last post. This is a temporary
nearest/color-resampling footprint, not a change to metadata or a public position-query policy.

Planning first intersects the finite visible display envelope with the sample bounds. Empty or
zero-area contact returns empty and samples nothing. Monotone binary searches over exact midpoint
edges select the smallest contiguous post window whose support has positive overlap with that
intersection. `NEAREST` uses that window. `BILINEAR` expands it by one post per side where available,
so interpolation just inside the component edge can see the neighboring post; component clipping
discards the conservative fringe. Hillshade reads neighbors directly and does not enlarge the image
window again.

For a selected inclusive column range `first..last`, its left support edge is the midpoint of posts
`first-1` and `first`, or `minX-columnSpacing/2` when `first` is zero. Its right edge is the midpoint
of `last` and `last+1`, or `maxX+columnSpacing/2` at the final column. Rows use the analogous
north/top and south/bottom edges with decreasing y. Exact checked midpoint/add/subtract rules reject
a non-finite or collapsed result before a plan exists.

`imageMapBounds` is that support rectangle. It may extend beyond the CRS domain at a first or last
post and is never validated as terrain. `clipMapBounds` is the positive intersection of
`imageMapBounds` and the exact sample bounds. AWT must clip to `clipMapBounds` before drawing, so no
color is painted beyond the represented terrain domain and fit continues to use the sample bounds.
This avoids both a half-cell shift and a false CRS-domain error.

Output density uses the approved G6 cap independently per axis. The logical screen length of the
selected support determines the requested output size, but output width/height never exceeds the
selected post count and is at least one. Zooming out therefore performs deterministic project-owned
resampling; zooming in never invents more derived pixels than source posts. Final Java2D placement is
nearest-neighbor, so Java2D does not filter a second time. Non-finite scale/bounds, invalid windows,
or checked midpoint/size failure occurs before sampling or allocation.

#### Colorization and hillshade

Rasterization visits output rows north-to-south. `NEAREST` obtains one virtual post color through the
existing exact center/tie rule. `BILINEAR` obtains four virtual post colors and combines them with
G6's premultiplied-alpha integer RGBA algorithm. This is interpolation of rendered post colors, not
nearest/bilinear numeric elevation query behavior; G9-005 remains the sole owner of the public
coordinate-query policy.

A virtual post color is evaluated in this order:

1. Read its center sample once.
2. Return the exact no-data color immediately when absent; do not read shade neighbors.
3. Map the finite elevation through the ramp.
4. When enabled, calculate one shade and multiply only RGB; retain ramp alpha.

A custom source returning a present non-finite value violates G9-001 and causes
`IllegalStateException`, not a hostile-input diagnostic. No-data, ramp endpoint clamping, and a grid
edge are ordinary results.

Hillshade uses first-order east/north differences rather than a Horn kernel or terrain-analysis
framework. West/east are adjacent columns; north is row minus one and south row plus one. With two
valid axis neighbors it uses a central difference, with one it uses a one-sided difference against
the valid center, and with neither it uses zero gradient. A missing grid-edge or no-data neighbor is
unavailable, so no-data does not spread into an otherwise valid post.

Before vertical-unit conversion, eastward rise is `(east-west)/2` with both neighbors,
`east-center` with east only, or `center-west` with west only. Northward rise is
`(north-south)/2`, `north-center`, or `center-south` respectively. The matching horizontal distance
is one post spacing for a one-sided result and one post spacing after the central numerator has
already been divided by two. These sign rules are fixed by rows increasing southward.

Vertical differences are converted to metres with the source `ElevationUnit.metresPerUnit()` and
then multiplied by vertical exaggeration. For a recognized projected/metre CRS, horizontal distances
use the declared post spacing; EPSG:3857 relief is explicitly coordinate-space slope and does not
claim ground-scale correction. For a recognized geographic/degree CRS, distances reuse G3's fixed
spherical radius `6_371_008.8` metres and `StrictMath`:

```text
north distance = radius * radians(rowSpacing)
east distance  = radius * cos(radians(latitude)) * radians(columnSpacing)
```

At exact positive or negative 90 degrees, the east gradient is defined as zero. Let `gE` and `gN` be
eastward and northward elevation rise per horizontal metre. With azimuth `a` and altitude `h`:

```text
sun = (cos(h) * sin(a), cos(h) * cos(a), sin(h))
normal = normalize(-gE, -gN, 1)
illumination = clamp(dot(normal, sun), 0, 1)
```

Finite slope arithmetic uses the original neighbor operands and this fixed Java-double sequence; a
caller cannot first perform an overflowing subtraction:

1. `difference(positive, negative, central)` computes the oriented delta. With same-sign operands it
   evaluates `positive-negative`, then divides by two for a central result. With opposite signs it
   evaluates `half=positive/2-negative/2`; central returns `half`, while one-sided returns
   `half*2` unless `abs(half)>Double.MAX_VALUE/2`, in which case it saturates to
   `copySign(Double.MAX_VALUE, half)`.
2. `saturatingMultiply(value, positiveFactor)` compares `abs(value)` with
   `Double.MAX_VALUE/positiveFactor` before multiplication. It returns the signed maximum on excess.
   Apply it first with `metresPerUnit`, then with vertical exaggeration.
3. `saturatingDivide(value, positiveDistance)` divides directly when distance is at least one. Below
   one, it compares `abs(value)` with `Double.MAX_VALUE*distance` first and saturates on excess.
4. Every zero result is canonical positive zero. Central deltas use one post spacing because step 1
   already divided the two-spacing numerator by two. Exact poles bypass east slope as zero; every
   other horizontal distance must be finite and positive.

The normal is then scaled first by `max(1,abs(gE),abs(gN))` and normalized with nested
`StrictMath.hypot`, so every finite source value produces finite deterministic illumination. A flat
surface has illumination `sin(h)`. Each ramp RGB channel is multiplied by illumination and
round-half-up; alpha is unchanged and the no-data color is never shaded.

### Limits, cancellation, and reports

Before its first sample, `rasterize` charges source pixels exactly once for the unique selected post
window. With hillshade it expands that window by one post on every available side and charges the
area of that clipped union instead, independent of no-data content. Bilinear's interpolation halo is
already part of the selected plan window. This preserves G4's source-window meaning.

The following per-output-pixel tap counts are checked with overflow-safe multiplication as derived
work but are not charged again as source pixels, matching G6:

| Resampling | No hillshade | Hillshade |
| --- | ---: | ---: |
| Nearest | 1 | 5 |
| Bilinear | 4 | 20 |

Five shade reads are the center plus west, east, north, and south. The conservative derived-work check
may exceed actual calls and is intentional. Rasterization separately charges four intermediate bytes
and four published bytes per output pixel. Before invoking it, AWT uses a fresh accounting instance
to preflight the complete simultaneously live path: eight intermediate bytes per output pixel for
RGBA plus ARGB, and four published bytes for the returned RGBA buffer. The two independent checks are
intentional; neither subtracts a buffer merely because the other operation also accounted for it.
Every product and cumulative addition is checked before sampling or allocation. No terrain-specific
allocation limit is added.

Cancellation is checked before any sample/allocation, at least once per output row, within 4,096
sample lookups or output primitives, before buffer transfer, during AWT conversion, and immediately
before the binding's `ACTIVE -> SUCCEEDED` arbitration. Cancellation or known limit failure publishes
no partial buffer. The source remains usable, and G4's exactly-one terminal outcome, warning cap,
latest layer report, later-layer continuation, and recovery behavior apply unchanged.

Attachment unit/style defects are programmer errors before ownership transfer. Missing CRS cannot be
constructed by G9-001; retained unknown metadata uses `CRS_DEFINITION_UNKNOWN`, a fabricated
recognized definition uses `CRS_DEFINITION_MISMATCH`, and a different recognized source/display CRS
uses `CRS_RASTER_WARP_UNSUPPORTED`. No-data, clamp, polar east-gradient collapse, and flat shade emit
no diagnostic. The rasterization operation itself creates no new warning or `ELEVATION_*` code.

There is no retained colorized image, hillshade, statistic, window, or grid copy. G6 remains the sole
source-pixel cache and G7 remains the sole evidence-qualified AWT render-cache boundary. G9-007 may
change elevation access only after measuring this direct path.

### Direct AWT binding and viewer

The existing final tagged host gains a working elevation variant:

```text
MapLayerBinding.borrowedElevation(
    String layerId, String name, ElevationSource source, ElevationRasterStyle style)
MapLayerBinding.ownedElevation(
    String layerId, String name, ElevationSource source, ElevationRasterStyle style)

// explicit rendering-policy overloads
borrowedElevation(..., RasterRenderOptions options, RasterRequestLimits limits)
ownedElevation(..., RasterRenderOptions options, RasterRequestLimits limits)

MapView.setElevationRasterStyle(String layerId, ElevationRasterStyle style)
```

Short factories use existing raster-render and raster-request defaults. Candidate validation checks
source openness, exact unit equality, recognized same-CRS compatibility, style values, and limits
before ownership transfer. Borrowed/owned attachment, one-view/source claims, transactional
replacement, reverse close, close failures, current-operation cancellation, and permanent MapView
close are exactly G4's rules. A style replacement is EDT-only, unit-validates before mutation,
replaces one immutable snapshot, and schedules a full repaint without replacing the binding or
source. Existing `setRasterRenderOptions` accepts ordinary raster and elevation bindings with the same
semantics; it still rejects feature/snapshot IDs.

Elevation is visual base content: it never produces a hit, hover, or selection, and programmatic
selection rejects its ID. It participates in mixed layer order and fit through sample bounds without
sampling. Opacity zero performs no plan rasterization, conversion, or draw and preserves prior report
and availability, exactly like G6. Nonzero opacity composes packed alpha and layer opacity once with a
disposable AWT graphics child. The generated buffer is converted through the existing RGBA-to-ARGB
helper and drawn over `imageMapBounds` under both the component clip and transformed
`clipMapBounds`.

`examples/elevation-viewer` is added only with working behavior. Its headless content factory builds
one deterministic EPSG:4326 metre-valued analytic grid away from poles and the antimeridian, including
a fixed no-data region and caller-owned multistop ramp. The Swing UI identifies it as
“synthetic elevation — no DTED input” and offers default-hillshade enablement, nearest/bilinear
selection, and opacity. Mutations occur on the EDT, source creation stays off it, and the view is
closed explicitly. The example never parses DTED, calls ImageIO, chooses a format, or implements
render math.

### Verification and simplicity

API tests cover stop counts 2/256/257, ownership, null colors, ordering/non-finite/unrepresentable
spans and lookup, endpoint clamp, exact/midpoint colors and rounding, all units, no-data default,
hillshade parameter bounds, signed zero, and style withers/equality. Core tests cover factory-only plan
invariants and empty request overrides, exact north/south orientation, support
windows, outside/touching/partial/full and next-up/down boundaries, bilinear halo, image/clip bounds,
density cap, nearest/bilinear parity, planar and geographic flat/east/north slopes, azimuth, altitude,
exaggeration, poles, no-data neighbors, central/one-sided/opposite-sign saturating slope arithmetic,
unique-window versus derived-tap accounting, every byte/output limit boundary and overflow,
cancellation stages, and no retained copy.

AWT tests cover same/mismatched/unknown CRS attachment, unit mismatch before ownership, fit without
sampling, opacity, sample-domain clipping, north/south orientation, transparent/colored no-data,
directional shade regions, mixed order, non-interactivity, style/options replacement, borrowed/owned
close and failure order, report transitions, cancellation arbitration, and later-layer rendering.
Host allocation tests pin seven/eight/nine intermediate bytes per output pixel around the required
eight-byte live-path threshold while published payload remains four. The example has a headless
construction/control/lifecycle test. `renderRegression` adds only tolerant region, direction, bounds,
and opacity relationships, never cross-platform whole-image bytes.
Architecture tests forbid AWT outside the AWT module, elevation-as-raster inheritance/adaptation,
DTED/GeoTIFF types, external dependencies, discovery, workers, native code, and retained derived
caches.

Four immutable style values, one stateless planner/rasterizer with one nested result, one existing
binding tag, and one working example are sufficient. The slice reuses raster pixels, requests,
resampling, accounting, presentation options, conversion, cancellation, reports, and lifecycle while
leaving numeric interpolation, format reading, caching, and analysis to the tasks that own them.

## DTED Levels 0, 1, and 2 reader slice (G9-003)

### Specification and supported first profile

The format boundary follows [MIL-PRF-89020B](https://quicksearch.dla.mil/qsDocDetails.aspx?ident_number=110830),
sections 3.9 and 3.13 and tables I–III. The implementation cites the section/table in package
documentation and tests; it does not copy the specification, infer a profile from third-party reader
behavior, or promise every producer dialect.

The first supported profile is intentionally strict:

- uncompressed Level 0, 1, or 2 content, conventionally named `.dt0`, `.dt1`, or `.dt2`, with exact
  `UHL1` (80 bytes), `DSI` (648 bytes), `ACC` (2,700 bytes), then fixed-length data records beginning
  at zero-based offset 3,428; the path suffix is never level evidence;
- DSI series designator `DTED0`, `DTED1`, or `DTED2`, WGS84 horizontal datum, `MSL` or `E96` vertical
  datum,
  zero orientation, and a one-degree, axis-aligned, non-wrapping geographic cell;
- the standard latitude interval for the declared level and longitude interval for the cell's
  latitude zone, with full-grid UHL/DSI dimensions;
- exactly one fixed record for every west-to-east longitude profile, with all latitude positions
  represented south-to-north inside each record;
- big-endian fixed binary preambles and 16-bit signed-magnitude metre samples; and
- complete Level 0 and ordinary Level 1 cells without voids, plus Level 2 complete cells without voids
  or declared-partial Level 2 cells whose fixed profiles may contain `0xffff` void samples at any
  latitude position.

Variable-length partial profiles that omit samples before the first or after the last known elevation
cannot be classified safely from a byte sentinel. An input with full-grid headers but shortened
records therefore fails by the earliest exact file-length or data-record outcome; G9-004 owns its
hostile-input precedence. Partial Level 0 and partial ordinary Level 1 are
`DTED_PROFILE_UNSUPPORTED`; an SRTM Level 1 partial profile is deferred until its required DSI/ACC
producer fields are explicitly interpreted. Multiple accuracy subregions, nonzero orientation,
WGS72/other datums, two's-complement producer deviations, `.avg`/`.min`/`.max` companions,
compressed/container inputs, mosaics, and extensions above Level 2 are also outside this slice. A
later task may add an evidenced profile without weakening this one. G9-004 hardens every malformed
path and verifies checksums; it does not silently broaden the supported dialect.

For a full one-degree cell, latitude rows and longitude columns are derived exactly:

| Level | Latitude interval | Row count | Zone I/II/III/IV/V longitude interval (arc-seconds) |
| --- | ---: | ---: | --- |
| 0 | 30 | 121 | 30 / 60 / 90 / 120 / 180 |
| 1 | 3 | 1,201 | 3 / 6 / 9 / 12 / 18 |
| 2 | 1 | 3,601 | 1 / 2 / 3 / 4 / 6 |

Zones use the absolute latitude of the cell edge nearest the equator: I `[0,50)`, II `[50,70)`, III
`[70,75)`, IV `[75,80)`, and V `[80,90)`. This classifies north/south boundary cells symmetrically.
Column count is `3600 / longitudeInterval + 1`; all listed divisors are exact. North/south cells use
the same table. Cells are rejected when an origin/corner would cross a pole or wrap the antimeridian;
`[-180,-179]` and `[179,180]` remain ordinary non-wrapping bounds.

### Two public types and one eager transaction

The new JDK-only `mundane-map-io-dted` module has one public package,
`io.github.mundanej.map.io.dted`, containing only:

```text
DtedFiles
  open(SourceIdentity identity,
       Path path,
       DtedOpenOptions options) -> ElevationSource
  open(SourceIdentity identity,
       Path path,
       DtedOpenOptions options,
       CancellationToken cancellation) -> ElevationSource

DtedOpenOptions
  defaults()
  elevationSourceLimits() -> ElevationSourceLimits
  withElevationSourceLimits(ElevationSourceLimits) -> DtedOpenOptions
```

The no-token overload delegates to `CancellationToken.none()`. There is no bare path-only overload:
the caller supplies the non-sensitive logical identity required by G4/G9 diagnostics. The facade
returns only `ElevationSource`; it exposes no `DtedSource`, level/header/record type, parser, byte
view, channel, or format metadata. `DtedOpenOptions` is a final private-constructor immutable value
with defaults, value equality, bounded path-free `toString`, defensive ownership, parameter-named
null failures, and complete package/type/member Javadocs. G9-004 can add one real `DtedLimits` field
and wither without replacing the options type; G9-003 adds no inactive format-limit placeholder or
string-key option map.

The module declares API as an `api` project dependency because public signatures expose API types,
and core as `implementation` solely for `PackedElevationGrid`. It has no AWT, ImageIO, external
library, service provider, reflection, discovery, native code, or optional adapter. Package-private
parser peers remain in the public package, avoiding an exported-looking `internal` package. One
directly constructed package-private file-access seam supports deterministic short-read and close-
failure tests; it is neither public configuration nor auto-discovered.

Opening is one externally synchronous transaction:

1. Validate public arguments/options and an open cancellation token without touching the path.
2. Open one read-only `FileChannel`, obtain its size, and read fixed records positionally through
   reused bounded buffers. Never read the complete file into one byte array.
3. Parse/validate enough header state to establish the supported level, exact dimensions, bounds,
   record size, and expected file size before allocating sample storage.
4. Apply G9-001's column, row, checked sample, effective Java-array, retained-byte, and warning limits
   in their approved order, then allocate one temporary row-major `double[]` and `BitSet`.
5. Read profiles in file order, decode samples into the temporary arrays, and check cancellation at
   every controlled boundary.
6. Check cancellation, close the transaction channel successfully, and only then call
   `PackedElevationGrid.copyOf` with the fixed empty opening report.
7. Check cancellation immediately after that opaque defensive copy. If cancellation won, close the
   grid and throw `SOURCE_CANCELLED`; otherwise return it and release temporary references.

The returned source owns only the packed arrays and has G9-001's handle-free, idempotent close. No
file handle, path, header, buffer, temporary sample array, executor, cache, memory map, or cancellation
token survives publication. An I/O/parse/cancel failure closes the transaction channel exactly once;
the original failure remains primary and a mapped close failure is suppressed. A clean transaction-
close failure is terminal `DTED_IO_FAILED` with `operation=close`, not `SOURCE_CLOSE_FAILED`, because
no source was published. Unexpected `RuntimeException` or `Error` is cleaned up and propagated
unchanged with cleanup failure suppressed.

### Fixed header and profile decoding

All fixed text fields are decoded from ASCII bytes directly; no default charset, locale, regex over
the whole header, or intermediate field collection is used. Numeric fields accept only their exact
ASCII digit grammar and implied decimal place. Hemisphere characters are explicit `N|S` and `E|W`.
Coordinates are parsed to integral tenths of arc-seconds first, cross-checked without floating
tolerance, and converted once to degrees by division by 36,000. This keeps UHL origin/interval, DSI
origin/corners/intervals, level/zone dimensions, and the one-degree cell mutually exact.

G9-003 reads and uses only the fields required for this vertical slice:

- UHL literal, longitude/latitude origin, intervals, longitude-profile count, latitude-post count,
  and multiple-accuracy flag;
- DSI literal, five-byte level designator, vertical/horizontal datum, origin, four corners,
  orientation, intervals, row/column counts, and partial-cell indicator; and
- ACC literal and its no-subregion flag.

Every duplicated required field must agree exactly. Other fixed bytes are consumed but neither
retained nor interpreted yet. Levels 0 and 1 require partial-cell indicator `00`; Level 2 accepts
`00` or `01` through `99`. Indicator `00` always forbids voids. A nonzero Level 2 indicator permits
fixed-array voids but is not exposed as a percentage or generic metadata. G9-004 supplies the full
reserved-field, accuracy, grammar, and precedence matrix.

After the 3,428-byte headers, each supported data record has exact length
`8 + 2 * rowCount + 4`. Its preamble is one byte `0xaa`, a three-byte unsigned big-endian block count,
a two-byte unsigned longitude count, and a two-byte unsigned latitude count. For profile ordinal `p`,
block and longitude counts are exactly `p` and latitude count is zero. The final four-byte unsigned
big-endian checksum is read and retained only in operation-local scalars; G9-003 fixtures write the
correct byte-sum, but G9-004 owns enforcement and mismatch diagnostics.

Each two-byte big-endian elevation word is decoded without casting to Java `short` first:

```text
0xffff                 -> no-data mask set, stored payload +0.0
0x8000                 -> canonical finite +0.0
sign bit clear         -> magnitude 0..32767 metres
other sign bit set     -> negative magnitude
```

Two's-complement guessing is forbidden. File profile `p` becomes output column `p`. File sample zero
is the southernmost post and therefore becomes output row `rowCount-1`; the final file sample becomes
row zero. The checked row-major target index is
`(rowCount - 1 - fileSample) * columnCount + p`. This explicit transpose is the only layout copy.
Metadata uses the caller identity, exact one-degree `sampleBounds`, recognized canonical EPSG:4326,
and `ElevationUnit.METRE`. Level, headers, partial percentage, accuracy, checksums, and path do not
leak into the format-neutral model.

Expected file size is checked as
`3428 + columnCount * (12 + 2 * rowCount)` with checked `long` arithmetic before sample allocation.
G9-003 rejects shorter or longer content rather than accepting a prefix or trailing payload. The
level profile itself caps a record at 7,214 bytes and a standard grid at 3,601 by 3,601; G9-001's
effective caller limits may only reduce those values. G9-004 adds explicit file/profile/parser-
allocation ceilings and exhaustive hostile arithmetic tests.

### Diagnostics, cancellation, and precedence

Direct caller defects use the standard parameter/lifecycle exceptions. File-derived failures use one
bounded `SourceException` report and these initial stable codes:

| Code | Stable use |
| --- | --- |
| `DTED_IO_FAILED` | `open|size|read|close` JDK I/O failure |
| `DTED_UHL_INVALID` | malformed required UHL field |
| `DTED_DSI_INVALID` | malformed required DSI field |
| `DTED_ACC_INVALID` | malformed required ACC field |
| `DTED_PROFILE_UNSUPPORTED` | validly encoded level/datum/orientation/accuracy/partial shape is outside this profile |
| `DTED_HEADER_INCONSISTENT` | individually valid duplicated header fields disagree |
| `DTED_FILE_LENGTH_MISMATCH` | checked expected size differs from actual size |
| `DTED_DATA_RECORD_INVALID` | preamble/count/sample encoding violates the supported record |

`SOURCE_LIMIT_EXCEEDED` retains `scope=elevationOpen`; `SOURCE_CANCELLED` retains G4 semantics.
G9-004 adds checksum and newly distinguished hostile-input codes but does not rename these outcomes.
Components are `uhl`, `dsi`, `acc`, or `data`; data record numbers are positive profile ordinal plus
one, and all byte offsets are zero-based. Exact context is bounded to stable tokens such as
`field`, `reason`, and `operation`, plus decimal expected/actual values where documented. It never
contains a path, raw bytes, untrusted field text, localized exception message, datum string, or
fixture name.

Precedence is caller validation, already-cancelled token, open/size I/O, fixed header availability,
header/profile semantics, shared elevation limits, expected file length, temporary allocation,
profiles in physical order, final cancellation, channel close, packed copy, post-copy cancellation,
publication. The first terminal failure wins; cleanup never replaces it. File-derived values are
validated before API/core factories so their programmer-facing `IllegalArgumentException` cannot
leak as a malformed-file result.

Cancellation is polled before and after each opaque channel operation, between UHL/DSI/ACC and every
profile, within controlled sample conversion at no more than 4,096 values, before temporary/copy
allocation, before channel close/publication, and immediately after the opaque packed copy. It
performs no interrupt, listener, future, or worker coordination and publishes no partial grid.

### Fixtures, publication, and verification

Independent test-only writers generate, rather than check in, exact fixed files under each test's
temporary directory. They share no production parser constants or helpers and independently assert
important offsets, lengths, and SHA-256. All three use a zone-V cell from 80 to 81 degrees north and
0 to 1 degree east, reducing the valid profile counts while retaining full rows:

| Level | Columns | Rows | Data-record bytes |
| --- | ---: | ---: | ---: |
| 0 | 21 | 121 | 254 |
| 1 | 201 | 1,201 | 2,414 |
| 2 | 601 | 3,601 | 7,214 |

The Level 0 and Level 1 fixtures are complete and contain deterministic positive, negative, zero, and
first/interior/last samples. The Level 2 fixture is declared partial and adds leading, interior, and
trailing voids. All contain correct checksums. Tests prove exact bounds/spacing/CRS/unit, west/east
column and north/south row orientation, empty opening diagnostics, supplied limits, cancellation
before I/O/mid-profile/pre-copy/post-copy, channel closure before return, primary/suppressed cleanup,
source close, and retained metadata/report behavior. Malformed tables, checksum rejection, truncation
at every boundary, fuzzing, and real producer data belong to G9-004/G9-006.

The working module is added to the authoritative inventory as JDK-only Level 2 runtime and Published,
but not Level 1 release or Native-targeted. The publication contract consequently expands from the
historical five Level 1 coordinates to the current six-coordinate set by adding
`io.github.mundanej:mundane-map-io-dted`. In the same change, the staging verifier checks its binary,
sources, Javadocs, module metadata, POM scopes, license, and checksums; the standalone Java 21
consumer resolves it from staging and opens/queries/closes one independently built tiny Level 0
fixture. This extends rather than rewrites G8's recorded Level 1 candidate evidence.

Focused module and architecture checks run first, then the already-existing
`publicationDryRun consumerSmoke` lane, `qualityGate`, and whitespace. Native, DTED corpus,
performance, and render-regression lanes do not run. Two public types, one private fixed parser, one
eager grid result, three generated fixtures, and one staged consumer extension are enough: there is
no empty module, reader framework, public header model, file-retaining source, viewer, or speculative
lazy abstraction.
