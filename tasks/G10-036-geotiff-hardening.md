# G10-036 — GeoTIFF hardening

Status: Proposed
Depends on: G10-035
Gate: G10
Type: AFK

## Goal

Close the GeoTIFF raster and elevation security envelope with exact limits, diagnostics,
cancellation, cleanup, and deterministic hostile-input evidence.

## Context

G10-030 through G10-035 implement every approved raster/elevation success path. G10-003 specifies the
closed tag/GeoKey/segment grammar, allocation model, diagnostic vocabulary, and precedence.

## Scope

Complete exact/equality/one-over limit coverage; unsigned arithmetic and range overflow; tag, IFD,
payload, segment, and citation alias/overlap rules; malformed/unsupported precedence; snapshot
mutation; cancellation; source reuse/close; data-leak canaries; cleanup suppression; and bounded
deterministic byte mutation across both routes.

## Out of scope

New TIFF tags, formats, compression, recovery, repair, warnings, persistent caching, performance
optimization, or additional verification commands.

## Acceptance criteria

- Every stable G10-003 diagnostic/context and every `GeoTiffLimits` ceiling has direct equality,
  one-over, overflow, and precedence evidence where applicable.
- Aliased/overlapping/misaligned/truncated/hostile inputs cannot cause unbounded allocation, partial
  publication, stale state, or raw path/data/value leakage.
- Cancellation and cleanup preserve the first terminal outcome, release resources deterministically,
  and leave reusable operations reusable exactly where specified.

## Required tests

Complete malformed/profile/limit matrix, deterministic bounded mutation, data-leak canaries,
cancellation checkpoint tests, snapshot-change tests, raster reuse, source close, cleanup-failure
suppression, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep mutation deterministic and short enough for the normal quality loop. Do not turn unsupported
profiles into recovery warnings.
