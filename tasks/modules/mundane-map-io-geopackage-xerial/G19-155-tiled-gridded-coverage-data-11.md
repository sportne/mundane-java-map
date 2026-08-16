# G19-155 — Tiled Gridded Coverage Data 1.1

Status: Proposed
Depends on: G19-012, G19-074, G19-151, G19-154
Gate: G19
Type: HITL

## Goal

Implement complete official Tiled Gridded Coverage Data 1.1 read/write semantics for raster and elevation grids.

## Context

GeoPackage tile BLOBs currently become imagery only. Coverage ancillary tables, sample datatypes,
scale/offset/null values, units, precision and elevation integration are absent.

## Scope

- Implement package/table/tile ancillary schemas, extension rows, datatypes, scale/offset, precision, null,
  grid-cell encoding, bounds, units, statistics and producer metadata.
- Decode/write supported integer/floating coverage encodings into neutral raster/elevation while preserving raw semantics.
- Validate matrix/ancillary/tile cardinality, endian/sample layout, codecs, no-data/NaN/extrema, seams and pyramids.
- Add bounded windows/resampling/statistics plus insert/update/delete/bulk writer preparation.
- Share sample/tile/pixel/cache/request/owned-byte/work budgets across an entire query or transaction.

## Out of scope

- Inferring coverage from generic image/private terrain tiles and 3D terrain-mesh generation.

## Acceptance criteria

- Official/independent coverage files preserve sample, null, scale/offset, units and placement semantics.
- Raster/elevation windows and written tiles agree within declared numeric tolerances at seams and pyramid levels.
- Missing/conflicting ancillary state and limit failures reject atomically before source/transaction publication.

## Required tests

- Ancillary/datatype/endian/scale/offset/null/unit/statistics/grid-cell/pyramid/read-write matrices.
- Missing/duplicate/conflicting rows, corrupt tiles, NaN/extrema/precision, seams, cancellation and exact limits.

## Validation

Run module/coverage/elevation/interoperability checks, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve the 1.1 sample/codec profile, numeric tolerances and independent coverage corpus.
