# G7 — Performance and indexing design

Project index: [DESIGN.md](../DESIGN.md).

## Performance evidence baseline (G7-001)

### Evidence-only support boundary

G7-001 adds no production algorithm, API, cache, threshold, or dependency. It creates one non-published
support project, `modules/mundane-map-performance-tests`, only when that project can execute all twelve
real-stack scenarios below. The authoritative project inventory classifies it as Support, includes its
small JVM checks/style/Javadocs in `qualityGate`, and excludes it from publication, Level 1 runtime,
Native Image, corpus, render-regression, and consumer graphs. Main dependencies are exactly the API,
core, AWT, shapefile, and image modules; it never consumes examples or another project's test output.

The project uses the ordinary Java-library conventions. `src/main/java` owns a public launcher solely
for `JavaExec`, package-private harness/scenario/report code, and support-only fixture generators.
`src/test/java` owns the harness/statistics/report tests and one reduced smoke execution. Fixed encoded
PNG/JPEG evidence resources and their provenance may be main resources because the project is never
published; generated shapefiles and all reports live beneath its `build/` tree. No production package
may depend on this project or its fixture code.

The module task `runPerformanceEvidence` and root task `performanceEvidence` are introduced here. The
root task depends only on that execution, uses a Java 21 launcher even when Gradle runs newer, and
supplies exact defaults `-Xms512m`, `-Xmx512m`, G1, headless AWT, UTF-8, `en-US`, and UTC. It writes
UTF-8/LF files beneath `build/performance-evidence/`: `evidence-v1.json` plus a deterministic
`evidence-v1.md` rendering. `check`, `checkAll`, and `qualityGate` must not depend on the full run.

A separate Ubuntu 24.04/Java 21 performance-evidence CI job first runs
`:modules:mundane-map-performance-tests:classes` with ordinary dependency resolution, then runs
`./gradlew --offline performanceEvidence --rerun-tasks --console=plain` and uploads the two reports.
Gradle dependency resolution is build setup; once the runner begins, scenario code performs no
network, process launch, home-directory lookup, download, or external-data access. The job fails on
configuration, fixture, semantic, cleanup, or report errors, never because a duration is high. It is
evidence, not a required portable speed claim.

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
| `SMOKE` | same | 1 | 2 | Normal module tests with reduced cardinalities |

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
They do not compare whole images or cross-platform pixel counts. Toolkit-neutral records, queries,
viewports, and PNG samples use exact ordered digests; JPEG probes use the established tolerance away
from block edges. Reports separate source cache state
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

Rendering mixes ordered `(invariantName, classification)` pairs, never raw pixels. Exact classes are
`BACKGROUND_MAJORITY`, `EXPECTED_COLOR_MAJORITY`, `EXPECTED_ALPHA_MAJORITY`,
`PAINT_BOUNDS_CONTAINED`, `PAINT_COUNT_IN_RANGE`, and `VIEWPORT_MATCH`; failed color/tolerance or bounds
checks terminate before digesting. A color majority requires more than half of the fixed region's
pixels to have every declared RGBA channel within the scenario's integer tolerance. Implementation
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
  settings=profile,dumponexit=true,disk=true,maxsize=512m
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

Harness tests pin seed/fixture reproducibility, scenario ID/order uniqueness, `BASELINE` versus
`SMOKE`, setup/timing/verification separation, exact iteration counts, warmup exclusion, raw samples,
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
