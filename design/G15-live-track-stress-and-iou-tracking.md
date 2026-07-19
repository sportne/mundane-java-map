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

The design and G15 task cards are Draft. G15-001 must approve the precise mathematical profile,
provenance, workload distribution, and support wording before implementation.

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

The bundled chart is Natural Earth `ne_110m_land`, obtained from the official
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

### Population and report stream

- Population tiers are exactly 10,000, 100,000, and 1,000,000 live tracks.
- A run has a fixed population. Birth, death, identity correlation, report association, merging,
  splitting, and duplicate identities are out of scope.
- Every report carries its already-associated integer track ID, event time, measured X/Y position,
  and deterministic sequence number.
- Each track has an independent renewal schedule with an interval in the closed range `[1 s, 60 s]`.
  G15-001 freezes the deterministic distribution and default mean. The proposed default mean is
  approximately 10 seconds, which yields about 100,000 scheduled reports/second at one million
  tracks while preserving the user's interval bounds.
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

A bounded timing wheel schedules due reports by simulation time. The default design has fixed
one-second slots covering the maximum 60-second interval; G15-001 may approve a finer fixed quantum
if required for the stochastic profile. Each track occurs in exactly one bucket. Processing a tick
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

## Frame pacing and telemetry

An EDT timer requests frames at a user-selected maximum rate. The supported cap includes common
values through 60 FPS and an explicit uncapped mode; G15-001 freezes the exact controls. The proposed
reference workload uses a 10 FPS cap together with the approximately 100,000-report/second 1m
configuration. This is the target configuration to measure, not a portable minimum-FPS assertion. A
cap limits requests and never sleeps or busy-waits on the EDT. If a prior request remains in progress,
the timer records a skipped request rather than enqueueing an unbounded backlog.

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

The proposed canonical run is a 10-second warmup followed by a 60-second measurement for each tier;
G15-001 freezes durations. Correctness, bounds, cleanup, and report completeness are gates. Achieved
FPS and throughput are evidence, not portable wall-clock pass/fail thresholds. Every report records
the cap and whether the run was CPU, frame-cap, or backlog limited. Full evidence is opt-in and is not
part of `qualityGate`, `performanceEvidence`, or ordinary CI.

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
