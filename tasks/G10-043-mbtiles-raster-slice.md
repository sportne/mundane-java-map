# G10-043 — MBTiles raster slice

Status: Proposed
Depends on: G10-039, G11-004
Gate: G10
Type: AFK

## Goal

Publish a working optional Xerial-backed MBTiles adapter that reads metadata and renders one bounded
sparse PNG/JPEG raster tileset.

## Context

G10-039 supplies bounded byte-array image decoding. G11-004 and its G10-004 dependency must approve
the exact Xerial adapter boundary before this card executes; the proposed G10 design fixes MBTiles
1.3 metadata, TMS conversion, and raster semantics.

## Scope

After approval, create `modules/mundane-map-io-mbtiles-xerial` using the exact pinned classifiers and
private connection policy; implement bounded metadata inspection, strict real-table schema, PNG/JPEG
format normalization, explicit zoom selection, checked TMS-to-XYZ conversion, smallest populated
EPSG:3857 extent, sparse transparent windows, coalesced warnings, G10-039 decoding, transactional
disabled-by-default LRU, viewer rendering, lifecycle/cancellation, architecture inventory,
publication, and staged offline consumer behavior.

## Out of scope

Executing before G11-004 completes; vector tiles, UTFGrid, multiple tilesets, remote fetching,
automatic zoom, arbitrary CRS, writes, views/extensions, attribution rendering, non-Linux support,
or Native Image.

## Acceptance criteria

- A strict MBTiles fixture exposes detached immutable metadata, validates an explicit zoom, converts
  TMS rows exactly, serves sparse 256-pixel PNG/JPEG windows, and renders on canonical EPSG:3857.
- Fixed SQL/session/fingerprint/cancellation/cleanup and cache commit semantics match the approved
  optional-adapter policy without leaking JDBC/Xerial or triggering discovery.
- The separately named module, exact classified dependency, architecture allowlist, publication
  metadata, build-only mirror, and clean offline Java 21 consumer are verified.

## Required tests

Metadata/schema/profile tests, zoom/TMS/extent math, PNG/JPEG/sparse reads, cache behavior,
cancellation/fingerprint/lifecycle, decoder diagnostic translation, render/viewer, architecture and
external-dependency inventory, publication, and offline-consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-mbtiles-xerial:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

This planning card does not approve G10-004 or G11-004. Reconcile it with their final approved
artifact/platform/profile record before implementation.
