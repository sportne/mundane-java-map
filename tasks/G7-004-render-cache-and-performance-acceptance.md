# G7-004 — Render cache and performance acceptance

Status: Proposed
Depends on: G7-003, G6-004
Gate: G7
Type: AFK

## Goal

Add bounded projected-path, symbol, and raster render caches where evidence justifies them, then
record the performance and memory envelope accepted for Level 1.

## Context

G6-004 supplies format-level raster caches. G7-003 supplies the optimized uncached vector path.
G7-001 is the measurement method and must be rerun unchanged for before/after comparison.

## Scope

- Explicit bounded caches in `mundane-map-core` and `mundane-map-awt`
- Integration with symbol and raster cache ownership without duplicate unbounded storage
- Performance-evidence scenarios, cache metrics, and Level 1 performance-envelope documentation

## Out of scope

- Process-global implicit caches, disk/remote caches, pre-rendered tile pyramids, and native
  acceleration
- Changing source/query semantics to improve a benchmark

## Acceptance criteria

- Cache boundaries are selected from G7-001 evidence; each cache has a stable key, owner, entry/byte
  budget, deterministic eviction, and an explicit bypass rule for oversized values.
- Projected-path keys include geometry/source version, projection/CRS, viewport scale or tolerance,
  clipping state, and every style input affecting the path.
- Symbol keys include immutable symbol/catalog identity and placement/render parameters; raster keys
  compose with G6-004 without retaining duplicate decoded images.
- Viewport, layer/source version, style/catalog, projection/CRS, decoder, close, and cancellation
  changes invalidate exactly the affected entries.
- Failed/cancelled/partial values are never cached, concurrent reads are safe, and cached values
  cannot be mutated by callers.
- Metrics expose hits, misses, evictions, bypasses, estimated bytes, and rebuild work to tests and the
  performance report without exposing cache implementation as public API.
- The unchanged evidence suite records before/after median/tail timing, allocation/retained-memory
  estimates, and semantic checks for cold and warm pan/zoom workloads.
- The Level 1 performance document records fixture sizes, reference environment, accepted cold/warm
  behavior, cache memory budgets, known limits, and any target not met; absolute timing is not made a
  portable correctness gate.
- Any unmet target remains documented work; no custom native performance library is added without a
  separate benchmark-backed decision.

## Required tests

- Cache key, hit/miss/eviction/bypass, invalidation, budget, close, cancellation, concurrency, and
  defensive-immutability tests.
- Cached-versus-uncached semantic/render equivalence across pan, zoom, style, CRS, and source changes.
- Full performance evidence before/after comparison using identical fixture seeds and configuration.
- Architecture tests for bounded explicit ownership and prohibited mechanisms.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-performance-tests:check --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer deleting an unjustified cache to keeping complexity without measurable benefit. Cache memory
budgets are part of the Level 1 support envelope and must be visible in evidence.
