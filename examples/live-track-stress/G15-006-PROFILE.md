# G15-006 100k profile

This is environment-labelled development evidence, not a portable performance claim or gate.

## Environment

- Baseline commit: `8225de2` plus the profiling probe only
- Date: 2026-07-21
- OS: WSL2, Linux `5.15.167.4-microsoft-standard-WSL2`, x86-64
- JVM: Ubuntu OpenJDK `21.0.11+10`
- CPU exposed to WSL: Intel Core i9-14900KF, 16 cores / 32 logical CPUs
- Work files: `/tmp/mundane-map-g15-006*.jfr`
- Workload: 100,000 tracks, reference seed/profile, 1,200 virtual seconds, 121
  `900 x 500` frames, and 12,016,919 processed reports

## Reproduction

```bash
./gradlew :examples:live-track-stress:liveTrackScaleProbe \
  -PliveTrackWorkers=8 -PliveTrackSeconds=1200 -PliveTrackJfr=true --console=plain
jfr view hot-methods /tmp/mundane-map-g15-006.jfr
jfr view allocation-by-site /tmp/mundane-map-g15-006.jfr
jfr view gc-pauses /tmp/mundane-map-g15-006.jfr
```

Set `liveTrackWorkers` to `1`, `2`, `4`, or `8` for the recorded worker comparison. The probe accepts
10-second multiples from 10 through 3,600 seconds and remains separate from `qualityGate`.

## Before and after

| Measurement | Before | After |
| --- | ---: | ---: |
| Initialization | 196.091 ms | 124.620 ms |
| Total probe | 1,776.061 ms | 1,036.827 ms |
| Processed reports | 12,016,919 | 12,016,919 |
| Final colored pixels | 205,884 | 205,884 |
| GC pauses | 11 | 9 |
| Total GC pause | 19.5 ms | 19.0 ms |

The approximately 41% shorter recorded total justified retaining two deterministic optimizations:

- precompute the 60 integer-second IOU coefficient sets once per shard and reuse them for reports;
- compute each Box-Muller pair's shared radius once, and compute at most 61 display transitions per
  shard/frame instead of recomputing one per axis and track.

All operations and outputs remain in the approved `StrictMath` profile. The after recording no
longer identifies IOU coefficient or display-transition recomputation as a leading site. Its main
remaining application work is packed filter update, timing-wheel processing, and rasterization.

Java 21's `StrictMath.sin`/`cos` implementation uses temporary `double[]` storage inside `FdLibm`;
those JDK sites account for more than 98% of sampled allocation pressure. There is no example-owned
per-report allocation site in the after recording. Replacing strict trigonometry would alter the
approved deterministic draw mapping, while the measured GC cost is under 2% of this short run, so
G15-006 deliberately retains the strict implementation.

## Worker qualification

Unrecorded-JFR comparison runs on the same machine produced:

| Workers | Total probe |
| ---: | ---: |
| 1 | 3,232.272 ms |
| 2 | 1,789.209 ms |
| 4 | 1,211.806 ms |
| 8 | 952.738 ms |

Stable contiguous shards reproduce the same checksum, report count, and frame composition at one
and eight workers. The bounded supported range remains `[1, min(32, population)]`; the default
remains `min(8, availableProcessors)` because eight was the fastest measured supported default on
this machine. No timing-based auto-tuning or work stealing is introduced.
