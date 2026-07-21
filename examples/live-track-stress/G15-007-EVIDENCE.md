# G15-007 live-track evidence

This is one named-machine evidence set, not a portable performance threshold or an operational
accuracy claim. Each profile used the committed `liveTrackEvidence` contract: a 10-second warmup,
60-second measurement, eight stable shards, reference seed and filter, `900 x 500` viewport, 10 FPS
cap, 1 GiB maximum heap, `/tmp` work area, and atomic JSON/Markdown reports.

## Environment

- Date: 2026-07-21
- OS: WSL2 Linux `5.15.167.4-microsoft-standard-WSL2`, AMD64
- JVM: Ubuntu OpenJDK `21.0.11`
- CPU exposed to WSL: Intel Core i9-14900KF, 32 logical processors
- Schema: `mundane-map-live-track-evidence/v1`

## Results

Full retained report pairs: [10k JSON](evidence/2026-07-21/live-track-10k.json) /
[Markdown](evidence/2026-07-21/live-track-10k.md),
[100k JSON](evidence/2026-07-21/live-track-100k.json) /
[Markdown](evidence/2026-07-21/live-track-100k.md), and
[1m JSON](evidence/2026-07-21/live-track-1m.json) /
[Markdown](evidence/2026-07-21/live-track-1m.md).

| Profile | Run ID | Reports/s | FPS | Build p95 | Backlog | Peak heap | Result |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 10k | `20260721T145417Z-10k-546274` | 1,006.60 | 10.0 | 1.660 ms | 0 s | 30,459,408 B | Frame-cap limited |
| 100k | `20260721T145534Z-100k-546478` | 9,992.75 | 10.0 | 7.583 ms | 0 s | 95,895,336 B | Frame-cap limited |
| 1m | `20260721T145651Z-1m-546716` | 100,005.23 | 10.0 | 25.003 ms | 0 s | 446,673,680 B | Frame-cap limited |

All three runs completed 600 requested, built, and consumed measurement frames with no skipped,
stale, replaced, rejected, or late work. Each retained exactly one pending next report per track,
terminated all workers, closed all frame resources, and removed its temporary workspace. The 1m run
used 113,025,600 logical track bytes, 16,000,000 packed display-position bytes, and a largest single
allocation of 8,000,000 bytes.

The position RMSE and normalized-innovation values are diagnostic rather than acceptance thresholds.
The 100k and 1m populations sample more tracks crossing the simulated world's wrap/reflection
boundaries, which the deliberately simple Cartesian estimator does not model as continuous. Their
large innovation outliers are therefore retained rather than hidden; no operational accuracy or
safety conclusion follows from this stress workload.

## Reproduction

```bash
./gradlew liveTrackEvidence -PliveTrackProfile=10k --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=100k --console=plain
./gradlew liveTrackEvidence -PliveTrackProfile=1m --console=plain
```

Each invocation replaces `build/reports/live-track/live-track-<profile>.json` and `.md`. The complete
reviewed reports are also retained under `evidence/2026-07-21/`; generated build output remains
separate so later local runs do not silently alter the named-machine record.
