# G10-042 — GeoPackage tiles and hardening

Status: Proposed
Depends on: G10-041, G10-039
Gate: G10
Type: HITL

## Goal

Complete the approved GeoPackage adapter with bounded PNG/JPEG tile matrices, sparse raster
rendering, independent fixtures, and full container hardening.

## Context

G10-041 completes vector features and G10-039 supplies the shared in-memory image decoder. The
proposed G10-004 profile fixes tile-matrix math, cache semantics, hostile-container policy, and
fixture/support evidence.

## Scope

Implement explicit-zoom GeoPackage tile `RasterSource` opening, validated 256-pixel matrix math,
sparse transparent reads and coalesced warnings, mixed PNG/JPEG BLOB decoding through G10-039,
disabled-by-default bounded transactional decoded LRU, tolerant affine rendering, complete
schema/file/row/BLOB/geometry/tile/limit/cancellation/mutation/corrupt-database hardening, independent
fixtures with provenance, and both feature/tile viewer completion.

## Out of scope

Executing before approved ancestors; automatic zoom, overviews, non-PNG/JPEG tiles, reprojection,
raw tile APIs, writes, extensions, disk caches, Native Image, or new corpus commands.

## Acceptance criteria

- Valid sparse PNG/JPEG tile matrices serve deterministic windows at an explicit zoom, render in the
  declared recognized CRS, and commit only successful cache admissions/promotions.
- Every approved container, schema, feature, tile, limit, cancellation, fingerprint/mutation,
  corrupt/truncated, cleanup, and diagnostic boundary has stable exact evidence.
- Generated and independent fixtures have pinned provenance, licenses, recipes, and hashes and cover
  both feature and tile modes without widening the profile.

## Required tests

Matrix/placement math, sparse/mixed-image reads, cache hit/eviction/rollback, G10-039 diagnostic
translation, tolerant rendering, full hostile/limit/cancellation/lifecycle matrix, corrupt database,
fixture provenance/digest, architecture, and viewer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geopackage-xerial:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoPackage interoperability review**. This checkpoint occurs only after
G10-004 and G11-004 approve execution; a maintainer then approves independent-fixture provenance and
manually reviews both viewer modes. It does not create a broader SQLite or platform claim.
