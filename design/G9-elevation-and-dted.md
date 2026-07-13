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
