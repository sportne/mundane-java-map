# G15 — Live-track stress and IOU tracking design

Project index: [DESIGN.md](../DESIGN.md)

Roadmap: [ROADMAP.md](../ROADMAP.md)

Tasks: [tasks/README.md](../tasks/README.md#g15--live-track-stress-and-iou-tracking)

## Purpose and status

G15 proposes one deliberately demanding JVM example: simulate, estimate, and display 10,000,
100,000, or 1,000,000 individually evolving tracks over a simple global land chart. Each track emits
stochastic position reports no more often than once per second and no less often than once per
minute. A forward Integrated Ornstein-Uhlenbeck (IOU) Kalman estimator produces the displayed
position. The viewer reports achieved frame rate and accepts an explicit maximum frame-rate cap.

This is a stress and architecture-evidence capability, not a general tracking framework or a
navigation-grade operational system. The first implementation remains inside a new
`examples:live-track-stress` project. A public tracking API or production module requires a second
consumer and a later task.

G15-001 is approved and G15-002 is complete. The decision freezes the mathematical, provenance,
workload, storage, lifecycle, rendering, and evidence profile below; the first implementation slice
proves its packed estimator kernel. G15-003 through G15-008 remain Proposed until their working
vertical slices pass review. Neither completed task creates a production API or module.

## Research and provenance boundary

The public mathematical basis is Paul W. Vebber's 1991 Naval Postgraduate School thesis,
*An Examination of Target Tracking in the Antisubmarine Warfare System Evaluation Tool (ASSET)*.
The thesis describes Kalman estimators using IOU target-motion statistics, attributes operational
development to Daniel H. Wagner Associates, and cites earlier Wagner memoranda and lecture notes. It
provides the IOU stochastic differential equation and state-space derivation needed for an
independent implementation:

`dv = -beta * v * dt + sigma * dW`

Source: [NPS thesis PDF](https://upload.wikimedia.org/wikipedia/commons/a/aa/An_examination_of_target_tracking_in_the_Antisubmarine_Warfare_System_Evaluation_Tool_%28ASSET%29._%28IA_examinationoftar00vebb%29.pdf).

G15 does not reproduce historical source code, claim identity with a proprietary implementation, or
use the name to imply endorsement. G15-001 records the equations, bibliography, independent-
implementation basis, and approved wording. A source search is provenance evidence, not legal
clearance; any distribution concern is a maintainer checkpoint.

The bundled chart is Natural Earth `ne_110m_land` version `4.1.0`, obtained from the official
[1:110m physical-vectors page](https://www.naturalearthdata.com/downloads/110m-physical-vectors/).
Natural Earth's [terms of use](https://www.naturalearthdata.com/about/terms-of-use/) identify the
site's raster and vector data as public domain. G15-004 records the exact upstream version, archive
URL, files retained, hashes, retrieval date, terms snapshot/reference, and any transformation recipe.
The repository bundles only the small land shapefile sidecars required by the example; runtime
downloads are forbidden.

## Architectural placement

The example composes existing project boundaries rather than widening them:

```text
Natural Earth SHP/SHX/DBF/PRJ
        |
        v
mundane-map-io-shapefile --> MapView land binding (ordinary map stack)
                                      |
truth simulator --> IOU filters --> packed display snapshot --> AWT track overlay
       |                |                     |                         |
       +------- worker-owned shards ----------+                 EDT paints latest frame
```

- `mundane-map-io-shapefile` opens the bundled land chart through existing public contracts.
- `MapView` owns viewport, projection, land rendering, navigation, and lifecycle on the EDT.
- Example-owned packages contain truth generation, report scheduling, IOU filtering, worker
  coordination, packed frame planning, telemetry, and the specialized overlay.
- Swing, Java2D, `BufferedImage`, timers, and desktop controls remain in the example and AWT boundary.
- No production module gains a live-source, tracker, scheduler, thread-pool, or million-feature API.
- No new runtime dependency is introduced.

The overlay is intentionally not a new generic `MapLayerBinding`. Existing feature sources are
synchronous immutable snapshots and are correct for ordinary data, but allocating/publishing up to
one million `FeatureRecord` values each frame would make object churn—not tracking or rendering—the
stress subject. The example therefore owns one specialized, non-intercepting transparent Swing
component above `MapView`. It draws only a detached latest frame and never changes map interaction
semantics.

## Fixed first profile

### Approved constants and support wording

The reference estimator uses `beta = 0.05 s^-1`, driving-noise magnitude
`sigma = 20 m s^-3/2`, and isotropic position-measurement standard deviation `5,000 m`. Configurable
test/evidence values are limited to finite `beta` in `[1e-6, 1]`, `sigma` in `(0, 1,000]`, and
measurement standard deviation in `(0, 1,000,000]`. Prediction intervals are finite and in
`[0 s, 60 s]`; authoritative reports must advance time by an integer `1` through `60` seconds.

The approved public description is: **an independently implemented, forward, position-only
IOU-Kalman Filter state estimator derived from public continuous state-space equations for a bounded
stress simulation**. It is not historical source code, a claim of equivalence to any proprietary or
operational implementation, an endorsement, or an operational accuracy/safety claim. The 1991 NPS
thesis is the mathematical/provenance source and is marked for unlimited public distribution; its
historical application parameters are not copied into this synthetic workload.

For `z = beta * delta`, implementations use `-StrictMath.expm1(-z)` for `1 - exp(-z)`. When
`z < 1e-3`, fixed Taylor polynomials through the `z^4` term evaluate `a/delta`,
`Q11/(sigma^2*delta^3)`, `Q12/(sigma^2*delta^2)`, and `Q22/(sigma^2*delta)` respectively:

```text
1 - z/2 + z^2/6 - z^3/24 + z^4/120
1/3 - z/4 + 7z^2/60 - z^3/24 + 31z^4/2520
1/2 - z/2 + 7z^2/24 - z^3/8 + 31z^4/720
1 - z + 2z^2/3 - z^3/3 + 2z^4/15
```

The first report at simulation second zero initializes position to the measurement, velocity to
zero, position variance to the measurement variance, position/velocity covariance to zero, and
velocity variance to `sigma^2/(2*beta)`. X and Y share the three covariance scalars because identical
initial covariance, dynamics, timing, and isotropic measurement variance preserve that invariant;
means remain separate. Updates use the scalar Joseph form and explicitly restore symmetry. For
finite covariance, `diagonalScale = max(1, abs(P00), abs(P11))` and
`diagonalTolerance = 64 * ulp(diagonalScale)`; a diagonal below `-diagonalTolerance` is rejected and
an individual diagonal in `[-diagonalTolerance, 0)` is set to zero. Then
`determinantScale = max(1, abs(P00 * P11) + P01 * P01)` and
`determinantTolerance = 64 * ulp(determinantScale)`. A determinant below
`-determinantTolerance` is rejected. A negative determinant within tolerance is repaired to the PSD
boundary by replacing `P01` with `copySign(sqrt(P00 * P11), P01)` (or zero when either diagonal is
zero), then rechecked. Non-finite covariance is always rejected. Display prediction is pure and does
not mutate the authoritative state, timestamp, covariance, or counters.

### Population and report stream

- Population tiers are exactly 10,000, 100,000, and 1,000,000 live tracks.
- A run has a fixed population. Birth, death, identity correlation, report association, merging,
  splitting, and duplicate identities are out of scope.
- Every report carries its already-associated integer track ID, event time, measured X/Y position,
  and deterministic sequence number.
- Each track has an independent renewal schedule with an interval in the closed range `[1 s, 60 s]`.
  The interval is a geometric variate with success probability `0.1`, capped at `60`: for a uniform
  53-bit `u` in `[0,1)`,
  `min(60, 1 + floor(StrictMath.log1p(-u) / StrictMath.log(0.9)))`. Its exact expected interval is
  `(1 - 0.9^60) / 0.1`, approximately `9.9820299 s`, or about `100,180` steady-state reports/second
  at one million tracks. The population-sized second-zero initialization reports are counted
  separately from scheduled steady-state reports.
- Event time, rather than worker arrival time, drives prediction. The first profile rejects
  out-of-sequence reports rather than adding smoothing or rollback.
- The simulator must not silently coalesce or drop reports. Scheduled, processed, rejected, late,
  and pending counts are independently observable.

### Truth motion and measurements

Each track is simulated individually in projected metre coordinates. The first truth model is a
bounded random-tour process with speed and course perturbations, world-X wrapping, and latitude/Y
reflection or turn-back at a declared Web Mercator latitude ceiling. Position reports add
zero-mean Gaussian X/Y measurement error from a deterministic per-track random stream.

Simulation and tracking operate in EPSG:3857 so IOU position, velocity, covariance, and measurement
noise use metres and seconds. The bundled EPSG:4326 land data is transformed by the existing
explicit CRS registry. Tracks remain within the declared Web Mercator domain. This is a stress-model
coordinate choice, not a global geodesic tracking claim.

A global seed plus track ID derives independent deterministic random streams. A fixed configuration,
seed, population, worker count, and elapsed simulation time must reproduce the same truth reports and
estimates regardless of wall-clock speed. Tests use a deterministic virtual clock.

The reference seed is hexadecimal `0x4d554e44414e454c`. Schedule, truth, and measurement draws are
counter-based `SplitMix64` mixes; no generator object or mutable random state exists per track. The
mix function is exactly `z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9`, then
`z = (z ^ (z >>> 27)) * 0x94d049bb133111eb`, then `z ^ (z >>> 31)`, with Java `long` wraparound.
Starting at the seed, apply that mix after XOR with, in order, the stream tag, unsigned track ID,
report sequence, and draw index. Tags are `0x5343484544554c45` (schedule),
`0x54525554484d4f54` (truth), and `0x4d4541535552454d` (measurement). A uniform is
`(raw >>> 11) * 0x1.0p-53`; Box-Muller uses
`u1 = ((raw1 >>> 11) + 1) * 0x1.0p-53`, `u2 = (raw2 >>> 11) * 0x1.0p-53`, and strict
`StrictMath.sqrt(-2*StrictMath.log(u1)) * StrictMath.cos(2*StrictMath.PI*u2)` and the same radius
times `StrictMath.sin(2*StrictMath.PI*u2)` for the even/odd member of a pair. Pair `p` uses draw
indices `2*p` and `2*p+1`. Report sequence zero with the truth tag uses uniform draw indices 0, 1,
2, and 3 for initial X, Y, course, and speed; measurement-tag pair zero supplies the initial report's
X/Y errors, and schedule-tag draw zero selects the first scheduled interval. Each scheduled report
sequence `q >= 1` uses truth-tag pair zero (draw indices 0 and 1) for that leg's speed and course
perturbations, measurement-tag pair zero for its X/Y errors, and schedule-tag draw zero to select the
following interval. Thus sequence separates legs while tag and index separate purposes. All
`N(mean, standardDeviation)` notation below names the standard deviation, not variance. Truth starts
uniformly over wrapped
world X and the Web Mercator Y range for latitude `[-80°, 80°]`, with course uniform in `[0, 2*pi)`
and speed uniform in `[30, 250] m/s`. Each leg first propagates its prior constant speed/course, wraps
X at `±20,037,508.342789244 m`, reflects Y at `±15,538,711.09630922 m` and reverses the course's
north/south component before the next perturbation, then perturbs the next-leg
speed by `N(0, 3*sqrt(delta)) m/s` and course by `N(0, 0.005*sqrt(delta)) rad`; speed is clamped to
`[30, 250] m/s`. Measurement X/Y add independent `N(0, 5,000) m` errors.

### IOU-Kalman Filter state estimator

The forward estimator state is `[x, y, vx, vy]`. X and Y are independent instances of the same
continuous model:

```text
dp = v dt
dv = -beta * v dt + sigma dW
```

For `e = exp(-beta * delta)` and `a = (1 - e) / beta`, each axis uses:

```text
F = [ 1  a ]
    [ 0  e ]

Q11 = sigma^2 / beta^2 *
      (delta - 2 * (1 - e) / beta + (1 - e^2) / (2 * beta))
Q12 = sigma^2 * (1 - e)^2 / (2 * beta^2)
Q22 = sigma^2 * (1 - e^2) / (2 * beta)
```

The implementation uses stable `StrictMath.expm1`-based forms near zero and exact limiting behavior
for a task-approved small `beta * delta` region. Measurements are position-only, with an approved
isotropic error variance. The first report initializes position, zero velocity, and declared finite
covariance. Prediction and measurement update preserve finite symmetric positive-semidefinite
covariance within documented numerical tolerance; the update uses the Joseph form unless G15-001
approves an algebraically equivalent scalar form with stronger evidence.

The production-shaped kernel is scalar and allocation-free per report. Struct-of-arrays storage holds
mean, covariance, timestamp, initialization state, and counters. Under the approved symmetric
profile, both axes may share the three independent covariance entries only if tests prove that the
invariant is preserved. A generic dense 4-by-4 test oracle independently evaluates the same equations
and is never used in the measured path.

G15-002 implements this boundary inside `examples:live-track-stress`. One packed filter owns
structure-of-arrays mean, shared covariance, timestamp, initialization, and counter storage plus one
reused five-double coefficient scratch array. Its update path allocates nothing, rejects intervals
outside `[1 s, 60 s]`, uses the approved Taylor/direct coefficient branches and Joseph update, and
applies the approved finite/PSD checks. The filter is deliberately shard-confined rather than
thread-safe. Tests compare irregular updates against an independently coded dense 4-by-4 oracle,
exercise the small-decay limit and storage isolation, and run deterministic innovation/RMSE sanity
evidence. The runnable entry point exercises the same packed kernel without introducing a public
tracking abstraction.

The displayed position predicts the current estimate to the frame's simulation timestamp without
mutating authoritative filter state. Backward smoothing, adaptive noise, interacting multiple
models, historical Wagner variants, bearing-only reports, velocity reports, sensor bias, data
association, and distributed fusion remain out of scope.

## Packed execution model

### Track storage

Population-sized state uses checked packed primitive arrays, organized as struct-of-arrays. There
are no per-track objects, futures, random generators, matrices, locks, or scheduled tasks. Logical
storage bytes and largest allocation are computed before construction and reported. The 1m profile
must fail predictably before allocation when configured heap or implementation ceilings cannot hold
the run.

### Scheduling

A bounded timing wheel schedules due reports by simulation time. It has exactly 64 one-second slots,
uses integer simulation seconds, and maintains primitive bucket heads plus one primitive next-link
per track. Each track occurs in exactly one bucket. Processing a tick
visits due tracks, emits their reports, advances their truth/filter state, and requeues them. The
normal path does not scan all tracks to discover due work.

### Worker ownership

An explicit coordinator creates a fixed number of long-lived worker shards. Every track ID maps to
one stable shard, and only that shard mutates its truth, schedule, and filter entries. The EDT never
touches mutable track arrays. Cross-shard work stealing is excluded from the first profile because it
would complicate deterministic ownership; the evidence report exposes per-shard work skew.

The coordinator supports start, pause, reset, and close. Cancellation is checked at bounded report
and frame intervals. Close stops new frame requests, cancels workers, joins them outside the EDT,
releases frame buffers, closes the map/source, and leaves no non-daemon worker alive.

Worker counts are integers in `[1, min(32, population)]`; the default is
`min(8, Runtime.availableProcessors())`, with a floor of one. Shards are deterministic contiguous
track-ID ranges formed by quotient/remainder partitioning. Real-time operation advances every due
integer simulation second in order and accumulates backlog instead of skipping time. Pause freezes
simulation time; reset is allowed only after workers and frame production have quiesced; close is
idempotent and joins workers off the EDT.

### Frame production and handoff

At a requested display instant, workers or a bounded frame executor predict each estimate to that
instant, project it through an immutable viewport snapshot, and rasterize a minimal fixed track mark
into a pooled packed ARGB frame. Work is partitioned by stable track ranges. A deterministic
composition rule prevents shard scheduling from changing pixels.

The frame handoff has at most one published frame and one in-progress request. A completed frame
contains its viewport generation, dimensions, simulation timestamp, population, and counters. The
EDT atomically acquires the newest completed frame and paints it only when generation and dimensions
still match; otherwise it records a stale-frame discard. Buffer ownership transfers explicitly from
producer to EDT and then to a bounded pool. Producers never mutate a published or painted buffer.

The first profile uses a tiny fixed, non-antialiased or otherwise explicitly qualified mark, not the
general symbol renderer once per track. The land chart still exercises the real map stack. This
keeps the stress evidence focused on simulation, estimation, projection, and dense display. A later
task may compare richer symbols only after evidence identifies that as a useful workload.

The mark is one opaque `2 x 2` ARGB square with value `0xff16d9e3`. Frame dimensions are positive
and capped at `8,388,608` pixels. At most three packed `int[]` buffers move exclusively among the
available, producer-owned, pending-published, and EDT-owned states. A producer atomically replaces
the pending buffer and returns any displaced pending buffer to available. The EDT atomically takes
the pending buffer, owns it through painting, and only then returns it to available. It may continue
painting a previously acquired buffer while a producer fills or publishes another. The preflight
frame bound is therefore `3 * width * height * 4` checked bytes. There is exactly one active frame
request. No producer mutates a pending-published or EDT-owned buffer.

## Frame pacing and telemetry

An EDT timer requests frames at one of the exact caps `1`, `2`, `5`, `10`, `15`, `30`, or `60` FPS,
or in an explicit uncapped mode. The default and reference workload use a 10 FPS cap together with
the approximately 100,000-report/second 1m configuration. This is the target configuration to
measure, not a portable minimum-FPS assertion. A cap limits requests and never sleeps or busy-waits
on the EDT. Uncapped mode issues at most one request per timer callback. If a prior request remains
in progress, the timer records a skipped request rather than enqueueing an unbounded backlog.

Achieved FPS is completed, EDT-painted, generation-valid frames over a fixed rolling interval. The
UI and evidence reports distinguish:

- requested maximum FPS, requested frames, completed frames, painted frames, skipped requests, and
  stale discards;
- frame-build p50/p95/p99 and maximum duration;
- scheduled, processed, rejected, late, and pending reports per second;
- truth/filter CPU work by shard and observed shard skew;
- population, seed, simulation time, wall time, worker count, heap ceiling/usage, logical state
  bytes, frame-buffer bytes, viewport, and JVM/OS/CPU metadata;
- deterministic position-error RMSE and normalized-innovation sanity summaries.

The UI updates human-readable telemetry at a bounded low rate independent of track-frame cadence.
Telemetry accumulation uses primitive counters/histograms and must not allocate per report or frame.
The UI refreshes telemetry once per second. Achieved FPS is a rolling five-second count of completed,
generation-valid frames actually painted by the EDT.

Preflight uses a conservative hard logical ceiling of `192` bytes per track and a largest permitted
single population allocation of `8 * population` bytes, plus the checked three-frame bound above.
Construction is rejected before any population allocation unless exact logical bytes plus `256 MiB`
headroom both fit the configured maximum heap and remain at or below 60 percent of that heap. Reports
break logical storage down by primitive array and report observed heap separately; logical array
bytes are not presented as JVM retained-heap measurements.

## Verification lanes

G15 uses two dedicated JVM lanes because one-million-track evidence does not belong in the normal
quality gate:

- G15-005 creates `./gradlew liveTrackSmoke --console=plain`. It runs a deterministic headless 10k
  end-to-end scenario, validates counts, finite estimator state, frame publication, stale-frame
  behavior, and cleanup, and is bounded to complete comfortably within five minutes on the project
  baseline machine.
- G15-007 creates
  `./gradlew liveTrackEvidence -PliveTrackProfile=<10k|100k|1m> --console=plain`. It uses a native
  `/tmp` work directory by default, records warmup and measurement phases, and writes machine-readable
  JSON plus concise LLM-readable Markdown under `build/reports/live-track/`.

The canonical evidence run is a 10-second warmup followed by a 60-second measurement for each tier.
The smoke lane advances 120 virtual seconds, builds a bounded number of frames, and has a five-minute
wall-clock timeout. Correctness, bounds, cleanup, and report completeness are gates. Achieved
FPS and throughput are evidence, not portable wall-clock pass/fail thresholds. Every report records
the cap and whether the run was CPU, frame-cap, or backlog limited. Full evidence is opt-in and is not
part of `qualityGate`, `performanceEvidence`, or ordinary CI.

Evidence uses schema identifier `mundane-map-live-track-evidence/v1`. Its required top-level JSON
members are `schema` (that exact string), `runId` (string), `profile` (`10k`, `100k`, or `1m`),
`status` (`SUCCESS`, `CANCELLED`, or `FAILED`), `limitations` (array containing only `CPU_LIMITED`,
`FRAME_CAP_LIMITED`, `BACKLOG_LIMITED`, or `INDETERMINATE`), `configuration`, `environment`,
`phases`, `storage`, `telemetry`, and `cleanup` (objects), and `diagnostics` (array). Configuration
contains numeric population, seed, workers, FPS cap/null for uncapped, beta, sigma, measurement
standard deviation, warmup seconds, and measurement seconds. Phases separates initialization,
warmup, and measurement counters. Storage contains checked logical bytes by primitive array, largest
allocation, frame bytes, maximum heap, and observed heap. Telemetry contains the named counters,
rates, latency quantiles, shard skew, RMSE, and innovation summaries from this design. Cleanup records
worker termination and resource closure booleans. Diagnostics contain stable category, severity, and
message strings. Additive fields are permitted; required fields are never omitted, even on failure.
Work is created below
`/tmp/mundane-java-map-live-track/<run-id>/`; finalized JSON and concise Markdown are copied to
`build/reports/live-track/live-track-<profile>.json` and `.md`, where profile is exactly `10k`,
`100k`, or `1m`. Each report includes configuration, environment, initialization versus scheduled
work, telemetry, terminal status, and categorized problems or limitations. Cancellation and failure
still attempt a terminal report. Each file is written to a sibling `.part`, closed, and atomically
moved with replacement when supported, with a same-directory replace fallback. A later run for the
same profile intentionally replaces the previous finalized pair. No result is described as successful
if required fields or cleanup evidence are absent.

The deterministic kernel, simulator, and smoke checks use a headless path. G15 does not add a Native
Image claim: the user-facing stress viewer is Swing/JVM evidence and the example is not a published
runtime module. A later task needs an explicit value proposition before adding native workload.

## Rendering and interaction behavior

The runnable example provides:

- population selector for 10k, 100k, and 1m before allocation;
- seed, worker-count, report-profile, and FPS-cap controls;
- start, pause/resume, reset, fit-world, and close behavior;
- normal `MapView` pan/zoom interaction with a non-intercepting track overlay;
- a simple global Natural Earth land chart and a visually distinct track color;
- bounded telemetry visible without obscuring the map.

The first visual profile uses ocean `#081520`, land fill `#435e4b`, land outline `#789080`, and track
marks `#16d9e3`. The bundled dataset is Natural Earth `ne_110m_land` version `4.1.0`, acquired from
`https://naciscdn.org/naturalearth/110m/physical/ne_110m_land.zip`, whose approved archive SHA-256 is
`1926c621afd6ac67c3f36639bb1236134a48d82226dc675d3e3df53d02d2a3de`. G15-004 retains the
upstream SHP, SHX, DBF, PRJ, and CPG members without rewriting them. Its manifest records the archive
URL, UTC retrieval date, archive and retained-member SHA-256 hashes, version page, and terms URL. An
absent optional member in a future approved archive would be recorded as absent, not synthesized.

Changing population or structural configuration performs an explicit stopped reset. Pause freezes
simulation time and display prediction. View navigation may invalidate in-flight frames; it never
blocks waiting for them. The overlay does not participate in selection, hover, labels, or individual
track inspection in the first profile.

## Failure and overload policy

- Invalid configuration, non-finite parameters, allocation overflow, unsupported heap envelope, and
  impossible population sizes fail before workers start.
- A late processing interval is recorded. Reports are not silently discarded to preserve display
  cadence.
- At most one frame request is outstanding. Frame demand is skipped under overload; report work is
  authoritative.
- Non-finite truth/filter state terminates the run with the track ID, simulation timestamp, and one
  stable example diagnostic category. It does not continue painting corrupt estimates.
- A worker failure cancels the whole run and is surfaced on the EDT without leaking a buffer or
  thread.
- The evidence lane always attempts to write a terminal report describing success, cancellation, or
  failure.

Stable diagnostics are example-owned records rather than additions to the public source diagnostic
catalog. Secrets, host paths outside the report's designated workspace, or full per-track traces are
not emitted.

## Task boundaries

- G15-001 approves the IOU-Kalman Filter state-estimator, workload, coordinate, scheduling, rendering, evidence, provenance,
  and support profile.
- G15-002 creates the example project and proves the optimized IOU-Kalman Filter state estimator against an independent
  dense oracle.
- G15-003 adds deterministic packed truth/report simulation, timing-wheel scheduling, and stable
  shard ownership.
- G15-004 bundles and displays the Natural Earth chart with exact provenance and visual approval.
- G15-005 completes the first 10k live picture and creates the fast smoke lane.
- G15-006 scales the same behavior to 100k using measured sharding and packed frame construction.
- G15-007 enables the 1m tier and creates the opt-in evidence/report lane.
- G15-008 hardens lifecycle, overload, replay, documentation, and holistic simplicity.

G15-002/G15-003 and G15-004 are dependency-parallel after G15-001, but all touch the example
registration eventually; one integration owner must serialize shared Gradle/settings, design index,
task index, and roadmap files. G15-005 through G15-008 are serial because each extends the same
viewer, coordinator, telemetry, and evidence contracts.

## Explicit exclusions

- No public tracker API, generic live-feature source, streaming/reactive API, or new production
  module.
- No data association, multi-sensor fusion, smoothing, historical replay UI, persistence, networking,
  track birth/death, labels, selection, trails, uncertainty ellipses, or military symbology.
- No general Earth dynamics, geodesic Kalman filter, ECEF model, terrain interaction, or operational
  accuracy/safety claim.
- No JTS, SIS, GDAL, H3, PROJ, native code, JNI, FFM, GPU, vector API, or external benchmark library.
- No benchmark pass/fail based on portable wall-clock FPS and no claim that WSL `/mnt/d` results
  characterize native Linux or other hardware.
- No runtime download or discovery of map resources.

## Gate closeout rule

G15 closes only after the 10k smoke passes, all three tiers produce complete evidence reports on a
named machine, the one-million-track run remains bounded and controllable, lifecycle tests leave no
workers/resources behind, the Natural Earth provenance is approved, and the maintainer accepts the
visual/telemetry outcome. Closeout must re-evaluate whether the example-specific overlay and packed
kernel should remain local. They stay local unless demonstrated reuse makes a smaller public
abstraction possible.
