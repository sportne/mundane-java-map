# G7 — Performance and indexing design

Project index: [DESIGN.md](../DESIGN.md).

## Performance evidence baseline (G7-001)

### Evidence-only support boundary

G7-001 adds no production algorithm, API, cache, threshold, or dependency. It creates one non-published
support project, `modules/mundane-map-performance-tests`, only when that project can execute all twelve
real-stack scenarios below. The authoritative project inventory classifies it as Support, includes its
small JVM checks/style/Javadocs in `qualityGate`, and excludes it from publication, Level 1 runtime,
Native Image, corpus, render-regression, and consumer graphs. G7's main dependencies are exactly the
API, core, AWT, shapefile, and image modules; G9-007 adds the DTED format module as one support-only
project dependency for its public-reader scenarios. It never consumes examples or another project's
test output.

The project uses the ordinary Java-library conventions. `src/main/java` owns a public launcher solely
for explicit build execution, package-private harness/scenario/report code, and support-only fixture
generators.
`src/test/java` owns the harness/statistics/report tests and one reduced smoke execution. Fixed encoded
PNG/JPEG evidence resources and their provenance may be main resources because the project is never
published. Each evidence Java process generates shapefiles and other writable fixtures only in its
unique native-WSL scratch tree; only completed reports return beneath the root `build/` tree. No
production package may depend on this project or its fixture code.

The module task `runPerformanceEvidence` and root task `performanceEvidence` are introduced here. The
root task depends only on that execution, uses a Java 21 launcher even when Gradle runs newer, and
supplies exact defaults `-Xms512m`, `-Xmx512m`, G1, headless AWT, UTF-8, `en-US`, and UTC. A typed
build-logic task copies the ordered runtime classpath and declared DTED inputs into an
invocation-unique directory directly beneath `/tmp`; the process working directory,
`java.io.tmpdir`, generated fixtures, and provisional reports all remain below that scratch root.
A `finally` cleanup removes the tree on success or ordinary failure. On success only the declared,
nonempty reports are copied as
`build/performance-evidence/evidence-v1.json`
and `evidence-v1.md`. Invocation-unique scratch state prevents cross-run measurement contamination;
the durable report directory remains normal Gradle output. The profile, harness, scenario order,
oracles, and report bytes other than measured
durations are unchanged. `check`, `checkAll`, and `qualityGate` must not depend on the full run.
G9-007 later makes `runPerformanceEvidence` depend on one fresh-JVM DTED memory probe; the root still
has this sole direct dependency and the canonical output remains these two reports.

G7-004 also adds module task `runQuickPerformanceEvidence` and root task `performanceQuick`. The root
depends only on that module execution. It uses the same native `/tmp` classpath/workspace isolation,
same Java settings, every current scenario, and the existing `SMOKE` fixture/oracle profile with one
warmup and two measurements. Supplying those counts explicitly makes its report
`investigation=true`, so every retention decision is `NOT_EVALUATED`; it cannot accept or reject
production code. It skips the independent full-cardinality BASELINE oracle, publishes only to
`build/performance-quick/`, and remains outside every
other verification lane. Its purpose is a complete semantic/timing iteration signal that is measured
under five minutes on the reference WSL workspace, not a portable duration gate or replacement for
canonical evidence.

A separate Ubuntu 24.04/Java 21 performance-evidence CI job first runs
`:modules:mundane-map-performance-tests:testClasses` with ordinary dependency resolution, then runs
`./gradlew --offline performanceEvidence --rerun-tasks --console=plain` and uploads the two reports.
G9-007's bounded intermediate probe output is deliberately not uploaded; its reviewed interpretation
and hash enter the G9 decision record instead.
Gradle dependency resolution is build setup; once the runner begins, scenario code performs no
network, process launch, home-directory lookup, download, or external-data access. The job fails on
configuration, fixture, semantic, cleanup, or report errors, never because a duration is high. It is
evidence, not a required portable speed claim.

The build deliberately does not implement a hostile-filesystem security model for these trusted
build inputs. Symlink component policing, report locks, negative scratch attacks, and live Gradle
task-notation traversal added orchestration cost without changing evidence semantics and were
removed in G7-005. Explicit task dependencies keep full, quick, probe, and optional JFR work outside
the normal quality lane.

### One small sequential harness

The support project retains only these package-private concepts:

```text
EvidenceConfiguration
EvidenceScenario
EvidenceObservation
EvidenceSample
ScenarioOracle
EvidenceReport
EvidenceRunner
```

`PerformanceEvidenceMain` is the sole public class because `JavaExec` needs a main entry point. The
fixed scenario list is constructed in source order. There is no annotation/reflection discovery,
ServiceLoader, benchmark SPI, agent, executor, worker pool, or parallel scenario execution.

Two profiles use the same fixture algorithms, scenario IDs, and oracle algorithms, with
profile-specific expected observations:

| Profile | Seed | Warmups | Measurements | Purpose |
| --- | --- | ---: | ---: | --- |
| `BASELINE` | `0x4d554e44414e454a` | 5 | 20 | Full `performanceEvidence` and comparisons |
| `SMOKE` | same | 1 | 2 | Normal tests and investigative `performanceQuick` with reduced cardinalities |

The seed's decimal value is `5572446169001248074`. Investigation properties are exact:
`performanceScenario` is absent or one declared scenario ID, `performanceWarmups` is an ASCII decimal
integer in `[0,100]`, and `performanceMeasurements` is in `[1,100]`. No sign, whitespace, leading
zero other than `0`, fallback, or clamping is accepted. Zero warmups means no warmup loop; at least one
measurement is mandatory. Any override marks the report `investigation=true`; the unqualified full
task uses `BASELINE` defaults and `investigation=false`. Actual values are recorded, and comparison
claims require identical configuration and fixture versions.

Each fixture is built once outside timing. A scenario performs `setupScenario` once, then for every
warmup and measurement performs untimed `prepareSample`, one exact timed batch, untimed oracle/result
consumption, and untimed `finishSample`; `closeScenario` runs once at the end. `prepareSample` resets
the exact initial viewport, hover/selection/report state, output surface, and sample-local resources.
It does not clear a cache whose declared state intentionally persists across warmup/measurements.
Cold/disabled scenarios create and later close a fresh sample source outside the timer. This prevents
pan/zoom drift and makes warm state deliberate rather than iteration-order leakage.

File/workspace/source construction and deletion occur off the EDT. Every `MapView` construction,
binding/viewport mutation, hit, paint, and close occurs on the EDT; each top-level `Graphics2D` is
created/disposed within its timed frame batch. An owned view closes before its fixture workspace.
When work fails, the scenario error remains primary and `finishSample`/view/source/file cleanup errors
are suppressed in encounter order. AWT-owned batches enter the EDT before starting the timer and leave
it after stopping, excluding queue latency while preserving real EDT execution. The harness never
calls `System.gc`, sleeps, calibrates/subtracts timer cost, pins CPUs, or creates threads.

Every warmup and measurement returns one small `EvidenceObservation`; outside the timer the fixed
oracle validates it and mixes its digest into one volatile consumer. A semantic mismatch or nonpositive
elapsed nanos fails immediately. Warmup times are discarded. Measured raw nanoseconds are retained and
sorted only for statistics. For an even count, median is
`lower + (upper - lower) / 2` and therefore rounds a half nanosecond down; odd uses the middle. The
zero-based nearest-rank p95 index is computed with checked integers as
`((95 * count + 99) / 100) - 1`. Throughput is reported only as checked integer
`operationsPerSecondMilli = floor(batchOperations * 1_000_000_000_000 / medianNanos)`; Markdown renders
that value with exactly three decimal places. Overflow, zero operations, or zero median fails rather
than saturating or switching to floating arithmetic.

Rendering oracles reuse G2's portable region, transformed-bounds, and per-channel-tolerance rules.
They validate six named portable invariants before digesting and never mix raw viewport doubles,
whole rendered images, or cross-platform pixel counts. Toolkit-neutral records and queries use exact
ordered digests. PNG reads hash dimensions plus every ordered packed-RGBA output pixel. JPEG reads
validate eight distinct safe tile-interior probes against the repository-authored source formula with
a per-channel tolerance of 20, then hash only probe coordinates, expected RGBA, and the stable
classification. Repeated raster reads must return distinct result and pixel-buffer identities, but
identity is validated rather than hashed. Reports separate source cache state
`NOT_APPLICABLE|DISABLED|ENABLED_PRESEEDED|ENABLED_MIXED_KEYS` from view cache state
`NONE|DEFAULT_WARM_AFTER_WARMUP`. `prepareSample` preserves the declared warmth; no label describes an
OS filesystem cache as cold.

### Six deterministic fixture families

The `BASELINE` fixtures are fixed and versioned; `SMOKE` uses the same generators with documented
smaller counts:

1. `feature-grid-v1`: 65,536 point records on a 256-by-256 row-major EPSG:3857 grid at exact
   `(1_000 * column, 1_000 * row)`, stable IDs/order, and no attributes. This is the explicit linear
   in-memory query baseline. A 16-by-16 window starting at `(c,r)` is exactly
   `[(c-0.5)*1000,(r-0.5)*1000,(c+15.5)*1000,(r+15.5)*1000]`; the eight fixed origins are
   `(0,0)`, `(32,16)`, `(64,48)`, `(96,80)`, `(128,112)`, `(160,144)`, `(192,176)`, and `(240,240)`.
2. `vector-path-v1`: 256 multiline records with four 64-vertex parts. For record `i`, part `p`, vertex
   `v`, coordinates are `x=i*4096+p*512+v*8` and
   `y=(i%64)*2048+p*256+(v even ? 96 : -96)+v*4`, yielding 65,536 coordinates. Another 128 records sit
   on a 16-by-8 grid with base `(2_000_000+(i%16)*80_000,(i/16)*80_000)`. Each contains two polygons:
   rectangles `[0,0,30000,60000]` and `[40000,0,70000,60000]`, with holes
   `[8000,15000,22000,45000]` and `[48000,15000,62000,45000]`. A shell
   emits 16 equal steps on each clockwise edge plus closure (65 points); a hole emits eight steps per
   counterclockwise edge plus closure (33), yielding 25,088 coordinates. IDs are `line:%03d` then
   `polygon:%03d`; no trigonometry or mutable coordinate graph is used.
3. `symbol-field-v1`: three immutable snapshot layers contain 3,072 points on a 64-by-48 grid, 512
   four-vertex lines on 32-by-16, and 512 five-point rectangles on 32-by-16, all at 20,000-unit cell
   spacing with IDs `symbol:point|line|polygon:<four-digit-ordinal>`. Point `o` is
   `(20000*(o%64),20000*(o/64))`; modulo 12 selects circle, square, triangle, diamond, cross, X, star,
   arrow, the G2 native vector path, the G2 4-by-2 RGBA icon, blue-square/yellow-diamond composite, or
   that composite at screen-relative 30 degrees and offset `(3,-2)`. Markers are centered 18-by-18
   screen pixels, opaque `(36,144,94)` unless their named composite/icon supplies the approved G2
   colors, and use nearest icon interpolation.

   Line `o` has base `(20000*(o%32),20000*(o/32))` and relative points
   `(0,0),(6000,2000),(12000,-2000),(18000,0)`; parity selects 8-pixel circle endpoint markers or
   8-pixel arrowheads on an opaque `(18,54,40)` two-pixel round screen stroke. Polygon `o` uses the
   closed relative ring `(0,0),(12000,0),(12000,10000),(0,10000),(0,0)` and modulo three selects the
   approved forward-diagonal, backward-diagonal, or cross-diagonal hatch with eight-pixel spacing,
   two-pixel line, opaque `(42,132,96)`, and transparent background. All opacity is `1.0`. Shared
   immutable symbol and icon instances make per-feature allocation impossible.
4. `hit-stack-v1`: four ordered source-backed `InMemoryFeatureSource` bindings each contain one record
   for each of 256 probe cells. Probe `q` is `(10_000*(q%16),10_000*(q/16))` under exact viewport
   `MapViewport(800,600,75000,75000,300)`. Each public world probe is converted through
   `MapView.mapToScreen` and passed to `hitTest` with exact tolerance `0.0` logical pixels. For
   `q=0..63`, all bindings cover the probe with geometry selected by `q%3`: point at the center; line
   from center `(-3000,-3000)` to `(+3000,+3000)`; or solid square of radius 3,000. Binding 3 is
   topmost. For `64..127`, the same cycle applies, only binding 0 is
   centered and bindings 1–3 shift by `(6000,6000)`. For `128..191`, every binding supplies a square
   shell of radius 3,000 with a square hole of radius 1,000 around the probe. For `192..255`, all
   geometry shifts by `(5000,5000)`. Twelve-pixel markers, two-pixel round lines, and solid fills make
   IDs `hit:<binding>:<q>` and the exact result 64 `hit:3:<q>` hits, 64 `hit:0:<q>` hits, then 128
   misses in probe order.
5. `shapefile-grid-v1`: a support-only exact binary writer creates 50,000 Point records in physical
   row-major order on a 500-by-100 grid at `(1000*column,1000*row)`, plus exact SHX, DBF, UTF-8 CPG,
   and recognized EPSG:3857 PRJ below `build/`. DBF fields are `ID N(10,0)` and `GROUP C(8)` with
   values `ordinal+1` and ASCII `group-%02d` from `row%20`; no rows are deleted/null. Eight query
   windows start at `(0,0)`, `(60,10)`, `(120,20)`, `(180,30)`, `(240,40)`, `(300,50)`, `(360,60)`,
   and `(420,80)` and use half-cell bounds around ten columns by ten rows, returning 100 physical
   records, 100 points, and 200 attributes each. Bytes are independently decoded by public-reader
   tests; exact lengths/SHA enter the report. The writer is not production/shared parser code.
6. `raster-1024x768-v1`: checked-in repository-authored 1,024-by-768 RGBA PNG and RGB JPEG plus exact
   rotated/sheared world files `2\n0.25\n0.5\n-2\n1000\n2000\n` and provenance/SHA records. PNG pixel
   `(x,y)` is `(x&255,y&255,(x^y)&255,((x+y)%5==0?128:255))`. JPEG uses constant 64-by-64 tiles with
   `(R,G,B)=((17*tileX+3*tileY)&255,(5*tileX+19*tileY)&255,(11*tileX+7*tileY)&255)`; probes are tile
   centers and tolerate 20 per channel. Checked bytes, not the running JDK's writer, are authoritative.

`SMOKE` changes only declared cardinality/batch parameters: feature grid is 32-by-32 and its 4-by-4
windows start at `(0,0),(4,2),(8,6),(12,10),(16,14),(20,18),(24,22),(28,28)`; vector data has 16
multilines with two 16-vertex parts plus eight unchanged two-polygon records; symbol layers contain
192 points, 32 lines, and 32 polygons; hit probes are exactly
`0..7,64..71,128..135,192..199`; shapefile grid is 50-by-10 (500 records), with 5-by-5 windows at
`(0,0),(6,0),(12,1),(18,2),(24,3),(30,4),(36,5),(42,5)`. It uses the same checked raster files but
window `(32,32,192,128)` to 120-by-80. Raster-pan uses the first four declared positions; vector-pan
uses the closed trace `(12,0),(0,12),(-12,0),(0,-12)`; vector-zoom uses `[1.25,0.8]` twice;
preseeded JPEG performs two timed reads.
Its throughput batches for scenarios 1–12 are respectively 1,024 records, 128 records, one frame, one
frame, 32 probes, 200 records, one frame, 9,600 output pixels, 19,200 output pixels, four frames, four
frames, and four frames. All formulas, symbols, IDs, ordering, metadata, and oracles are otherwise
identical. The fixed seed is an evidence-version salt mixed first into every fixture/scenario digest,
not a claim of pseudorandom generation; tests therefore pin its presence and value rather than a
nonexistent random sequence.

No fixture requires network, an external corpus, a random API, or a platform encoder. Values use the
approved packed production paths where applicable. Fixture setup, file generation, checksum, and
provenance validation occur before any scenario timer.

### Twelve stable real-stack scenarios

Scenario IDs, declaration order, fixture versions, cache labels, batches, and oracles are compatibility
inputs for G7-002 through G7-004. Descendants may add versioned scenarios or counters but cannot rename
or silently alter these rows:

| # | Scenario ID | Fixed batch, state, and semantic counters | Throughput batch | Primary next experiment |
| ---: | --- | --- | --- | --- |
| 1 | `memory-query-full` | One absent-bounds/ALL cursor over the linear `feature-grid-v1` source; 65,536 records/coordinates in exact ID order | 65,536 `records` | Oracle/no change |
| 2 | `memory-query-window` | Eight declared fixture windows/cursors; 2,048 records/coordinates total in source order; source cache N/A | 2,048 `records` | G7-002 |
| 3 | `dense-vector-render` | One 800-by-600 paint of one owned source-backed linear `vector-path-v1` binding; 384 records, 90,624 coordinates, viewport reset to `fitToData(24)` | 1 `frame` | G7-003 |
| 4 | `symbol-heavy-render` | One 800-by-600 paint of the three snapshot symbol layers; 4,096 features and 7,680 coordinates, viewport reset to `fitToData(24)` | 1 `frame` | G7-004 if justified |
| 5 | `hit-test-sweep` | Four owned source-backed linear bindings, exact viewport above, all 256 public hit tests; 128 hits/128 misses with exact binding/record order | 256 `probes` | G7-002/G7-004 |
| 6 | `shapefile-query-window` | One open source and eight declared `AttributeSelection.ALL` queries; 800 records, 800 coordinates, 1,600 attributes, clean diagnostics | 800 `records` | Format oracle |
| 7 | `shapefile-render-window` | One 800-by-600 paint of one owned shapefile source binding, viewport `fitToData(24)`; 50,000 point records, 50,000 coordinates, attributes NONE | 1 `frame` | G7-003 where applicable |
| 8 | `png-window-bilinear-disabled` | A fresh disabled-cache source per sample; one window `(128,128,768,512)` to 480-by-320, 393,216 source and 153,600 output pixels, exact RGBA digest | 153,600 `outputPixels` | Codec oracle |
| 9 | `jpeg-window-bilinear-preseeded` | One source, one same-key untimed preseed before warmup, then eight reads of `(128,128,768,512)` to 480-by-320; 1,228,800 output pixels, distinct result identities/tolerant probes | 1,228,800 `outputPixels` | G6 cache oracle |
| 10 | `affine-raster-pan` | One owned PNG raster view at 800-by-600/`fitToData(24)`; 12 absolute `initial.panByPixels(dx,dy)` positions `(-120,-80),(-80,-40),(-40,0),(0,0),(40,0),(80,40),(120,80),(80,80),(40,40),(0,0),(-40,-40),(-80,-80)`, one paint each | 12 `frames` | G7-004 if justified |
| 11 | `vector-pan-sequence` | The owned source-backed vector view resets to `fitToData(24)`, then cumulatively paints after four `(12,0)`, four `(0,12)`, four `(-12,0)`, and four `(0,-12)` pixel pans; 16 frames and final viewport within four ULP of initial | 16 `frames` | G7-002/003/004 |
| 12 | `vector-zoom-sequence` | The same source-backed view resets, then paints after `[1.25,0.8]` repeated six times at screen anchor `(400,300)`; 12 frames, unchanged center, final scale within four ULP of initial | 12 `frames` | G7-003/004 |

Rows 3, 5, 11, and 12 deliberately use source-backed in-memory bindings; future G7-002 comparisons may replace
only their explicit linear source factory while retaining every symbol, viewport, batch, and oracle.
Scenarios 1–2 likewise compare the direct linear versus later explicit indexed in-memory factory.
Row 7 remains source-backed shapefile evidence but its format source is never replaced by G7-002. The
symbol scenario remains snapshot-backed because its purpose is renderer/template work, not query
indexing. Source cache state is `NOT_APPLICABLE` for 1–7/11–12, `DISABLED` for 8,
`ENABLED_PRESEEDED` for 9, and `ENABLED_MIXED_KEYS` for 10. View cache state is `NONE` throughout the
G7-001 baseline; G7-004 may rerun unchanged rows with `DEFAULT_WARM_AFTER_WARMUP` only after recording
that implementation choice.

Ordered semantic values use one typed FNV-1a-64 oracle: offset `0xcbf29ce484222325`, prime
`0x100000001b3`, byte-wise XOR/multiply modulo `2^64`, with the evidence seed mixed first as a tagged
long. Each later value starts with one
type byte: UTF-8 string `0x01` plus checked four-byte big-endian length/content; int `0x02` plus four
two's-complement bytes; long `0x03` plus eight; finite double `0x04` plus the eight big-endian bits from
`Double.doubleToLongBits` after canonicalizing `-0.0` to `+0.0`; boolean `0x05` plus `0|1`; enum
`0x06` plus four-byte length and UTF-8 `Enum.name()`; packed RGBA `0x07` plus big-endian
`R,G,B,A` bytes. NaN/infinity is an oracle failure, never canonical data.
Attributes are key-sorted and encoded by the immutable public value kind: string, boolean, long,
finite double, `BigDecimal.toPlainString()`, `LocalDate.toEpochDay()`, `AttributeNull`, or each signed
byte of `AttributeBytes`. Runtime class names and `Object.toString()` are never attribute encodings.

Rendering mixes ordered `(invariantName, classification)` pairs, never raw pixels. Exact classes are
`BACKGROUND_MAJORITY`, `EXPECTED_COLOR_MAJORITY`, `EXPECTED_ALPHA_MAJORITY`,
`PAINT_BOUNDS_CONTAINED`, `PAINT_COUNT_IN_RANGE`, and `VIEWPORT_MATCH`; failed color/tolerance or bounds
checks terminate before digesting. More than half of the eight-pixel border must match the declared
corner background within eight per channel. More than half of painted pixels must differ from that
background in RGB while retaining alpha within eight of it. Paint bounds remain inside the surface,
paint count is between 16 and one less than the surface size, and the final viewport matches the
declared trace within four ULP. Implementation
freezes one 16-lowercase-hex expected observation digest for every `(BASELINE|SMOKE, scenario)` pair in
`ScenarioOracleV1` before this task can complete. Those 24 constants are independently recomputed by
profile-specific fixture tests and cannot be updated by a baseline/optimization run. The checked-in
reference interpretation records only the 12 BASELINE digests/evidence; SMOKE constants remain normal
test evidence rather than being mislabeled as performance baseline results.

Every row therefore declares its exact batch operation count and relevant semantic counters: records,
coordinates, probes/hits, frames, source/output pixels, and diagnostic-code digest. Baseline code does
not invent allocation or retained-memory numbers; JFR attributes allocation, and later explicit
cache/index policies report their own logical storage.

Shapefile scenarios exercise the real public source but do not claim G7-002 indexes the format; G5's
physical validation/diagnostic order remains authoritative. Raster scenarios compose with G6-004's one
source cache and do not introduce another retained pixel layer. All opened cursors, sources, files,
graphics, and views close after each scenario even when verification fails.

### Versioned report and reference interpretation

One immutable report object renders both files; Markdown never recalculates JSON values. JSON schema
`mundane-map-performance-evidence/v1` has fixed field order:

```text
schemaVersion
revision (`performanceRevision` Gradle property, else `GITHUB_SHA`, else absent)
environment
configuration
fixtures
scenarios
```

Explicit `performanceRevision` has precedence over `GITHUB_SHA`; when present, either must match exact
lowercase ASCII `[0-9a-f]{7,64}` or configuration fails before fixture setup. Values are never trimmed,
lowercased, or copied on mismatch. Environment strings come only from the named Java/OS properties,
must contain printable ASCII without CR/LF and at most 128 characters, and otherwise fail rather than
truncate. GC names use the same rule and sort lexicographically.

Environment records Java specification/runtime/vendor/VM name and version, OS name/version/arch,
available processors, maximum heap, GC names, and headless state. It does not copy arbitrary runtime
arguments. The only reported JVM settings are the task-owned canonical ordered list
`-Xms512m`, `-Xmx512m`, `-XX:+UseG1GC`, `-Djava.awt.headless=true`, `-Dfile.encoding=UTF-8`,
`-Duser.language=en`, `-Duser.country=US`, and `-Duser.timezone=UTC`; startup verifies the effective
heap/collector/locale/zone values agree.
Configuration records profile, unsigned-hex seed, warmup/measurement counts, and fixed JVM/locale/zone
settings. Fixture rows record stable ID/version, counts/sizes, semantic digest, and file length/SHA when
applicable. Scenario rows record ID, expected next experiment, batch/work unit, cache state, semantic
counters/digest, raw measured nanos, median, p95, and throughput.

Reports never contain hostname, user/home/absolute path, random UUID, timestamp, localized number, or
unfiltered command line. JSON escaping, UTF-8/LF, declaration ordering, bounds, and absent revision are
exact. “Deterministic” means one captured immutable report renders to structurally identical ordered
JSON/Markdown facts; it never claims timings or environment are byte-identical across runs. Markdown
presents that same object and prominently states JVM warmup, scheduler, filesystem cache, machine, and
cross-run noise caveats. Durations are not API claims, correctness gates, marketing numbers, or
automatically compared with another machine.

Raw report files remain ignored build artifacts. Completing the implementation task adds only a lean
checked-in reference interpretation table to this G7 file: candidate revision/environment, scenario,
dominant package/stage or `not established`, and the next experiment G7-002, G7-003, G7-004, or no
change. It records actual counter/JFR evidence and a SHA-256 of the source report, not a leaderboard or
portable threshold. No baseline observation is fabricated during design authoring.

The completed reference run used the G7-001 working tree based on revision
`5e807f8c595ede96f15924bd283e740fe3013de8`; the report correctly leaves `revision` absent because the
implementation changes were not yet committed. Its JSON SHA-256 is
`45a067d31beeb54c4ef755ea6b28a220aefe9bb88643c2a042d4217860766d8e`. The environment was Ubuntu
OpenJDK 21.0.11 on Linux 5.15 WSL2 amd64 with 32 reported processors, a fixed 512 MiB heap, G1,
headless AWT, `en-US`, and UTC. It used the canonical seed, all six `v1` fixture families, five
warmups, twenty measurements, all twelve scenarios, and `investigation=false`. The stable facts below
are semantic counters and stages, not portable timing claims:

| Scenario | Reviewed semantic evidence | Dominant package/stage | Next experiment |
| --- | --- | --- | --- |
| `memory-query-full` | 65,536 records and coordinates; digest `d4da23a839eb2a48` | Not established | No change |
| `memory-query-window` | 2,048 records and coordinates; digest `5f4d383c501cd46b` | Linear in-memory viewport filtering | G7-002 |
| `dense-vector-render` | 384 source records and six portable render invariants; digest `ef9c8b51b0161c12` | Source capture, AWT coordinate/path construction, and Java2D rasterization | G7-003 |
| `symbol-heavy-render` | 4,096 features and six portable invariants; digest `bab603885f61b84a` | Not profiled | G7-004 |
| `hit-test-sweep` | 256 probes: 128 hits and 128 misses; digest `0e482955bd4df5fe` | Ordered binding/geometry scan | G7-002, then G7-004 |
| `shapefile-query-window` | 800 records, 800 coordinates, and 1,600 attributes; digest `40f419feca886494` | Sequential SHP/DBF cursor reads; deliberately outside G7-002 | No change in G7 |
| `shapefile-render-window` | 50,000 records and six portable invariants; digest `b76b1a1b48e14b55` | Source capture followed by AWT paint | G7-003 |
| `png-window-bilinear-disabled` | 393,216 source and 153,600 output pixels; digest `2125ea9e978a8b2e` | Not established | No change |
| `jpeg-window-bilinear-preseeded` | 393,216 source and 1,228,800 output pixels over eight reads; digest `040aebf7d620a9b6` | Existing G6 source-cache path | G6 cache oracle; no G7 change |
| `affine-raster-pan` | Twelve frames and six portable invariants; digest `c9178011eeb45578` | Not established | G7-004 evidence only |
| `vector-pan-sequence` | Sixteen frames, 384 source records, and six portable invariants; digest `0791a2a96393ed1a` | Repeated source capture, projection, and path construction | G7-002, G7-003, then G7-004 |
| `vector-zoom-sequence` | Twelve frames, 384 source records, and six portable invariants; digest `0f00fd437fbd4f25` | Repeated source capture, projection, and path construction | G7-003, then G7-004 |

The same-toolchain JFR investigation for `dense-vector-render` used one warmup and two measurements.
The recording SHA-256 was `20fad2f1bb686afd13714c6321b14289d0192cbac4af588d498f122e18f894b5`;
its recording contained 436 `jdk.ObjectAllocationSample` and sixteen
`jdk.ExecutionSample` events. Reviewed AWT samples place rendering in source capture,
`MapView.toScreen`, multipart coordinate slicing, `Path2D` growth, and Java2D Marlin rasterization.
This supports G7-003's existing
screen-coordinate clipping/simplification experiment; it does not establish retained-memory size or
waive later same-binary evidence rules. The canonical report also makes sequential shapefile window
querying the clear environment-specific long pole, but G7-002 intentionally indexes only explicit
in-memory sources and must not claim a shapefile speedup.

### Optional reproducible JFR workflow

The module adds optional `performanceJfr`, using the identical resolved Java 21 launcher,
`BASELINE` fixture, selected exact scenario, semantic oracle, and heap settings. Before deriving any
path it validates `performanceScenario` against the fixed ID list. It deletes only that scenario's
prior `.jfr`, summary, and event text files, creates the bounded output directory, records afresh, then
invokes `bin/jfr` from the same Gradle toolchain installation to create human inspection files. It adds
only:

```text
-XX:StartFlightRecording=
  filename=<root-build>/performance-evidence/jfr/<scenario>.jfr,
  settings=profile,dumponexit=true,disk=true,maxsize=512m,
  jdk.ObjectAllocationSample#enabled=true
```

It is never a dependency of `performanceEvidence` or `qualityGate`. The documented workflow is:

```bash
./gradlew :modules:mundane-map-performance-tests:performanceJfr -PperformanceScenario=dense-vector-render --console=plain
```

The task writes `summary.txt` from `jfr summary` and `events.txt` from `jfr print` for exact events
`jdk.ExecutionSample`, `jdk.ObjectAllocationSample`, `jdk.ObjectAllocationInNewTLAB`,
`jdk.ObjectAllocationOutsideTLAB`, `jdk.FileRead`, and `jdk.FileWrite`. Empty unsupported/disabled event
families remain visibly empty; they are never reported as zero allocation. The implementation resolves
the root build path without embedding it in the evidence report. JFR evidence is environment-specific
hotspot attribution for CPU, allocation, and file I/O; it is not retained-memory accounting or a
timing assertion. There is no profiler abstraction, allocation agent, or external tool dependency
beyond the matching Java 21 `jfr` executable.

### Verification and simplicity

Ordinary module tests derive and execute only `SMOKE`; the independently reconstructed `BASELINE`
oracle runs only as a prerequisite of the dedicated `performanceEvidence` lane. Harness tests pin
seed/fixture reproducibility, scenario ID/order uniqueness, `BASELINE` versus `SMOKE`,
setup/timing/verification separation, exact iteration counts, warmup exclusion, raw samples,
odd/even median, nearest-rank p95, throughput, volatile consumption, semantic mismatch failure, and
cleanup on every stage. Report tests pin fields/order/escaping/LF, environment and revision fallbacks,
two-renderer equality, and absence of timestamps/sensitive paths. The smoke profile executes all twelve
public production paths at reduced cardinality.

Architecture/build tests keep the project support-only/non-published, reject network/process/
reflection/discovery/external runtime dependencies, and prove the full task is absent from normal,
native, corpus, rendering, and publication closures. The implementation runs focused module and
architecture checks, the new evidence lane, `qualityGate`, and whitespace. It does not run
`renderRegression`, `shapefileCorpus`, `nativeSmoke`, publication, or consumer lanes merely because
their production paths are measured.

The fixed task remains reviewable through three internal milestones: (A) project/lane wiring plus the
sequential lifecycle, statistics, report object, and two query scenarios; (B) all six independently
verified fixture families and the remaining ten semantic scenarios; then (C) architecture/task-graph
rules, offline CI, same-toolchain JFR, full baseline run, frozen oracle digests, and checked-in
interpretation. Each milestone runs its new smoke/focused checks and receives review, but no milestone
marks G7-001 complete, changes a production source, or unblocks G7-002 until the entire final validation
passes.

One support module, one sequential runner, six fixture families, twelve fixed scenarios, one report
object rendered twice, and optional JDK JFR are sufficient. There is no JMH, reusable benchmark SPI,
generic binary writer, public metrics API, database/server, automatic regression detector, native
acceleration, or optimization. G7-002 may act only after this baseline establishes the existing
semantic and evidence method.

## Packed spatial index and viewport query (G7-002)

### Explicit in-memory source choice

G7-002 leaves `FeatureSource`, `FeatureQuery`, `FeatureCursor`, AWT bindings, and every format contract
unchanged. Core adds one public immutable limits value and two explicit factories on its existing
concrete source:

```text
FeatureIndexLimits(
    int maximumRecords,
    long maximumRetainedBytes,
    long maximumBuildBytes,
    long maximumQueryBytes)
  defaults()
  withMaximumRecords(...)
  withMaximumRetainedBytes(...)
  withMaximumBuildBytes(...)
  withMaximumQueryBytes(...)

InMemoryFeatureSource.openIndexed(
    SourceIdentity identity,
    List<FeatureRecord> records,
    Optional<AttributeSchema> schema,
    Optional<CrsMetadata> crs,
    FeatureSourceLimits sourceLimits,
    FeatureIndexLimits indexLimits)

InMemoryFeatureSource.openIndexed(SourceIdentity identity,
                                  List<FeatureRecord> records)
```

The convenience uses absent schema/CRS and both values' Level 1 defaults. Existing `open(...)` remains
the byte-for-byte linear implementation and correctness oracle. Indexed selection is never automatic:
there is no strategy enum, record-count threshold, system property, mutable default, warning fallback,
or global registry. A caller that requests indexed construction either receives that immutable source
or a bounded opening failure; it never silently receives a linear source.

No `PackedFeatureSpatialIndex`, node, leaf, mutable builder, candidate collection, metric, or generic
index interface is public. The index is a package-private implementation detail owned by exactly one
immutable in-memory source snapshot. It stores only source ordinals/envelopes and never copies
geometry, attributes, IDs, schema, CRS, or symbols. Caller list mutation is already blocked by G4's
snapshot, and immutable records/geometries remain the single authoritative values. Close follows the
existing source lifecycle; primitive arrays are ordinary source state, not a separately closeable
resource or cache.

All new public members receive Javadocs covering explicit selection, defaults/limits, build failure,
one-live-cursor/external-serialization behavior, value equality, and absence of format integration.
The private index is safe for concurrent read-only plan construction, but the public
`InMemoryFeatureSource` deliberately retains G4's one-live-cursor and external-serialization contract.
G7-002 does not claim that arbitrary `FeatureSource` instances or one indexed source support concurrent
cursors.

### One fixed packed STR-16 tree

The sole structure is package-private final `PackedFeatureSpatialIndex`, a static sort-tile-recursive
R-tree with leaf capacity and internal fanout fixed at 16. Each source record occurs in exactly one
leaf, avoiding grid duplication/deduplication and handling points, axis-degenerate lines, large
spanning envelopes, equal envelopes, and skew without a format assumption.

Final primitive state is exactly:

```text
int[]    recordOrdinals     // N source ordinals grouped by leaf
int      leafCount
int      rootNode
int      height

double[] nodeMinX
double[] nodeMinY
double[] nodeMaxX
double[] nodeMaxY
int[]    nodeFirst          // leaf: recordOrdinals offset; internal: childRefs offset
byte[]   nodeCount          // 1..16 records or children
int[]    childRefs          // one tree edge per non-root node
```

Leaves occupy node indexes `[0,leafCount)`; successive parent levels follow; root is last. A nonempty
tree's exact node count is computed with checked integers by repeatedly adding
`ceil(levelItemCount/16)` until one root remains. `childRefs.length == nodeCountTotal - 1`. Empty input
owns zero-length arrays, `leafCount=0`, `rootNode=-1`, and `height=0`. Every final/work array length,
Java-array addressability, byte product, and sum is checked before its allocation.

The build uses only reusable primitive `int[N] order` and `int[N] scratch` work arrays. First they
hold/sort source ordinals and copy final leaf order into `recordOrdinals`; afterward their prefixes are
reused for parent node indexes. One iterative stable merge sort avoids `Integer[]`, streams,
per-element comparators, maps, recursion proportional to N, and per-node objects.

At each level:

1. `groupCount = ceil(itemCount/16)`.
2. `sliceCount` is the smallest positive integer whose checked square is at least `groupCount`, found
   by integer arithmetic without floating `sqrt`.
3. Stable-sort items by `(centerX, centerY, minX, minY, maxX, maxY, sourceOrdinalOrNodeIndex)`.
4. `groupsPerSlice = ceil(groupCount/sliceCount)` and
   `itemsPerSlice = checked(groupsPerSlice*16)`; split declaration-order chunks of at most that size.
5. Stable-sort each chunk by `(centerY, centerX, minY, minX, maxY, maxX,
   sourceOrdinalOrNodeIndex)`, then pack consecutive groups of at most 16.
6. Union each group's finite envelopes with the existing strict `Envelope` behavior and append its
   node; internal child references retain that packed order.

Centers use the existing overflow-safe finite envelope midpoint; finite canonical comparisons use
`Double.compare`, and the final ordinal/node-index key makes the ordering total. Equal, degenerate,
and duplicate envelopes therefore build byte-for-byte identical arrays for identical ordered input.
No epsilon, coordinate normalization, projection, or source-ID key participates.

### Index-specific resource limits

All `FeatureIndexLimits` fields are positive, immutable, and exact; there is no zero/unlimited
sentinel, property override, or mutable global. Defaults are:

| Ceiling | Default |
| --- | ---: |
| Source records | 1,000,000 |
| Retained index bytes | 16,777,216 |
| Cumulative build bytes | 33,554,432 |
| One query-plan bytes | 1,048,576 |

Retained bytes count exact primitive capacities:

```text
4 * N
+ 32 * nodeCountTotal
+ 4 * nodeCountTotal
+ 1 * nodeCountTotal
+ 4 * max(0, nodeCountTotal - 1)
```

Build bytes are cumulative retained capacity plus `8*N` for the two sort arrays; no replacement-level
array is allocated. Query-plan capacity is precomputed as
`8*ceil(N/64) + 4*stackCapacity`, where nonempty
`stackCapacity = 1 + 15*(height-1)` and empty is zero. This covers one candidate `long[]` bitset and the
chosen fixed worst-case depth-first stack capacity. Object headers/alignment and the pre-existing source record snapshot/geometry are
not falsely attributed to the index. Equality fits; plus one and arithmetic overflow fail before
allocation, with overflow reported as requested `Long.MAX_VALUE` under the existing convention.

Opening order is: direct arguments/value invariants; record-count ceiling before source-list copy;
G4's existing record/schema/duplicate-ID/extent snapshot; exact node/retained/build/query preflight;
then primitive construction. Index capacity failures reuse terminal `SOURCE_LIMIT_EXCEEDED` with
`scope=spatialIndexBuild`, `limit=records|retainedBytes|buildBytes|queryBytes`, and existing exact
`requested`/`maximum`. Exceeding the record ceiling is intentionally first even when later input would
also violate schema or ID rules. Once that ceiling passes, existing duplicate/schema failures retain
their G4 precedence and shape ahead of retained/build/query preflight. There is no `SPATIAL_INDEX_*`
diagnostic family, warning, or linear fallback.

### Source-ordered query plan

An absent-bounds `FeatureQuery` uses the unchanged linear cursor: an all-record query gains nothing
from a tree and allocates no plan. A present source-coordinate envelope uses inclusive AABB
intersection for nodes and entries:

```text
maxX >= query.minX && minX <= query.maxX &&
maxY >= query.minY && minY <= query.maxY
```

Exact edge/corner touches and point/axis-degenerate envelopes therefore match G4. `Envelope` has no
wrapped-antimeridian representation; tests cover registered CRS domain edges and ±180-degree touches,
not invented world-wrap/split semantics.

The cursor creates its plan lazily on first `advance()`, after the unchanged source/cursor/token/query/
tighter-limit checks. It allocates exactly the preflight stack plus:

```text
long[] candidates = new long[ceil(N/64)]
```

Depth-first traversal pushes children in reverse packed order so the first child is visited first,
although no traversal order reaches consumers. For each entry in an overlapping leaf, it sets the
source ordinal's candidate bit without testing the record envelope. Every record appears once, so no
dedup map exists. Level 1 uses no node-containment shortcut. A stack/node/entry/word cancellation
checkpoint occurs before allocation/traversal, within 4,096 primitive units, and before plan
publication.

After a complete plan, the cursor scans candidate bits in ascending source ordinal. Every bit first
calls the unchanged `FeatureQueryAccounting.recordExamined()`, then applies the same exact immutable
feature-envelope predicate as the linear source; a nonmatch is skipped and a match follows the exact
G4 attribute projection, returned-record/payload limits, cancellation, and publication path. Thus the
work ceiling bounds the actual record-envelope tests and preserves linear partial-publication timing.
Records, attributes, IDs, diagnostics, and output ordering are identical when
both implementations' work limits admit completion, while the indexed cursor honestly counts only
candidate envelope tests rather than pruned records. It may succeed under a tighter
`maximumRecordsExamined` where linear scan fails; tests pin that intentional difference. Previously
published records remain valid if a later candidate exhausts a limit.

The plan is operation/cursor-owned. Normal exhaustion, terminal failure, and cancellation clear it as
part of operation-resource release before entering the terminal state and releasing the cursor slot.
Early close, including close initiated by source close, preserves G4's different required order: mark
`CLOSED`, invalidate current, and release the source slot before clearing the plan exactly once as
cleanup. Primitive plan cleanup cannot fail, and repeated close never touches a later cursor's slot.
Failure/cancellation publishes no partial plan, leaves the still-open source reusable, and never
mutates the index. Empty/disjoint bounds may visit the root but examine zero records; a full present
extent may examine all records. Repeated queries rebuild plans; G7-004, not this task, decides any
retained viewport cache.

No AWT production change is needed. Existing source-backed paint/hit/hover/selection code already
issues a bounded `FeatureQuery`; an explicitly indexed source substitutes transparently. Query
padding, projected paths, clipping, simplification, source concurrency changes, and render caches stay
out of this slice.

### Shapefile boundary

G7-002 does not index shapefiles or reinterpret SHX. G5 requires physical SHP record framing and
diagnostics in source order, including off-query malformed records; SHX is an address table, not a
trusted spatial index. Building from untrusted record boxes, skipping off-query validation, or
materializing the complete file would be a different format task. The G7 shapefile baseline remains
an unchanged oracle and must not be reported as accelerated by this index. `FeatureSource` gains no
index method and external adapter types remain absent.

### Evidence extension

G7-001 scenario IDs/configurations/oracles remain unchanged. G7-002 adds `index-comparison-v1`, a
row-major point-grid generator using points `(1_000*c, 1_000*r)` and these exact dimensions:

| Records | Columns | Rows |
| ---: | ---: | ---: |
| 32 | 8 | 4 |
| 128 | 16 | 8 |
| 512 | 32 | 16 |
| 2,048 | 64 | 32 |
| 8,192 | 128 | 64 |
| 32,768 | 256 | 128 |
| 131,072 | 512 | 256 |

Build scenarios `index-build-128`, `index-build-8192`, and `index-build-131072` time explicit
`openIndexed` and report exact nodes/leaves/height/formula-checked retained bytes plus median/p95.
Each listed size has two scenario rows, `index-query-linear-<N>` followed by
`index-query-str16-<N>`, where N is ungrouped ASCII decimal (`32`, `128`, `512`, `2048`, `8192`,
`32768`, or `131072`). The index is built outside query timing; each timed operation opens, exhausts,
and closes one fresh cursor, so both implementations run the identical batch and indexed queries
build a fresh operation-local plan. This yields one raw sample series and median/p95 per
implementation and size under G7-001's existing one-series-per-scenario report shape.

G7-002 appends rows after the original twelve without reordering them: the three build rows in the
order above; the fourteen linear/STR query rows paired in ascending table size; then
`memory-query-window-indexed`, `hit-test-sweep-indexed`, `dense-vector-render-indexed`,
`vector-pan-sequence-indexed`, and `vector-zoom-sequence-indexed`. All rows exist under both G7-001
profiles with identical IDs and algorithms. The three build rows retain their named sizes in SMOKE;
the query rows retain their named source sizes but run viewport ordinals 0 through 23, exactly four of
each class, instead of BASELINE's 0 through 255. A build row's exact throughput batch is N
`recordsIndexed`; a query row's is 256 `queries` in BASELINE and 24 `queries` in SMOKE. Setup owns the
already-created immutable record fixture; build timing covers `openIndexed` through its successful
return and closes the resulting source after the timer. Query setup constructs the source/index once
outside timing; one timed batch opens, exhausts, and closes every cursor, and scenario cleanup closes
the source.

The report's required `expected next experiment` is exact rather than inherited from now-completed
G7-002: every build row, every comparison-query row, and `memory-query-window-indexed` uses
`no change`; `hit-test-sweep-indexed` uses `G7-004`; `dense-vector-render-indexed` uses `G7-003`; and
both indexed pan/zoom rows use `G7-003/G7-004`. These strings are pinned with scenario declaration
order and do not change in response to timings.

Every comparison record has exact ID `index:` plus its zero-padded six-digit source ordinal, empty
name, one point, and no attributes. Both linear and indexed sources open with the same explicit
`FeatureSourceLimits`: at most 131,072 examined records, 131,072 returned records, 131,072 returned
coordinates, one returned attribute value, 2,097,152 decoded text characters, 8,388,608 logical
payload bytes, and one retained warning. The full query therefore fits: its IDs contribute 1,572,864
decoded characters and 3,145,728 logical bytes, its points contribute 2,097,152 logical bytes, and the
records total 5,242,880 logical payload bytes. Tests pin these arithmetic facts and the exact limits;
no setup, timed, oracle, or inference execution relies on Level 1's lower 100,000-record return
default.

For viewport ordinal `i` from 0 through 255, class `i % 6` selects one of the following exact bounds.
The cycle contains 43 instances each of classes 0 through 3 and 42 each of classes 4 and 5:

1. disjoint: `[maxPointX+500,maxPointY+500,maxPointX+1_500,maxPointY+1_500]`, returning zero;
2. edge/corner touch: choose point column `(37*i)%columns` and row `(53*i)%rows`, then use
   `[x-500,y-500,x,y]`, returning exactly that point;
3. small: `k=max(1,N/1_024)` points;
4. medium: `k=max(1,N/128)` points;
5. large: `k=max(1,N/8)` points; and
6. full: `[-500,-500,maxPointX+500,maxPointY+500]`, returning all N points.

For zero-based classes 2 through 4 (the small, medium, and large items 3 through 5 above), every fixed
N makes k a power of two. Set
`width=2^floor(log2(k)/2)`, `height=k/width`, origin column
`(37*i)%(columns-width+1)`, and origin row `(53*i)%(rows-height+1)`; half-cell bounds around that
rectangle select exactly k points. Integer arithmetic is checked and the tests pin every expected
cardinality. These definitions replace approximate selectivity labels with reproducible workloads.
Crossover is the first declaration-order size at which the indexed query-batch median is strictly
less than the linear median; absent crossover is reported explicitly. It remains evidence, never an
automatic selection threshold. Every indexed comparison row carries the same evidence counter
`observedCrossoverRecords`: the positive record count at that first size or zero when none of the
seven sizes crosses. Keeping the value in the existing scenario evidence-counter map avoids a report
schema revision. The counter is present only when `performanceScenario` is absent and all fourteen
comparison rows completed under one configuration; warmup/measurement investigations can still
produce it because they retain the complete set. A single-scenario investigation runs only its named
row as G7-001 requires, omits this counter, and renders `not evaluated (filtered investigation)` rather
than zero or a companion run. Tests require all seven present copies to agree, pin omission for every
filter case, and require Markdown to render rather than recompute the value.

Existing rows gain exact scenario IDs `memory-query-window-indexed`, `hit-test-sweep-indexed`,
`dense-vector-render-indexed`, `vector-pan-sequence-indexed`, and `vector-zoom-sequence-indexed`; each
changes only the explicit source factory and baseline rows remain present. Semantic digest equality is
a hard gate. For each profile, these five rows directly reuse the corresponding original row's frozen
`ScenarioOracleV1` semantic digest and exact throughput unit/count; they do not create a second
expected truth. Every new build/query `(BASELINE|SMOKE, scenario)` pair instead adds its own frozen
expected observation digest, independently recomputed by profile-specific fixture tests under
G7-001's existing rule and never learned from an evidence run. Build observations pin N, leaf/node/
height/formula bytes, and a clean diagnostic digest. Query observations pin query count, total exact
records/coordinates, source-order digest, clean diagnostics, and, for indexed comparison rows only,
the untimed candidate total. The report adds implementation, node/leaf/height, retained bytes, build
median/p95, linear input records, indexed candidates, query/render median/p95, and observed crossover
under the existing schema's semantic/evidence counter maps. Timing is descriptive and does not select
the default factory.

Production exposes no metrics. Same-package core tests exercise the package-private index and
candidate plan directly: the ordinary layout access needed by the builder/cursor proves
record/node/leaf/height/retained formulas, and iterating the immutable plan proves candidate count.
No source retains a “last plan” or any test-observation state. The performance harness derives static
layout values independently from the frozen formulas and core tests prove those values equal the
actual arrays.

For only the fixed comparison scenarios, the harness infers each candidate count outside timing by
leaving every non-work ceiling at the explicit source value and finding the lowest positive tightened
`maximumRecordsExamined` that permits successful exhaustion. That value is C for C>0 because every set
bit is tested exactly once. C=0 is a successful zero-result exhaustion at work limit one. The proof is
unambiguous: if C=1, the only overlapping leaf has one entry, its union envelope is that record's exact
envelope, and the query must return it; if C>1, limit one fails before exhaustion. Core tests pin all
three cases. Inference executions never contribute timing samples, and the five real-stack indexed
variants do not claim a candidate counter. No logger, listener, JMX, public observer, or mutable
cumulative metric is added.

### Verification and simplicity

Core tests cover empty/one/15/16/17 records; exact node/edge/byte formulas and every limit
minus/equal/plus-one/overflow; repeated byte-identical layouts; equal/duplicate/point/axis-degenerate/
large-spanning envelopes; and every STR tie key. Fixed-seed property tests compare linear/indexed
results, order, geometry, ALL/NONE/ONLY attributes, reports, domain edges, disjoint/full/edge/point
queries, and tighter work-limit divergence over thousands of records/viewports.

Lifecycle tests cover cancellation during node/entry/bit traversal and publication, early/exhausted/
failed/cancelled/source close plan release, slot reuse, immutable yielded records after close, private
index concurrent plan equality, and public second-cursor rejection. AWT tests substitute one indexed
source through paint/hit/hover/selection with exact topmost/source order. Performance tests pin sizes,
scenario IDs, seeds, counter inference, formulas, semantic equality, and absence of a timing threshold.

Architecture tests reject public/internal node leakage, boxed/object trees, per-node objects, external
dependencies, threads/executors, static/global index/cache state, reflection/discovery, format/AWT
dependencies in core, and shapefile integration. Validation runs core/AWT/performance/architecture
checks, `performanceEvidence`, `qualityGate`, and whitespace; no native, render-regression, corpus, or
publication lane runs.

One explicit indexed factory, one small limits value, one fixed packed STR-16 tree, and one
cursor-owned source-order plan are sufficient. There is no index SPI, strategy chooser, generic tree
library, automatic threshold/fallback, persistent query cache, format adapter, or public observability.
If reference evidence shows small-N overhead, the report records crossover; it does not add implicit
runtime selection.

### G7-002 implementation evidence

The implementation follows this design with one explicit packed STR-16 factory and no public index
types or automatic threshold. Core tests directly pin actual plan totals against separately reviewed
fixture constants; the performance harness independently derives the same totals with an object-tree
reference, then validates each selected indexed row through untimed production work-limit inference.
Timed query batches only open, exhaust, and close cursors while capturing immutable record references;
all count/order/value/digest checks remain outside timing. The maximum fixed comparison capture is
6,242,731 pre-sized references (49,941,848 logical reference bytes) and is released after every sample.

The completed canonical BASELINE run used the G7-002 working tree based on revision
`52dbebdc251b0ee06a595a5c4f4486097c37b0b3`; the report leaves its revision absent because the changes
were not yet committed. All 34 scenarios completed under the fixed 512 MiB heap in 51m10s. The JSON
SHA-256 is `fae644ae310195b4dbb5fa9af0ec2e0231334bfedb8ed2d5fb8d6eb337e65951`, the observed descriptive
crossover is 32 records, and semantic oracles remained unchanged. Independent review and
`qualityGate` passed; no G7-003 clipping or simplification capability is included.

## Clipping and simplification (G7-003)

### One paint-only screen-plan boundary

G7-003 changes no source, query, geometry, symbol, hit, selection, fit, CRS, projection, or public
`MapView` contract. AWT projects one feature's immutable geometry once into packed logical-screen
coordinates, then asks one stateless core utility for an operation-local paint plan:

```text
ScreenGeometryOptimizationLimits(
    int maximumOutputCoordinates,
    long maximumBuildBytes,
    long maximumTopologyComparisons)
  defaults()
  withMaximumOutputCoordinates(...)
  withMaximumBuildBytes(...)
  withMaximumTopologyComparisons(...)

ScreenGeometryOptimizer.optimize(
    Geometry authoritativeScreenGeometry,
    Envelope expandedScreenClip,
    double tolerancePixels,
    ScreenGeometryOptimizationLimits limits)
  -> ScreenGeometryOptimization

ScreenGeometryOptimization
  authoritativeGeometry()
  renderingGeometry() -> Optional<Geometry>
  outcome() -> UNCHANGED | OPTIMIZED | PATH_CULLED | FALLBACK
  sourceComponentCount() / renderComponentCount()
  renderComponentOffset(int sourceComponentFenceIndex)
```

The limits and result are public final immutable core values because AWT is a separate module. They
have value equality, defensive array copies, indexed structural access, and complete Javadocs. All
maxima are positive. Defaults are 2,000,000 output
coordinates, 134,217,728 cumulative build bytes, and 1,000,000 topology comparisons. The optimizer
accepts only line- and fill-role geometry; marker geometry is a programmer-input error because point
symbols have no centerline or ring path to optimize. It never mutates or republishes source-coordinate
storage. Component counts/fenceposts are required to preserve multipart paint order; telemetry-only
coordinate/byte/comparison counts are deliberately absent from this public result and remain
operation-local inside AWT.

`authoritativeGeometry` is the caller's already-immutable packed screen value and is retained by
reference. `UNCHANGED` aliases it as rendering geometry without a copy. A single-component
`FALLBACK` does likewise; a mixed multipolygon `FALLBACK` owns one packed rendering geometry containing
authoritative fallback components beside optimized/unchanged components in source order. `OPTIMIZED`
owns one new packed geometry. `PATH_CULLED` has no rendering geometry. For lines, a source
component is an original part and a render component is one clipped fragment; the packed fencepost
mapping lets AWT paint every fragment for an original part before its endpoint markers. For polygons,
a source component is one declared polygon and maps to zero or one render polygon. All output
component/ring/coordinate order follows source traversal. Aggregate outcome precedence is exact:
`FALLBACK` if any visible component fell back; otherwise `PATH_CULLED` if no component renders;
otherwise `OPTIMIZED` if any component was clipped, simplified, or culled; otherwise `UNCHANGED`.

AWT owns one private `ScreenRenderPlan` wrapper only for the duration of one feature evaluation. It
contains the core result, resolved built-in symbol eligibility, screen clip/margin, and original-part
endpoint context, then becomes unreachable. Base paint and later hover/selection overlay passes build
their own plans because their symbol widths differ; at most the currently painted feature is retained.
There is no source/view/global cache, retained viewport plan, geometry version API, worker, prefetch,
or background computation. G7-004 alone may retain a proven result.

### Projection, eligibility, and clip margin

The AWT projector exhaustively handles all six G4 geometry types and preserves every point, part,
polygon, ring, repeat, and closure in packed screen geometry. It performs the existing strict
source-to-display operation followed by `MapViewport` conversion once per actual feature evaluation,
and canonicalizes signed zero. G4's mandatory whole-source strict projection preflight is the
authoritative CRS/domain failure boundary and may therefore have already visited the coordinate once
without retaining it. The immutable operation makes repeated post-success projection deterministic;
an impossible disagreement after successful preflight is an internal whole-pass failure, not a late
source diagnostic after pixels are publishable.

For a source paint, G7 makes the existing atomic publication point explicit. After query staging,
cursor cleanup, whole-entry projection preflight, and the final token checkpoint all succeed, the EDT
must win `ACTIVE -> SUCCEEDED` before painting any pixel from that binding. If cancellation already
won, it discards the entry and paints none. Once success wins, `cancelCurrentOperation()` returns false;
per-feature projection/optimization/Java2D paint is no longer a cancellable source stage and the report
still commits after child-graphics disposal. This matches G4's raster publication rule, prevents a
later feature from cancelling earlier pixels, and avoids retaining plans for an entire layer. Legacy
snapshots have no operation token.

Optimization is eligible only when the complete role symbol tree is made from the built-in Level 1
solid line, solid fill, hatch fill, legacy compatibility, and role-homogeneous composite renderers.
An unrecognized or custom line/fill renderer receives authoritative geometry unchanged; G7-003 adds
no renderer capability flag whose meaning custom code could misstate. Raster/vector markers and line
endpoint markers remain separate original-anchor operations, so a custom endpoint marker does not
disable safe centerline optimization. A custom fill outline does disable polygon optimization because
it consumes the ring path.

For each eligible evaluation, AWT resolves the maximum visible centerline, outline, or hatch half-
stroke width across the base symbol or the one overlay symbol being painted. Zero-effective-opacity
leaves contribute nothing. The closed optimization rectangle is
`[-m,-m,width+m,height+m]`, where `m = 1.0 + maximumHalfStrokeWidth` in logical pixels; a fill with no
stroke uses `m=1.0`. The one-pixel guard covers antialias support while Java2D's unchanged component
and inherited graphics clip remains the final device boundary. Endpoint marker footprints are culled
independently from their authoritative transforms and never enlarge the centerline rectangle. A
non-finite margin or expanded bound retains the existing symbol/transform failure rather than being
relabelled as optimization fallback.

Production simplification tolerance is exactly 0.25 logical screen pixel. Its equivalent display
distance changes with viewport units-per-pixel, which makes the policy scale-aware without assuming a
source CRS or simplifying nonlinear source coordinates. Core accepts any finite non-negative
tolerance for direct tests, but MapView has no mutable tuning property, system property, or ambient
quality mode. Screen optimization follows the already-rendered straight chords between projected
vertices; it does not densify a projection or claim geodesic error.

### Deterministic line plan

Every source part is processed independently in declaration order:

1. Consecutive equal screen points are skipped for centerline work; an all-coincident part retains G2's
   unpainted-centerline behavior.
2. Each remaining segment is clipped to the expanded rectangle by iterative Liang-Barsky parameter
   clipping in fixed left, right, top, bottom order. Inclusive edge/corner intersections are retained,
   but a boundary-only zero-length result is omitted rather than creating a new round cap.
3. Consecutive surviving segments join one fragment only when there was no rejected intervening
   segment and their computed boundary coordinates compare equal after positive-zero normalization.
   Otherwise a new fragment begins. No epsilon joins separate paths.
4. Each fragment is simplified after clipping by iterative Ramer-Douglas-Peucker. Its first and last
   points always remain. For point p and chord a-b, scale `(b-a)` and `(p-a)` by their maximum absolute
   ordinate, compute the clamped projection parameter from normalized dot/length-square values, then
   compute `scale*StrictMath.hypot(pNormalized-qNormalized)`. A zero scale is zero distance; a
   non-finite reconstructed distance makes the candidate fall back. A distance equal to tolerance is
   removable. `Double.compare` selects the maximum, the earliest source-order point wins an equal
   distance, and an explicit primitive stack replaces recursion.
5. A fragment with fewer than two distinct final points is omitted. Surviving fragments and their
   original-part fenceposts are packed once in original traversal order.

The clip-before-simplify order keeps every simplified chord inside the convex clip rectangle and makes
the 0.25-pixel error relative to the visible projected path. It never substitutes clip intersections
for semantic endpoints. G2 endpoint markers and outward bearings use the authoritative first/last
distinct points of the original part, even when its centerline has several fragments or no visible
fragment. Child-major composites still paint all source parts for one child before the next; within a
leaf, every fragment of a source part paints before that part's start/end markers.

### Conservative polygon candidate or whole-polygon fallback

The complete screen-geometry envelope may cull a polygon only when every exterior and declared hole
is disjoint from the expanded clip. This remains safe even for invalid caller topology. Every other
polygon component first undergoes bounded validation solely to decide whether optimization is safe:

- each ring has nonzero signed area, no non-adjacent touch/cross/overlap, and at least three distinct
  vertices plus closure;
- every hole is strictly inside its exterior with no shared/touching boundary;
- holes neither intersect nor contain one another; and
- the exact bounded floating predicates below classify every required orientation/area/containment
  decision unambiguously.

Zero-length edges are ignored for adjacency, but a repeated non-adjacent vertex is unsafe. Pair checks
are prospectively charged to `maximumTopologyComparisons`; exceeding it, encountering an ambiguous
numeric predicate, or finding topology outside this profile selects `FALLBACK` for that complete
polygon. Fallback is not repair, warning, or rejection: the original packed screen exterior and every
hole go to Java2D exactly as before. A multipolygon may optimize one valid component and fall back
another while preserving component order; its aggregate outcome is `FALLBACK` if any visible component
falls back.

A validated polygon wholly inside the expanded clip proceeds directly to closed-ring simplification.
For a partial intersection, optimization proceeds only when the exterior is strictly convex under the
predicate below and each hole is either strictly inside the clip or envelope-disjoint from it. A hole
that touches or crosses a
clip edge and a partial concave exterior cause whole-polygon fallback. The safe partial case clips the
exterior with Sutherland-Hodgman in fixed left, right, top, bottom order, keeps inside holes, and drops
only proven outside holes.

Closed-ring simplification first removes consecutive duplicates and excludes the repeated closure
from anchor selection. It chooses the lexicographically least
`(Double.compare(x),Double.compare(y),normalizedVertexOrdinal)` vertex as one anchor and the farthest
vertex as the other; equal farthest distances select the least forward circular offset from the first
anchor, then the least normalized vertex ordinal. Nonconsecutive duplicate vertices have already made
the polygon unsafe. It applies the line RDP rule independently to the two circular chains and restores
one exact copied closure coordinate.
The candidate must retain at least three distinct vertices, nonzero area and the original orientation
sign for every ring. The complete candidate then repeats the same simple-ring, strict-hole, and
hole-pair validation. Collapse, orientation reversal, a new touch/intersection/containment, clip
degeneracy, limit exhaustion, or numeric uncertainty discards every candidate array for that polygon
and uses its authoritative geometry. A hole is never silently collapsed, moved to another shell, or
converted to an island.

This validator is deliberately not a public topology predicate, overlay engine, polygon repairer,
triangulator, or general clipper. Its only observable promise is optimization or exact fallback. It
does not infer islands, change even-odd semantics for unverified input, normalize winding, split a
concave intersection, or introduce JTS/native code.

#### Exact bounded polygon predicates

Every predicate starts from finite positive-zero-normalized screen coordinates. For an orientation
`orient(a,b,c)`, compute deltas from a, let
`s=max(abs(bx-ax),abs(by-ay),abs(cx-ax),abs(cy-ay))`, return zero only when `s==0`, divide all four
deltas by s, and evaluate
`d=Math.fma(ubx,ucy,-uby*ucx)`. Let
`e=16*Math.ulp(max(1.0,abs(ubx*ucy)+abs(uby*ucx)))`. `d>e` is positive, `d< -e` is negative, and every
other result is ambiguous/unsafe. The validator never guesses collinearity from an epsilon.

A ring-area sign translates every vertex by the first distinct vertex, divides all deltas by the
maximum absolute translated ordinate, and accumulates normalized shoelace cross terms with Neumaier
compensation in traversal order. Its error bound is
`64*edgeCount*Math.ulp(1.0)`, computed with checked finite arithmetic; absolute compensated area at or
below that bound is ambiguous. A partially clipped exterior is strictly convex only when every three
successive distinct vertices has the same nonzero orientation classification as that area sign;
collinear/ambiguous turns conservatively fall back.

Non-adjacent segment intersection uses the four orientation classifications. Any ambiguous
classification is unsafe; otherwise opposite signs on both pairs mean a crossing. Exact shared
coordinates, detected before orientation, mean a forbidden touch. Strict point-in-ring uses the usual
half-open y rule in source traversal order and the same orientation classification; a query on a
vertex, horizontal boundary range, or ambiguous crossing is unsafe rather than inside. These rules
drive original and candidate simple-ring, shell/hole, and hole/hole checks identically. Tests pin the
formula constants, normalization, compensation, every sign boundary, strict-convex rejection, and
repeat results; no platform `Area`, epsilon setting, decimal fallback, or exact-arithmetic library is
introduced.

### Work bounds and failure behavior

The optimizer preflights every primitive output/scratch capacity with checked `long` arithmetic using
G4's logical byte charges. `maximumBuildBytes` is cumulative and includes output arrays, clip scratch,
keep bitsets, primitive RDP stacks, and topology work arrays; the authoritative input owned by the
caller is not charged again. Equality is accepted. A required output coordinate, build byte, topology
comparison, or Java-array length above its ceiling—plus arithmetic overflow—allocates no rejected
candidate and returns `FALLBACK`. Limits are optimization budgets, not untrusted-input acceptance
limits, so there is no `SCREEN_GEOMETRY_LIMIT_EXCEEDED` diagnostic and no partial/truncated plan.

Before processing a multipart geometry, a conservative whole-result capacity includes every
authoritative component plus maximum clip intersections and all fenceposts. If that complete mixed
capacity cannot fit the output/build limits, the optimizer aliases the entire authoritative geometry
as `FALLBACK` and builds no component candidate. A later prospective limit/overflow likewise discards
all component candidates and returns whole-geometry fallback. Mixed optimized/fallback multipolygons
are therefore produced only after the complete capacity preflight proves their one packed result can
be owned within the same budgets.

Already-valid finite screen input that produces a non-finite or numerically ambiguous optimization
intermediate falls back unchanged. Existing CRS, viewport, renderer, symbol, and Java2D failures keep
their approved diagnostic/exception behavior. No optimization diagnostic enters a source report, and
no timing-, locale-, platform-, or iteration-dependent branch selects a result.

### Authoritative interaction and rendering integration

Paint uses rendering geometry only for built-in line centerlines, polygon fill/outline paths, and
hatch clipping. Hit testing, hover, click selection, endpoint bearings/anchors, source feature
identity, selection persistence, measurement, labels, fit, extent, and query envelopes always use
authoritative geometry. A hit/hover/click operation builds an authoritative-only packed screen value
and does not run clipping or simplification. The intentional maximum 0.25-pixel paint displacement
therefore never changes which feature wins or manufactures an endpoint at a clip edge.

Source-backed base paint retains G4's one query transaction and complete staging/report boundary.
Legacy/source feature order, multipart order, child-major symbol order, fill even-odd behavior, hover/
selection overlay order, and graphics-state isolation are unchanged. A path-culled line still evaluates
its original endpoint markers; a polygon path cull suppresses its built-in fill/outline/hatch work. A
custom renderer, unsafe polygon, over-budget plan, or disabled evidence mode follows the exact former
unoptimized path.

All public MapView constructors select the Level 1 optimizer. A package-private AWT mode
`DISABLED|LEVEL1` and package-private constructor exist only for same-module equivalence tests and the
non-published performance project. That support project places one explicit bridge class in the same
AWT package to construct either mode without reflection; architecture tests forbid any published
consumer, example, format, or production module from referencing the mode/bridge. There is no public
toggle, environment switch, static mutable policy, or hidden fallback chosen from timings.

### Evidence and regression extension

Original G7-001 `symbol-heavy-render`, `dense-vector-render`, `vector-pan-sequence`, and
`vector-zoom-sequence` rows remain explicitly `DISABLED` in the performance harness, preserving their
declared pre-optimization experiment. So do G7-002's `dense-vector-render-indexed`,
`vector-pan-sequence-indexed`, and `vector-zoom-sequence-indexed`, preserving their one-variable source
factory comparison. Query and hit rows do not run the paint optimizer. G7-003 appends, in order:

1. `small-vector-render-unoptimized`;
2. `small-vector-render-optimized`;
3. `dense-vector-render-optimized`;
4. `vector-pan-sequence-optimized`; and
5. `vector-zoom-sequence-optimized`.

`small-vector-render-v1` uses exactly `line:000` and `polygon:000` from the profile's existing
`vector-path-v1` generator: 452 coordinates in BASELINE and 228 in SMOKE, one 800-by-600 frame after
`fitToData(24)`. Both small rows have throughput `1 frame`, identical fixture/viewport/symbols, and one
shared per-profile semantic oracle; only the package-private optimization mode differs. The three
optimized descendants reuse their corresponding original row's profile-specific fixture, batch,
throughput, viewport trace, symbols, and frozen semantic digest. Their exact expected next experiment
is `G7-004`; both small rows use `no change`.

The four original render rows, three indexed render rows, and `small-vector-render-unoptimized` report
`vectorPathState=DISABLED`; the four optimized rows report
`vectorPathState=LEVEL1_OPERATION_LOCAL`. Each adds deterministic counters for input/projected/render
coordinates, line fragments, culled paths, fallback plans, and logical retained render-geometry
bytes. AWT derives them from its authoritative geometry, the result outcome/mapping,
and G4's primitive/offset byte formula; it cannot observe core scratch bytes or topology comparisons.
Allocation evidence remains JFR evidence and is never relabelled as an exact counter. The counters come
from an operation-local package-private paint result returned through the support bridge; MapView
retains no last metrics, listener, logger, or public observability. The report compares paired
medians/p95 and allocation/JFR evidence descriptively. Semantic-oracle equality is a hard gate;
duration and a reduction in vertex count are not. A slower small case is recorded rather than causing
an automatic threshold or disabling the production policy.

Core tests pin clipping on every rectangle edge/corner, horizontal/vertical/diagonal/repeated/
degenerate parts, multipart splitting/mapping/order, RDP equality/ties/stacks, closed-ring anchors,
simple/invalid/nested/touching holes, convex partial clips, concave and boundary-hole fallback,
orientation/closure, mixed multipolygon outcomes, exact limits/overflow, and
byte-identical repeat results. Fixed-seed tests compare optimized plans to authoritative distance and
topology invariants without claiming arbitrary invalid geometry repair.

AWT tests compare enabled/disabled built-in paint across component edges, stroke widths, hatches,
holes, composites, endpoint markers, source/legacy bindings, overlays, pan/zoom, custom-renderer
bypass, cancellation-before-source-publication versus cancellation-after-success, and graphics-state
preservation. Hit/hover/selection and endpoint tests prove
they receive authoritative geometry. `renderRegression` adds tolerant region/color/bounds cases and
never requires whole-image or cross-platform pixel identity. Performance tests pin row order, modes,
profile cardinalities, throughput, oracle inheritance, counters, and absence of a timing gate.

Architecture tests keep algorithms JDK-only in core, confine Java2D/private orchestration to AWT,
reject external/native dependencies, recursion proportional to coordinates, public mutable buffers,
public optimization switches/metrics, static/global plans, and any cache. Validation runs focused
core/AWT/performance/architecture checks, `renderRegression`, `performanceEvidence`, `qualityGate`, and
whitespace; native, corpus, publication, and consumer lanes remain separate.

One fixed screen tolerance, one packed operation plan, deterministic line algorithms, and conservative
polygon candidate-or-fallback behavior are sufficient. There is no source simplification, query
padding, retained level-of-detail hierarchy, general topology library, custom-renderer protocol, or
cache. G7-004 receives an optimized uncached path and evidence rather than an abstraction built for a
cache that may not be justified.

### G7-003 implementation evidence

The implementation retains one packed core result by reference in AWT and traverses multipart source,
render-component, polygon, and ring ranges without unpacking component coordinate arrays. Core uses
prospective cumulative primitive charging, a conservative whole-multipart result preflight, implicit
allocation-free identity mappings for fallback, iterative line algorithms, and tri-state normalized
polygon predicates. Whole-result fallback is atomic on late build/topology exhaustion. Source paint
tests pin cancellation before publication to zero pixels/work and successful publication to a complete
paint that rejects later cancellation; separate legacy and feature-source CRS tests pin exactly one
post-preflight plan projection.

The performance harness freezes all twelve relevant rows in both profiles from a separate
fixture-to-core orchestration that cannot call MapView, the AWT evidence bridge, ScenarioRegistry
counter helpers, or captured reports. Production SMOKE and BASELINE captures match those 24 frozen
facts. Every counter dimension, mode, and row mapping has a negative control, and the report adds four
descriptive-only median/p95 pairs. Two independent reviews approved the final topology, build-budget,
packed AWT, publication, architecture, regression, and evidence boundaries.

The canonical BASELINE run used the G7-003 working tree based on revision
`c914cfd52c3eec304f3455a1af4edb571ff05d52`; the report omits a revision because the changes were not
yet committed. All 39 scenarios completed under the fixed 512 MiB heap in 49m34s. The JSON SHA-256 is
`3dd34fb556b1643ab18754108bcd31fa620b42b6571c5dafb1584da539256140`. Exact rendered coordinates fell
from 452 to 204 for the small pair, 90,624 to 27,136 for dense rendering, 1,442,144 to 424,768 for pan,
and 968,640 to 294,576 for zoom. On this host the optimized median was slower for every pair (0.634 vs
0.106 ms small, 7.971 vs 6.419 ms dense, 121.481 vs 101.623 ms pan, and 82.324 vs 73.326 ms zoom), as
the design requires the report to record rather than gate. `qualityGate`, `renderRegression`, focused
module/architecture checks, independent BASELINE-oracle verification, and whitespace all passed.

## Render-cache evidence and performance acceptance (G7-004)

### Two removable AWT candidates, not a cache framework

G7-004 evaluates exactly two private candidates: retained G7-003 screen plans and untransformed
Java2D templates for toolkit-neutral vector paths. A candidate exists in production only if the exact
same-binary evidence rules below retain it. Failure, ambiguity, incomplete canonical evidence, or a
threshold miss deletes that candidate's partition, modes, evidence rows, metrics, and candidate-only
tests before this task completes. Zero retained partitions is a successful result; if both are
rejected, the cache owner itself is deleted.

There is no cache in API or core, generic cache/key/weight/loader interface, public `MapView` option,
system property, service, listener, runtime timing choice, or application-visible metric. One private
`AwtRenderCache`, when at least one candidate survives, belongs to exactly one `MapView` and is
accessed only on the EDT. It has one small typed partition per retained candidate rather than a
pluggable namespace. It creates no thread, executor, lock, weak/soft reference, shutdown hook, or
process-global state. A package-private AWT evidence mode and same-package support bridge may isolate
candidate configurations during this task; architecture checks keep those types out of published
APIs, formats, and examples and remove modes for rejected candidates.

The G6-004 cache owned by each `ImageRasterSource` remains the only decoded/resampled raster-pixel
cache. AWT still converts an independently owned `RgbaPixelBuffer` for one paint and releases the
Java2D image afterward. This task does not retain encoded bytes, decoded pixels, resampled pixels,
Java2D raster images, affine raster plans, remote data, or disk entries and does not add a raster
cache metric or invalidation layer. The existing `affine-raster-pan` row therefore remains unchanged.

### Screen-plan candidate

`SCREEN_PLAN` stores one complete immutable `ScreenGeometryOptimization` produced by G7-003. It may
serve only the same built-in paint path; hit, hover, click, selection, measurement, endpoint markers
and bearings, query, extent, fit, source identity, and diagnostics always use authoritative uncached
geometry. Custom symbol renderers bypass it. A source-backed lookup happens only after G4's staged
query has won `ACTIVE -> SUCCEEDED`; from that point the approved feature paint is uncancellable.

Reuse is enabled only for snapshot bindings whose layer is the exact core `InMemoryLayer` and feature
bindings whose source is the concrete core `InMemoryFeatureSource`, including its explicit indexed
factory. Those implementations snapshot immutable feature/record/geometry references and return the
same geometry objects for their lifetimes. The general `Layer` contract permits a new current
snapshot on each call, and a general `FeatureSource` promises stable IDs and immutable yielded values,
not repeat-query object identity; other layers, shapefile, future format, and consumer sources
therefore bypass this candidate even if they happen to reuse an object today. This is one private AWT
eligibility check, not a public marker interface, source capability, renderer SPI, or inferred warmup
policy. Evidence rows use the already declared in-memory source and tests include equal-but-fresh
layer/source fixtures that deterministically bypass rather than growing a miss-only cache.

Its exact private key is the ordered tuple:

```text
binding attachment token identity
immutable geometry object identity
resolved geometry-to-display operation identity
exact MapViewport value
expanded logical-screen clip rectangle
0.25 logical-pixel tolerance bits
geometry role (`LINE` or `POLYGON`)
```

The attachment token distinguishes legacy/source bindings and changes on remove/re-add or source
replacement. Geometry identity is an intentional immutable version token: equal but distinct values
miss, avoiding an O(N) structural hash and any stale hit after a replacement. For an in-memory layer,
the resolved operation is the view's immutable map-to-display instance; for an in-memory feature
source, it is the binding-owned source-to-display instance. Neither is a CRS text guess. Exact
`MapViewport` includes component width/height, center, and units per pixel. The expanded clip includes
G7-003's one-pixel antialias margin plus the applicable exact half-stroke, so a style-margin change
misses without putting color, opacity, catalog name, or unrelated symbol state in the key.
Eligibility is decided before lookup and is not itself keyed: only the two built-in line/polygon roles
above are eligible. A fallback/optimized outcome is a value result and never a key input. Polygon
fill, outline, and hatch clipping share the same plan only when their exact expanded clip matches.

Viewport/style/operation/geometry changes normally produce safe key misses; returning to the exact
immutable state may hit. Removing or replacing a binding purges entries bearing its attachment token.
Closing the view clears the partition. Old viewport entries need no eager scan and remain bounded by
LRU. No cached clip intersection becomes a semantic endpoint, no source version string is inferred,
and no mutable caller value is retained.

The provisional production limits are:

| Limit | Value |
| --- | ---: |
| Entries | 8,192 |
| Logical retained bytes | 33,554,432 |
| Logical bytes per entry | 4,194,304 |

Logical weight reuses G4/G7's primitive accounting: eight bytes per retained reference slot, eight
per retained double, four per retained int, and one per retained byte. It traverses the fixed private
key/value shape and charges every distinct packed array reachable only because of the entry exactly
once by identity: the source geometry's coordinates/part/component offsets; authoritative projected
screen geometry; distinct render geometry; structural mapping/outcome arrays; and the key/value
reference slots. The standalone attachment token has no binding/source back-reference, and the
resolved operation is already view-owned, so those borrowed objects contribute only their cache
reference slots. Source geometry is charged conservatively even when a legacy layer also owns it.
Exact per-geometry formulas, alias cases, equality, plus-one, and checked overflow are pinned by tests.
Object headers/alignment are not invented as exact heap facts. Count, per-entry, and total logical
limits provide deterministic bounds; JFR may attribute actual allocation on the reference JDK but is
not retained-memory accounting.

### Vector-template candidate

`VECTOR_TEMPLATE` retains the approved private untransformed pair of `Path2D.Double` values created
from one immutable toolkit-neutral `VectorPath`: `strokePath` contains every subpath and `fillPath`
contains only explicitly closed subpaths. Only the built-in AWT vector renderer can use it.
Placement, anchor, map/screen size, transform, rotation, offset, color, stroke, opacity, catalog name, composite order,
and feature identity are applied after lookup and do not multiply templates. Raster icons, hatch
lattices, line centerlines, endpoint placement, custom renderers, and composite containers are not
cached.

Its exact key is `VectorPath` object identity. Level 1's even-odd winding rule is fixed and therefore
is not a speculative key field. Equal distinct paths intentionally miss. Neither cached path is ever
returned, appended to, or transformed in place. For every placement, AWT derives fresh
screen-coordinate `Shape` values with `AffineTransform.createTransformedShape`, preserving G2's rule
that marker scaling is never installed on the child `Graphics2D` and cannot scale `SymbolStroke`
twice. A catalog/registry change that supplies a new path identity misses naturally, while another
immutable symbol referring to the same path may reuse the pair. The instance registry is immutable
and custom registrations cannot access this cache.

The provisional production limits are:

| Limit | Value |
| --- | ---: |
| Entries | 512 |
| Logical retained bytes | 4,194,304 |
| Logical bytes per entry | 262,144 |

`VectorPath` itself has no construction ceiling. Before allocating either Java2D path, the converter
scans its checked command/ordinate counts once, derives exact stroke-stream and closed-fill-stream
command/ordinate counts, and computes the complete logical entry weight with checked `long`
arithmetic. Weight charges the retained key/path reference slots, the key path's packed opcode and
ordinate arrays, and both converted streams at one byte per used command plus eight bytes per used
ordinate. If the exact weight exceeds the per-entry or total partition limit, conversion is bypassed
before candidate allocation and no entry is evicted. It is deliberately not called a `Path2D` heap-
size estimate; the JDK implementation owns spare capacity and headers. Count/per-entry/total limits
bound retained logical input, while reference JFR records actual allocation behavior. The partition
is view-global because templates have path identity rather than binding ownership and is cleared on
view close.

### Admission, LRU, and lifecycle

Each retained typed partition uses deterministic LRU state backed by an insertion-order
`LinkedHashMap` on the EDT. Promotion is an explicit remove/reinsert after successful use, so lookup
itself cannot mutate state for work that later fails:

1. A lookup reports one request and hit or miss but does not yet mutate LRU/storage state.
2. A miss builds and uses the complete candidate while retaining no partial state.
3. Construction failure, source cancellation before success publication, an incomplete value, or a
   renderer failure before that value's leaf returns normally causes no admission, promotion, or
   eviction. Earlier independently completed values remain valid.
4. A disabled or logically oversized value reports bypass and performs the ordinary uncached render;
   it evicts nothing. There is no truncated entry.
5. After successful leaf rendering, a hit is promoted exactly once. A fitting missed value evicts the
   eldest successful-access entries in exact order until both the count and logical-byte budgets can
   hold it, then admits it as newest.
6. Replacement subtracts the old exact weight before checked admission. Accounting overflow fails the
   candidate path and falls back uncached without altering the partition.

The cache holds only immutable/private completed values. One cache event record is folded into the
operation-local package-private AWT paint result already used by G7 evidence. It carries the batch's
checked event sums, ending entries/bytes, and maximum entries/bytes observed during that batch,
including its starting retained state; the cache owner keeps no cumulative telemetry or lifetime
peak. Same-package tests and the non-published support bridge can consume that result, but `MapView`
stores no public or queryable last metrics. Cross-thread cancellation never touches cache state, and
non-EDT cache access is impossible through production entry points and rejected by direct tests.

### Append-only same-binary evidence

All G7-001, G7-002, and G7-003 scenario IDs, order, fixtures, viewport traces, batches, throughput,
cache labels, optimizer/index modes, and frozen semantic oracles remain unchanged. Existing rows are
the same-binary disabled comparisons; no checked-in report from another build is used as a timing
baseline. G7-004 appends these candidate rows in order while their candidate exists:

1. `small-vector-render-screen-cache-cold`;
2. `small-vector-render-screen-cache-warm`;
3. `dense-vector-render-screen-cache-cold`;
4. `dense-vector-render-screen-cache-warm`;
5. `vector-pan-sequence-screen-cache-cold`;
6. `vector-pan-sequence-screen-cache-warm`;
7. `vector-zoom-sequence-screen-cache-cold`;
8. `vector-zoom-sequence-screen-cache-warm`;
9. `symbol-heavy-render-template-cache-cold`; and
10. `symbol-heavy-render-template-cache-warm`.

Screen rows use the G7-003 Level 1 optimizer and only `SCREEN_PLAN`; template rows retain the original
symbol fixture's optimizer state and enable only `VECTOR_TEMPLATE`. Every descendant reuses the exact
corresponding profile fixture, viewport/trace, symbols, batch, throughput, and frozen semantic oracle.
Query, hit, shapefile, raster, index-comparison, and original rows remain untouched.

The schema-v1 `viewCacheState` domain is extended only for appended rows with exact values
`SCREEN_PLAN_COLD_EACH_SAMPLE`, `SCREEN_PLAN_WARM_PRESEEDED`,
`VECTOR_TEMPLATE_COLD_EACH_SAMPLE`, and `VECTOR_TEMPLATE_WARM_PRESEEDED`. Cold `prepareSample`
clears only its candidate partition immediately before the timed batch. Warm `setupScenario` performs
one complete untimed identical batch, verifies its oracle, resets semantic view state, and then
retains cache state through warmup and measurement samples. Thus warm means preseeded even when
`performanceWarmups=0`; it never claims a cold OS/filesystem/JIT state. Each timed batch creates one
fresh operation-local accumulator and reports only its checked event sums plus that batch's ending and
maximum cache state, so preseed and warmup work cannot pollute a measurement.

Candidate rows append exact evidence-counter keys in this order:

```text
<screenPlan|vectorTemplate>CacheRequests
<screenPlan|vectorTemplate>CacheHits
<screenPlan|vectorTemplate>CacheMisses
<screenPlan|vectorTemplate>Builds
<screenPlan|vectorTemplate>CacheAdmissions
<screenPlan|vectorTemplate>CacheEvictions
<screenPlan|vectorTemplate>CacheBypasses
<screenPlan|vectorTemplate>BuildUnits
<screenPlan|vectorTemplate>CacheCurrentEntries
<screenPlan|vectorTemplate>CacheCurrentLogicalBytes
<screenPlan|vectorTemplate>CachePeakEntries
<screenPlan|vectorTemplate>CachePeakLogicalBytes
```

All values are non-negative checked `long` JSON integers; overflow fails the sample rather than
saturating. Screen build units are newly owned render coordinates; template build units are path
commands plus ordinates. Existing optimized rows additionally report `screenPlanBuilds`, and the
original `symbol-heavy-render` reports `vectorTemplateBuilds`, so reduction has an exact same-workload
denominator. These counters are observation facts, not public production telemetry. Current and peak
logical bytes use the declared formulas; allocation remains optional JFR evidence.

Raw nanos, exact integer median, nearest-rank p95, and existing throughput remain authoritative.
Candidate comparisons use checked `BigInteger` cross-products, never floating ratios:

```text
atMost(candidate, baseline, p/q) iff q*candidate <= p*baseline
atLeast(part, whole, p/q)       iff q*part >= p*whole
buildReduction >= p/q           iff q*(baselineBuilds-candidateBuilds) >= p*baselineBuilds
```

Negative differences, zero denominators, overflow before `BigInteger` conversion, missing rows,
semantic mismatch, filtered investigation, noncanonical profile/configuration, bypass/eviction where
forbidden, or counter inconsistency means `not evaluated` or rejection as specified below; it never
silently passes.

### Predeclared retain/delete rules

Only one unfiltered canonical `BASELINE` run with the task-owned JVM settings and every comparison row
may decide production retention. The screen-plan candidate is retained if and only if all conditions
hold:

- each warm dense, pan, and zoom row has hit rate at least 4/5 and reduces screen-plan builds by at
  least 4/5 versus its unchanged optimized row;
- at least two of those three warm medians are at most 9/10 of their optimized median, and none is
  greater than 21/20;
- cold dense, pan, and zoom medians are each at most 21/20 of their optimized median, and the cold
  small median is at most 11/10 of `small-vector-render-optimized`;
- every semantic digest matches, every cache/counter/budget invariant passes, and the default-budget
  fixed rows report zero bypasses and evictions.

The vector-template candidate is retained if and only if its warm symbol row has hit rate and build
reduction at least 99/100, its warm median is at most 19/20 of the unchanged `symbol-heavy-render`
median, its cold median is at most 51/50, its semantic digest matches, all invariants pass, it reports
zero bypasses/evictions, and peak logical retained bytes do not exceed 4,194,304.

Equality passes. A rejected candidate is removed completely rather than left disabled, documented as
future work, or selected at runtime. JFR may corroborate where time/allocation moved, but cannot waive
a formula. Duration rules are a one-time complexity decision on the recorded reference environment;
`performanceEvidence` continues to fail only on configuration, semantics, counters, cleanup, or
report construction and never becomes a recurring wall-clock quality gate.
`performanceQuick` always reports the noncanonical SMOKE/investigation configuration and therefore
cannot enter these formulas even if its timings appear favorable.

### Checked-in acceptance record and G7 closeout

Implementation completion replaces the pending cells in this concise table and records the source
report SHA-256, exact revision, reference Java/OS/CPU-count/heap/GC/configuration, candidate counters,
median/p95 cross-products, retained/rejected result, final budgets, known limits, and any unmet
advisory goal. Raw reports/JFR remain ignored build artifacts. No number is described as a portable
FPS, latency SLA, heap measurement, or cross-platform guarantee.

| Candidate | Canonical reference evidence | Decision | Final limits/known limits |
| --- | --- | --- | --- |
| Screen plan | Canonical BASELINE report JSON SHA-256 `d53ff058919ff6fee178ec3ee86d0bd5ce540fe602adca686be287b763c0d585` | Rejected and removed | Warm pan had 0 hits, 6,104 builds, and 6,104 evictions because the working set exceeded 32 MiB |
| Vector template | Same report; Markdown SHA-256 `f7e53388585ff1fc9fec1bed4e07fbf3907ff145108a7653033ea74583a595` | Retained | 512 entries, 4 MiB logical total, 256 KiB per entry |

Tests pin exact keys and identity misses, weights and limit edges, successful-hit promotion, LRU ties,
oversized no-eviction, build/use/admission order, source publication/cancellation, binding purge, close,
EDT confinement, immutable templates, custom-renderer bypass, authoritative interactions/endpoints,
and cached/uncached portable rendering equivalence. Performance tests pin append-only scenario order,
profile cardinalities, cold clearing, zero-warmup preseed, operation-local deltas, counter order/units,
oracle inheritance, comparison arithmetic, candidate removal, filtered `not evaluated`, and one
deterministic acceptance rendering. Architecture tests forbid public/generic/global cache state, core
or format cache code, raster duplication, public metrics/modes, external/native dependencies, and
prohibited mechanisms. Validation includes AWT/performance/architecture checks,
`renderRegression`, the under-five-minute reference run of `performanceQuick`,
`performanceEvidence`, `qualityGate`, and whitespace.

G7 therefore closes with one canonical evidence lane plus one explicitly noncanonical quick lane, one
explicitly selected packed in-memory index, one fixed operation-local screen optimizer, and zero or
one private MapView cache owner containing only evidence-retained typed partitions. G6 remains the
sole raster-pixel cache. Ordinary developers choose
`InMemoryFeatureSource.openIndexed(...)` explicitly when an index is wanted; MapView otherwise has
fixed invisible defaults. There is no public performance policy, generic cache/index/topology
framework, automatic data-structure chooser, source LOD, native acceleration, or duplicate rendering
state. This is the smallest design that preserves bounded work and leaves observable complexity only
where recorded evidence pays for it.

### Final G7-004 decision record

The one canonical source run used Java `21.0.11+10-1-24.04.2-Ubuntu` on Linux
`5.15.167.4-microsoft-standard-WSL2`/amd64 under WSL2, with 32 reported processors, a 512 MiB maximum
heap, G1, headless AWT, seed `0x4d554e44414e454a`, five warmups, and twenty measurements. Both the
ordered runtime classpath and generated fixture/report work ran in one invocation-unique real
`/tmp` tree. The report has no revision field because it measured the uncommitted G7-004 candidate
worktree based on `d260e0b`; the two report checksums above bind the exact evidence instead. The run
completed successfully in 2m15s after removing `/mnt/d` filesystem traffic from the timed process.

The screen-plan candidate failed its exact behavior rules independently of timing. Its warm pan row
had 6,104 requests, zero hits, 6,104 builds, and 6,104 evictions at a 4,149-entry/33,551,684-byte end
state (4,153 entries/33,554,432 bytes peak). The 16-position trace has a working set larger than the
32 MiB provisional budget, so sequential LRU access thrashes. The partition, modes, evidence rows,
metrics, oracles, decision branch, and candidate-only tests were deleted. G7-003's operation-local
screen plan remains unchanged and uncached.

The vector-template candidate passed every declared rule. Against the unchanged symbol median of
21,677,854 ns (p95 24,107,840 ns), cold median was 18,690,680 ns (86.22%; p95 20,010,715 ns) and warm
median was 18,386,448 ns (84.82%; p95 19,304,863 ns). The cold row made 4,352 requests, 4,343 hits,
nine misses/builds/admissions, and retained nine entries/4,869 logical bytes; the warm row made 4,352
hits with no build. Both reported zero eviction and bypass. Production therefore enables only this
private view-owned AWT partition with the final 512-entry, 4 MiB total, and 256 KiB per-entry limits.
Public API and configuration remain unchanged; view close clears the partition, and G6 remains the
sole raster-pixel cache.

After the decision, the recurring full and quick reports contain the retained vector comparison only.
`performanceQuick` runs all 41 final SMOKE scenarios with one warmup/two measurements, is always
investigative/`NOT_EVALUATED`, and completed in 7.89–10.92 seconds during reference checks. The
canonical report remains an ignored source artifact rather than checked-in generated output. The
timings justify this one private implementation decision; they are not a portable SLA or runtime
policy.
