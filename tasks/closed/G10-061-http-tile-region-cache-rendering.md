# G10-061 — HTTP tile regions, cache, and rendering

Status: Complete
Depends on: G10-060
Gate: G10
Type: AFK

## Goal

Acquire bounded XYZ regions deterministically, tolerate missing tiles, reuse decoded tiles
transactionally, and render the detached result through a worker-driven viewer.

## Context

G10-060 establishes one-tile acquisition and detached raster ownership. G10-006 defines exact region
math, row-major deterministic batches, conservative reservations, missing-tile warnings, cache commit,
and success/close/cancellation linearization.

## Scope

Add exact Web Mercator region-to-tile selection, bounded deterministic concurrent batches, row-major
response/diagnostic processing, transparent 404/410 recovery, transactional decoded RGBA LRU, mosaic
composition, strict window reads, and detached-source lifecycle. Add a worker-driven local/loopback
viewer and tolerant render-regression coverage, including out-of-order server completion.

## Out of scope

Retries, redirects, stale validation, disk caching, prefetch/background refresh, live-network raster
sources, public services, arbitrary projections, exhaustive failure hardening, and Native Image.

## Acceptance criteria

- Region tile selection and mosaic placement are exact at Web Mercator/tile boundaries, with checked
  axis/count/output arithmetic and stable row-major ordering.
- Completion timing cannot change pixels, primary diagnostics, warnings, or LRU order; 404/410 tiles
  become transparent with bounded warnings and other failures publish no partial source.
- Cache hits/promotions/admissions commit only after complete fetch success and failed/cancelled fetches
  leave membership and order unchanged.
- A worker-driven viewer keeps network/decode work off Swing painting and renders detached mosaics;
  tolerant regression covers full and missing-tile regions.

## Required tests

XYZ/world boundary and region math; maximum concurrency with barrier-controlled out-of-order loopback
responses; row-major diagnostics; missing tiles; cache hit/LRU/eviction/rollback; cancellation/close
arbitration; detached windows; viewer threading; and rendering-regression tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-http-tiles:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

One integration owner must preserve G10-039 and any GeoPackage/MBTiles users while changing shared
decoder, publication, consumer, architecture, and inventory files.
