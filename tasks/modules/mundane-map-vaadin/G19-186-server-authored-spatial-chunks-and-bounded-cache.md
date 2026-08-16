# G19-186 — Server-authored spatial chunks and bounded cache

Status: Proposed
Depends on: G19-185
Gate: G19
Type: AFK

## Goal

Provide smooth production navigation through bounded server-authored spatial prefetch/reuse without browser source authority.

## Context

Retaining only the accepted viewport wastes stable nearby work and can expose query latency during pan/zoom. A naive tile cache
would stale portrayal/edit state, duplicate overlap or accidentally implement remote services in the browser.

## Scope

- Define semantic chunk planning and keys covering source/binding revision, portrayal/style/catalog/font revision, map/display
  CRS/profile, wrap/copy, scale band, space index and private schema/backend profile.
- Query, project, split/wrap, portray, label, clip and assign identities on server lanes; publish only same-origin prepared chunks.
- Add bounded prefetch policy, request scheduling/cancellation, session-memory CPU/GPU/resource caches and deterministic eviction;
  provide an equivalent no-cache mode.
- Deduplicate overlap and preserve logical/visual identity, order, labels, selection, hover, hit/capture/edit and world-copy behavior.
- Invalidate atomically on source/style/CRS/wrap/font/edit revisions, failure and lifecycle transitions; release every resource once.

## Out of scope

- Persistent browser storage, runtime offline data, client-derived remote requests, client portrayal or unbounded speculative fetch.

## Acceptance criteria

- Cached and uncached modes produce identical semantics and approved visuals while cached navigation reduces named query/transfer
  costs and reaches a stable memory plateau.
- Edits and every semantic revision cannot display/hit/select stale chunks or strand pending/resource ownership.
- Prefetch/cache work remains within exact counts, bytes, concurrency, distance, generations and eviction ceilings.

## Required tests

- Key/invalidation matrices across sources/styles/fonts/CRS/wrap/scales/edits, seams/copies/overlap and cached/no-cache parity.
- Slow/failing/canceled queries, rapid navigation/reversal, memory pressure/eviction, registrar failures, detach/session close and
  long whole-world navigation soak with query/transfer/paint/memory evidence.

## Validation

Run Vaadin source/query/frontend/browser and performance evidence lanes, then qualityGate and `git diff --check`.

## Notes

None.
