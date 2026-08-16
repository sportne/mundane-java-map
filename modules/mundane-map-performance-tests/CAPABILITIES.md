# Performance verification profile

This non-published support module owns performance methodology and evidence for the project. Its
benchmark dependencies never enter production runtime graphs, and its results are engineering
evidence rather than portable API performance promises.

## Harness division

| Workload class | Required harness | Purpose |
| --- | --- | --- |
| Isolated algorithms and bounded operations | Pinned OpenJDK JMH | Statistically disciplined warmup, forks, measurement, dead-code protection, throughput/latency, GC, and allocation evidence |
| End-to-end query/render/edit/format/workspace workflows | Existing deterministic evidence runner | Fixed semantic/work counters, representative scenario timing, memory/resource observations, and reproducible fixture evidence |
| JVM event investigation | Java Flight Recorder | Diagnostic allocation, CPU, I/O, lock, and GC investigation for selected scenarios; not the primary score source |
| Browser rendering and interaction | Owning Vaadin browser evidence lane | Real-browser query/transfer/paint/memory evidence; never simulated by JMH |
| Native Image execution | Owning Native Image matrix | Native startup, memory, and representative work on supported hosts; never inferred from JVM JMH |

JMH is required for microbenchmarks. The custom evidence runner must not grow a second competing
implementation of JMH's fork, warmup, sampling, or statistical functions.

## Benchmark correctness

- Every benchmark consumes observable results through JMH state/return values or blackholes and has
  an untimed semantic oracle proving the measured work is correct and cannot be optimized away.
- Setup levels, mutable state, parameters, threading, forks, warmup, measurement, JVM arguments,
  garbage collector, heap, and profilers are explicit and machine-readable.
- Input fixtures are deterministic, checksummed, licensed, realistically shaped, and separated from
  timed setup unless setup itself is the operation under measurement.
- Benchmarks close sources, cursors, sessions, files, databases, executors, images, and recordings;
  repeated trials must return owned-resource counters to baseline.
- Both ordinary representative sizes and documented hostile/maximum profiles are covered, but a hard
  ceiling may use an integration workload when JMH would be the wrong execution model.

## Measurement categories

- API/core geometry, topology, CRS/coordinate operations, raster warping, tile matrices, snapping,
  hit testing, portrayal, labeling, and spatial indexes.
- Format parse/decode/encode, window access, streaming, compression, resource resolution, and
  malformed/limit rejection for every implemented G19 adapter profile.
- SQLite-backed GeoPackage/MBTiles query, transaction, index, rewrite, and recovery on pinned storage
  profiles without pretending one filesystem represents all deployments.
- AWT scene construction/rendering, browser scene encoding/transport/paint, workspace open/save/package,
  and representative native workflows in their owning integration lanes.
- Throughput or sample/average/p50/p95/p99 latency as appropriate, plus allocation rate/bytes,
  garbage collections, retained/peak memory where meaningful, and named logical work counters.

## Interpretation boundary

- Scores are comparable only within a declared compatible environment/toolchain profile. Cross-machine
  results are labeled informational unless normalized by a separately validated method.
- Benchmarks do not compare unrelated third-party products for marketing and do not convert noisy
  wall-clock observations into correctness assertions.
- Optimization must preserve semantic, diagnostic, limit, ordering, ownership, and deterministic-output
  contracts. Faster incorrect or skipped work is a benchmark failure.
- Pull requests run bounded harness-correctness, coverage, semantic-oracle, work-consumption, limit,
  result-schema, and resource-closure checks.
- Full JMH and integration suites may run manually or on a schedule to inform engineering decisions,
  but timing, allocation, GC, and memory scores are never release-blocking thresholds.
- All scores are environment-specific informational evidence. The project does not maintain dedicated
  comparison hardware, performance baselines, statistical regression gates, rebasing, or waivers.

## Evidence and provenance

- Result bundles use a versioned machine-readable schema and record source revision, dirty state,
  operating system, architecture, CPU topology, memory, power mode, virtualization, JDK/JMH, JVM flags,
  GC, heap, forks, iterations, profilers, fixture hashes, scenario parameters, units, raw samples, and
  summary statistics.
- The exact JMH artifact/version/license/checksum graph is pinned and staged by the offline workflow.
- Result-schema, scenario, parameter, and methodology changes are reviewable and versioned; raw results
  are retained rather than rewritten into normative thresholds.

## Explicit non-goals

- Production runtime dependencies on JMH or benchmark-only libraries.
- A single universal “operations per second” promise across hardware, JVMs, browsers, or native hosts.
- Release-blocking timing/allocation/memory comparisons, dedicated benchmark hardware, or performance
  service-level guarantees.
- Microbenchmarks for inherently end-to-end browser, network, filesystem, or lifecycle behavior.
- Unbounded benchmark matrices that make ordinary correctness gates impractical.

## Completion rule

The module is complete when its decomposed G19 cards provide a pinned JMH methodology, benchmark
correctness guardrails, representative coverage of every material hot path, retained deterministic
integration/browser/native evidence, and reproducible explicitly informational results with no
production dependency leakage.
