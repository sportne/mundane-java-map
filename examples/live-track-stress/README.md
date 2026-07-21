# Live-track stress example

This JVM-only example exercises the real map, shapefile, projection, rendering, interaction, and
resource-lifecycle stack while simulating and estimating 10,000, 100,000, or 1,000,000 independent
tracks. It is architecture and performance evidence, not a reusable tracking API or an operational
tracking system.

## Run the viewer

```bash
./gradlew :examples:live-track-stress:run
./gradlew :examples:live-track-stress:run --args='--population=100000'
./gradlew :examples:live-track-stress:run --args='--population=1000000'
./gradlew :examples:live-track-stress:run \
  --args='--population=100000 --seed=0x1234 --workers=4 --report-profile=reference --fps=30'
```

Population, seed, worker count, the fixed `reference` report profile, and the initial FPS cap are
selected before packed state allocation. Population must be 10k, 100k, or 1m; workers are bounded to
1 through 32 and the population; and FPS is one of 1/2/5/10/15/30/60 or 0 for uncapped. The toolbar
shows the active choices, changes the FPS cap, pauses or resumes simulation time, resets the same
seeded run, and fits the world. Normal map drag/wheel navigation remains active because the
transparent track overlay does not consume pointer events.
Telemetry reports simulation time, achieved FPS, frame requests/completions/paints/skips/stale
results, build-latency quantiles, report conservation and lateness, backlog, shard skew, and logical,
frame, and observed heap use. A terminal engine failure appears with its stable category.

## Workload and estimator

Each fixed track owns deterministic truth state and emits one noisy position report every 1 through
60 seconds. Per axis, the forward Integrated Ornstein-Uhlenbeck model is `dp = v dt` and
`dv = -beta v dt + sigma dW`; the estimator uses the exact discrete state transition and process
covariance for the elapsed interval, then a position-only Kalman update. The reference parameters
are beta `0.05`, sigma `20`, measurement standard deviation `5,000`, and seed
`0x4d554e44414e454c`. Packed primitive arrays, a 64-slot due-time wheel, stable contiguous shards,
and explicit workers avoid one object or scheduled future per track.

The implementation was independently derived from the public equations discussed in Paul W.
Vebber's 1991 Naval Postgraduate School thesis, *An Examination of Target Tracking in the
Antisubmarine Warfare System Evaluation Tool (ASSET)*. Detailed provenance and approved support
wording are in [the G15 design](../../design/G15-live-track-stress-and-iou-tracking.md). This project
does not claim proprietary implementation equivalence, endorsement, navigation-grade accuracy, or
operational safety.

Truth and estimator state use a deliberately simple Cartesian Web Mercator map plane. Longitude
wrap and north/south reflection keep simulated truth on the display, but those discontinuities are
not part of the estimator dynamics. Large-population error and normalized-innovation summaries
therefore include seam/reflection outliers and are diagnostic only.

## Chart and resource limits

The background is the bundled, unmodified Natural Earth `ne_110m_land` 4.1.0 shapefile. Its hashes,
retrieval record, and public-domain terms are in
[NATURAL_EARTH_PROVENANCE.md](NATURAL_EARTH_PROVENANCE.md). It is opened through the ordinary
bounded shapefile and explicit CRS path; the example never downloads data at runtime.

Population is capped at one million and workers at 32. Construction checks arithmetic, a 192-byte
per-track hard logical ceiling, largest primitive allocation, three-frame ownership, 256 MiB
headroom, and a 60-percent heap envelope before allocating population state or a frame. At most one
frame request is outstanding; overload skips frame demand rather than reports. Close cancels and
joins the frame producer and every shard worker, closes the map source, and releases all buffers.

## Verification and evidence

The fast deterministic lane stays in the normal iteration loop:

```bash
./gradlew liveTrackSmoke --console=plain
```

Full evidence is opt-in and uses `/tmp/mundane-java-map-live-track/<run-id>/` for transient work. A
profile performs a fixed 10-second warmup and 60-second measurement at a 10 FPS cap, then atomically
writes versioned JSON and concise Markdown under `build/reports/live-track/`:

```bash
./gradlew liveTrackEvidence -PliveTrackProfile=10k --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=100k --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=1m --console=plain
```

Reports include environment/configuration, phase counters, storage, update throughput, latency,
frame/backlog/shard metrics, position and innovation summaries, diagnostics, and cleanup state.
Failure and cancellation still produce terminal reports when the output path remains writable.
[G15-007-EVIDENCE.md](G15-007-EVIDENCE.md) interprets the retained named-machine reports. FPS and
throughput are observations, not portable pass/fail thresholds. This example makes no Native Image
claim and adds no production module or external dependency.
