# G19-212 — Algorithm, format, and storage benchmark suite

Status: Proposed
Depends on: G19-211
Gate: G19
Type: HITL

## Goal

Provide representative JMH throughput, latency, allocation, and GC evidence for the material in-process hot paths introduced across the completed module profiles.

## Context

The current evidence scenarios emphasize selected rendering, indexing, Shapefile, raster, DTED, and GeoTIFF paths. G19 expands geometry, CRS, portrayal, formats, and SQLite-backed storage substantially.

## Scope

- Benchmark geometry/topology, coordinate operations, raster warping, tile matrices, snapping/hits, portrayal, labels, and indexes at representative and bounded maximum sizes.
- Benchmark parse/decode/encode, streaming, random/window access, compression, resource resolution, and malformed/limit rejection for each implemented format profile.
- Benchmark GeoPackage/MBTiles open/query/index/transaction/build/rewrite/recovery using pinned storage and cache profiles.
- Measure appropriate throughput or latency distributions plus allocation rate/bytes and GC; retain logical work counters for complexity-sensitive cases.
- Use deterministic licensed fixtures with independently verified content, hashes, generators, and realistic parameter distributions.

## Out of scope

- Third-party product comparisons, public-internet services, or multiplying every scenario across every parameter combination without evidence value.

## Acceptance criteria

- A coverage manifest maps every named material hot path to a benchmark or justified integration-only evidence lane.
- Ordinary and hard-bound profiles remain bounded and produce correct identical semantic observations.
- Storage results identify filesystem/cache/sync/transaction settings and never generalize beyond that profile.

## Required tests

- Full JMH scenario/parameter matrix in a completely recorded environment, coverage-manifest checks, fixture provenance, oracle, limit, and exact cleanup tests.
- Complexity/work-counter regressions for algorithms whose asymptotic behavior is part of the design.

## Validation

Run the full JMH suite and owning module semantic tests, `./gradlew :modules:mundane-map-performance-tests:check --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve benchmark coverage, parameters, fixtures, and any integration-only justification.
