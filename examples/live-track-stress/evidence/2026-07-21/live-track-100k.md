# Live-track evidence — 100k

- Schema: `mundane-map-live-track-evidence/v1`
- Run: `20260721T145534Z-100k-546478`
- Status: **SUCCESS**
- Limitation: [FRAME_CAP_LIMITED]
- Environment: Linux 5.15.167.4-microsoft-standard-WSL2, Intel(R) Core(TM) i9-14900KF, Java 21.0.11

## Configuration and outcome

| Metric | Value |
| --- | ---: |
| Population | 100000 |
| Workers | 8 |
| FPS cap | 10 |
| Warmup seconds | 10 |
| Measurement seconds | 60 |
| Processed reports | 699742 |
| Processed reports/second | 9992.7493 |
| Achieved FPS | 10.0000 |
| Frame build p95 (ms) | 7.5832 |
| Backlog seconds | 0 |
| Report shard skew | 1.0119 |
| Work shard skew | 1.1785 |
| Position RMSE (map units) | 172590.1521 |
| Normalized innovation mean | 1942.6192 |
| Peak observed heap bytes | 95895336 |

Cleanup: workers terminated=true, resources closed=true, workspace removed=true.
