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

- One bounded source-owned canonical `RgbaPixelBuffer` result cache and immutable public cache policy
- Exact source snapshots/SHA-256 versioning, deterministic LRU/bypass/invalidation, package-private
  metrics, and serialized concrete-source read/close lifecycle
- Complete AWT-free PNG/JPEG physical/safety validators for the accepted Level 1 profile, added format
  limits/diagnostics, AWT decoder integration, hostile fixtures, and architecture tests

## Out of scope

- Global unbounded caches, disk caches, remote cache policy, and G7 cross-layer render caches
- Encoded-byte, BufferedImage/AWT/ARGB, shared decoder, or cross-source caches; public metrics/version
- Performance claims before G7 evidence or custom native acceleration

## Acceptance criteria

- The private key is exact content length/SHA-256, source window, output size, and interpolation.
  Source ID, tighter limits/token, opacity, placement/CRS, and ImageIO hint choice are excluded.
- `ImageOpenOptions` remains source-compatible while adding an immutable cache policy, and the prior
  six-/eight-value `ImageSourceLimits` construction remains compatible when container limits append.
- Entry-count and estimated-byte budgets are explicit, configurable, and enforced with deterministic
  successful-access LRU; oversized/disabled/accounting-constrained entries bypass without failure.
- Cached hits/misses return fresh consumer-owned buffers. Miss admission retains the decoder buffer
  only after an independent copy fits; cache use cannot bypass uncached tighter-limit preflight.
- Open/container validation establishes a full SHA-256 baseline. Each read verifies it; a miss decodes
  one exact operation snapshot. Length/header/body mismatch clears cache and never adopts new bytes;
  open-, read-fingerprint-, and operation-snapshot changes have stable distinct reasons.
- Every open/read fingerprint and operation snapshot checks captured file size before and after an
  exact byte count. Success proves baseline bytes were observed with stable length and, under the
  documented non-adversarial/no-ABA local-file assumption, linearizes within that observation interval.
- Close and invalidation remove entries and release the channel; duplicate source IDs cannot share.
- Cancelled, failed, partial, or diagnostically invalid decodes are never cached.
- Concurrent identical requests are safe and bounded; callers cannot mutate cached arrays or values.
- One monitor serializes concrete-source read/read and read/close with exact winner semantics; close
  is not cancellation and failed/cancelled/partial work never promotes, evicts, or admits.
- Cache hit/miss/eviction behavior is observable to tests without making implementation-specific
  cache classes public API; success-only counters and hit promotion commit after final cancellation.
- PNG physical validation covers every chunk/CRC/order/IEND, the explicit opaque-ancillary profile,
  PLTE/tRNS, APNG rejection, and exact bounded IDAT inflation including Adam7. JPEG's finite marker
  state machine validates all scans/stuffing/restarts through sole EOI and rejects every unlisted,
  concatenated, or trailing form. Corrupt/truncated/decompression-heavy/overflow input stays within
  encoded/container/inflated limits and stable diagnostics.
- Cached and uncached results are semantically identical for nearest/bilinear, opacity, and affine
  placement.
- All new public policy/options/limit surfaces have complete Javadocs and source-compatible value,
  constructor, optional-accessor, and wither semantics.

## Required tests

- Deterministic key, hit/miss/successful-LRU/eviction/bypass/metrics, content-version, restoration,
  fresh-copy ownership, close, and invalidation tests.
- Serialized concurrent request/close order, waiting/hit/miss/admission cancellation, failed decode,
  no unsuccessful promotion/admission, and defensive-copy tests.
- Exhaustive bounded PNG/JPEG structural/CRC/inflate/entropy/EOI/trailing hostile fixtures, all limit
  boundaries, mutation during open/read, huge dimensions, and arithmetic overflow.
- Cached-versus-uncached source and offscreen-render equivalence tests.
- Public API Javadoc/doclint and equality/hash/toString, optional policy accessor, old-constructor,
  wither-preservation, null, and positive/equality/plus-one compatibility tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check :examples:raster-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use only the one source-owned cache; no process/global/AWT/encoded cache, finalizer, soft reference,
reflection, or new discovery. Cache behavior is correctness/resource policy, not a performance claim.
Do not run rendering-regression, performance, native, publication, or corpus lanes in this task.

Keep the fixed task reviewable through three internal milestones: (A) physical/safety validators and
hostile fixtures, (B) exact version/snapshot plus serialized lifecycle and races with caching disabled,
then (C) cache/accounting/render integration. Run each milestone's narrow image checks, but do not mark
this task Complete or unblock G6-005 until all milestones and the final validation pass together.
