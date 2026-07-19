# G10-037 — GeoTIFF corpus, viewers, and performance

Status: Complete
Depends on: G10-036
Gate: G10
Type: HITL

## Goal

Demonstrate bounded GeoTIFF interoperability and operational behavior with independent fixtures,
complete viewers, and repeatable raster/elevation performance evidence.

## Context

G10-036 completes and hardens the supported profile. The design requires a small independent-writer
corpus and reuses `performanceEvidence` rather than introducing a GeoTIFF-only lane.

## Scope

Add legally redistributable fixtures covering both semantic routes and representative layout,
compression, CRS, color, and sample combinations; pin generator recipes/tool versions/licenses and
SHA-256 hashes; complete raster/elevation viewer modes and Javadocs; and add bounded window-read,
eager-elevation memory, and repeatable timing cases to `performanceEvidence`.

## Out of scope

A new corpus command, broad TIFF conformance claims, benchmark-gated builds, persistent caches,
format-specific acceleration, remote/range access, or production dependencies.

## Acceptance criteria

- Every checked-in independent fixture has reviewable provenance, redistribution terms, generation
  instructions, digest verification, and coverage mapped to the approved profile.
- Both viewer modes open and render representative fixtures and expose stable unsupported/malformed
  outcomes without widening the profile.
- Performance evidence records environment, warmup, bounded windows, eager elevation peak memory,
  and measurements sufficient to decide whether optimization deserves a separate task.

## Required tests

Fixture manifest/digest/provenance tests, independent-fixture read/query/render tests, viewer smoke,
Javadoc/doclint, tolerant rendering, and GeoTIFF additions to the performance evidence lane.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check --console=plain
./gradlew renderRegression performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoTIFF interoperability and performance review**. A maintainer approves the
fixture provenance/redistribution record, manually checks both viewer modes, and reviews the bounded
performance evidence before the task closes.

Implementation evidence (2026-07-19): the final canonical five-warmup/twenty-measurement lane and
`qualityGate` completed together in 2 minutes 23 seconds. Four aligned 64-by-64 raster windows had a
0.552 ms median and reported 262,144 decoded segment bytes. Eager publication of a 512-by-512 Int16
grid had a 1.670 ms median and an exact 4,261,256-byte logical open peak. These are environment-bound
measurements, not release thresholds. Both frozen semantics also passed the independent BASELINE
oracle; this bounded evidence does not justify a GeoTIFF optimization task.
