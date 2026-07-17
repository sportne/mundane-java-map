# G7-004 — Render cache and performance acceptance

Status: Complete
Depends on: G7-003, G6-004
Gate: G7
Type: AFK

## Goal

Evaluate two bounded private AWT render-cache candidates with predeclared same-run rules, retain only
the candidates that earn their complexity, and record the resulting Level 1 performance envelope.
Retaining no view cache is a valid successful outcome.

## Context

G6-004 supplies the only decoded/resampled raster-pixel cache. G7-003 supplies an optimized,
operation-local, uncached screen plan and append-only evidence rows. G7-001 defines the measurement
method; its existing rows, fixtures, semantic oracles, order, and cache-state labels remain unchanged.

## Scope

- `modules/mundane-map-awt`: at most one private, EDT-confined `MapView` cache owner with
  independently removable screen-plan and untransformed-vector-template candidate partitions
- `modules/mundane-map-performance-tests` and `modules/mundane-map-architecture-tests`: append-only
  candidate evidence, decision arithmetic, and enforceable cache boundaries
- `DESIGN.md`, `design/G7-performance-and-indexing.md`, this task/index, and `ROADMAP.md`: the source
  evidence reference, retained/rejected decision, final limits, and G7 closeout
- Exact keys, logical byte/count budgets, admission, LRU, bypass, purge, and close behavior for each
  candidate
- Append-only same-binary cold/warm evidence scenarios, package-private operation metrics, objective
  retain/delete rules, and the checked-in Level 1 performance decision record

## Out of scope

- A public or core cache API, cache configuration, automatic policy selection, hit/query caches,
  process-global state, disk/remote caches, pre-rendered tile pyramids, and native acceleration
- A second decoded-image, raster-pixel, or Java2D-image cache above G6-004
- Caching custom-renderer output or changing source, query, interaction, endpoint, or cancellation
  semantics to improve evidence

## Acceptance criteria

- The screen-plan candidate is eligible only for exact core `InMemoryLayer` and
  `InMemoryFeatureSource` bindings, whose immutable geometry object identities remain stable. It is
  keyed by binding attachment identity, geometry identity, resolved geometry-to-display operation
  identity, exact viewport, expanded clip, tolerance, and line-versus-polygon role. Snapshot keys use
  the view's map-to-display operation; source keys use the binding's source-to-display operation. Its
  provisional limits are 8,192 entries, 32 MiB logical retained bytes, and 4 MiB per entry.
- The vector-template candidate is keyed only by immutable `VectorPath` identity and retains the
  approved untransformed private fill/stroke `Path2D.Double` pair for built-in renderers. It preflights
  exact source and converted stream counts before allocation. Its provisional limits are 512 entries,
  4 MiB logical retained bytes, and 256 KiB per entry.
- A successfully rendered hit promotes its deterministic LRU entry. Lookup and miss construction
  mutate no state until a complete value has rendered successfully; disabled, failed, cancelled,
  partial, or oversized work is not admitted and an oversized value causes no eviction. Binding
  removal purges its screen plans; `MapView.close()` clears every retained partition.
- Viewport, style margin, geometry/source attachment, CRS operation, or vector-path changes either
  select a different exact key or perform the stated owner purge. Interaction and semantic endpoint
  work always uses authoritative uncached geometry.
- G6-004 remains the sole map-raster decode/resample cache. AWT raster conversion remains per paint,
  and this task adds no raster cache key, partition, metric, or invalidation path.
- Existing evidence rows remain byte-for-byte compatible in ID, order, fixture, batch, cache label,
  and semantic oracle. New cold and explicitly preseeded-warm rows append to them and compare in the
  same unfiltered `BASELINE` run; warm seeding is independent of the configured warmup count.
- Both full and quick evidence Java processes run from an invocation-unique real `/tmp` classpath,
  working directory, temporary directory, fixture workspace, and provisional output tree. Staging
  preserves classpath order, rejects symbolic-link/path escapes, cleans on success or failure, and
  publishes only completed JSON/Markdown files back to separate build destinations with locked atomic
  replacement; the canonical profile and report semantics otherwise remain unchanged.
- Root `performanceQuick` depends only on the module quick task. It runs every SMOKE scenario with
  fixed explicit one/two iteration counts, no BASELINE oracle or override inputs, produces an
  investigative `NOT_EVALUATED` report under `build/performance-quick`, stays outside all other lanes,
  and completes in under five minutes on this reference WSL workspace without becoming a portable
  wall-clock gate.
- Logical weights charge every packed array retained through a key or value exactly once, including
  source geometry, authoritative/render screen geometry, and both vector-template streams. Borrowed
  attachment/operation objects have no cache back-reference and contribute only retained reference
  slots; object headers remain JFR evidence rather than invented heap estimates.
- Package-private, operation-local metrics report exact requests, hits, misses, builds, admissions,
  evictions, bypasses, and current/peak logical storage. There is no public metric, cumulative
  `MapView` last-result state, logger, JMX surface, or invented heap/allocation estimate.
- The screen-plan candidate is retained only if its warm hit and build-reduction rates are each at
  least 80% for dense render, pan, and zoom; at least two warm medians are at most 90% of their
  uncached optimized medians; none exceeds 105%; cold dense/pan/zoom medians do not exceed 105%; the
  cold small median does not exceed 110%; and default-budget fixtures have no bypass or eviction.
- The vector-template candidate is retained only if its warm hit and build-reduction rates are at
  least 99%, its warm median is at most 95% of the uncached symbol median, its cold median does not
  exceed 102%, and the default fixture has no bypass/eviction and retains at most 4 MiB logically.
  Checked integer/rational comparisons decide equality and overflow; ambiguity or failure rejects the
  candidate and removes its production code, scenarios, metrics, and tests.
- The G7 design records the source-report SHA-256, revision, reference environment/configuration,
  exact counters and median/p95 ratios, retained or rejected candidates, final budgets, known limits,
  and unmet advisory goals. Raw nanos and optional JFR attribution remain evidence, not a portable
  duration gate or retained-heap claim.
- G7 closes with no public performance/cache switch, no generic cache/index/topology framework, and
  no native acceleration. The final implementation contains only evidence-retained cache partitions;
  if none qualifies, the private cache owner is deleted.

## Required tests

- Candidate tests pin eligibility, exact keys, logical weights, preflight-before-allocation,
  limit equality/overflow, event counters, LRU order,
  hit/miss/admit/evict/bypass behavior, oversized no-eviction, binding purge, close, EDT confinement,
  source-success admission, cancellation, and private-value immutability. Tests for a rejected
  candidate are removed with its implementation; retained behavior keeps full coverage.
- Cached-versus-uncached semantic/render equivalence covers cold/warm pan, zoom, style margin, CRS
  operation, geometry identity, source replacement, symbols, overlays, endpoints, and custom-renderer
  bypass using the portable rendering assertions. Equal-but-fresh generic-source geometries bypass
  the screen cache, and mixed open/closed vector paths prove the cached fill/stroke split.
- Performance tests pin append-only row order, cold clearing, warm preseed with zero or more warmups,
  operation-local counter snapshots, formulas, frozen oracle reuse, retain/delete arithmetic, filtered
  investigation as `not evaluated`, deterministic decision rendering, quick-lane investigation
  identity, native scratch staging/publishing cleanup, and exact full/quick task isolation.
- Architecture tests prove AWT-only instance ownership, G6's sole raster-pixel cache, no public/cache
  SPI or knobs, and every existing prohibited mechanism.

## Validation

```bash
./gradlew :modules:mundane-map-awt:check :modules:mundane-map-performance-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew renderRegression --console=plain
./gradlew performanceQuick --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The retain/delete decision is AFK because every checkpoint and ratio is declared above. A filtered or
otherwise noncanonical run may investigate behavior but cannot decide retention. Duration thresholds
select code once from one recorded canonical reference run; they never become recurring CI quality
gates or runtime policy.

The canonical `/tmp`-native BASELINE run completed in 2m15s. Its JSON SHA-256 is
`d53ff058919ff6fee178ec3ee86d0bd5ce540fe602adca686be287b763c0d585`; its Markdown SHA-256 is
`f7e53388585ff1fc9fec1bed4e07fbf3907ff145108a7653033ea74583a595`. The screen-plan candidate was
rejected because warm pan produced zero hits, 6,104 builds, and 6,104 evictions; it was removed in
full. The vector-template candidate was retained at the final 512-entry, 4 MiB total, and 256 KiB
per-entry limits. The G7 design contains the complete environment, counters, timings, and known-limit
record. Final recurring evidence contains only the retained vector comparison.
