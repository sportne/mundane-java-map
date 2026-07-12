# G6-004 — Raster cache, lifecycle, and hardening

Status: Proposed
Depends on: G6-003
Gate: G6
Type: AFK

## Goal

Bound repeated PNG/JPEG decode and resample work while guaranteeing deterministic invalidation,
cancellation, resource cleanup, and hostile-input behavior.

## Context

G6-003 provides uncached correctness for windows and rendering. That path remains the oracle for
cache equivalence and failure handling.

## Scope

- Bounded encoded/decode/resample caches in the raster source and AWT adapter
- Cache keys, budgets, invalidation, metrics needed for tests, and lifecycle integration
- Corrupt, truncated, oversized, cancellation, and concurrency hardening fixtures

## Out of scope

- Global unbounded caches, disk caches, remote cache policy, and G7 cross-layer render caches
- Performance claims before G7 evidence or custom native acceleration

## Acceptance criteria

- Cache keys include stable source identity/version, source window, output size, interpolation, and
  every decode option affecting pixels; opacity is cached only at the correct composition layer.
- Entry-count and estimated-byte budgets are explicit, configurable, and enforced with deterministic
  eviction; oversized entries bypass or fail according to a documented rule.
- Close and source invalidation remove owned entries and release streams/images; one source cannot
  evict or reuse another source's identity incorrectly.
- Cancelled, failed, partial, or diagnostically invalid decodes are never cached.
- Concurrent identical requests are safe and bounded; callers cannot mutate cached arrays or values.
- Cache hit/miss/eviction behavior is observable to tests without making implementation-specific
  cache classes public API.
- Corrupt, truncated, decompression-heavy, dimension-overflow, and oversized inputs remain within
  configured bytes/pixels/work limits and yield stable diagnostics.
- Cached and uncached results are semantically identical for nearest/bilinear, opacity, and affine
  placement.

## Required tests

- Deterministic hit/miss/eviction, source-version, oversized-entry, close, and invalidation tests.
- Concurrent request, cancellation race, failed-decode, and defensive-copy tests.
- Hostile fixtures for corrupt/truncated streams, huge dimensions, decode-byte limits, and arithmetic
  overflow.
- Cached-versus-uncached source and offscreen-render equivalence tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :examples:raster-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer small, explicitly owned caches over process-global state. Do not use finalizers, reflection,
soft-reference behavior, or platform-default ImageIO discovery as lifecycle policy.
