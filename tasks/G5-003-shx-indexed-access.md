# G5-003 — SHX indexed access

Status: Proposed
Depends on: G5-002
Gate: G5
Type: AFK

## Goal

Use a validated SHX sidecar for deterministic record lookup and bounded feature queries while
preserving the approved behavior for missing or inconsistent indexes.

## Context

The sequential source in G5-002 is the fallback and correctness oracle. SHX is a record-offset index,
not a spatial index; viewport acceleration remains G7 work.

## Scope

- SHX parsing and index storage in `modules/mundane-map-io-shapefile`
- Random-record and query integration with the existing shapefile source
- Hand-built SHP/SHX fixture pairs and mismatch fixtures

## Out of scope

- Building or repairing SHX files
- Spatial trees, geometry simplification, DBF lookup, or other vendor index formats
- Changing the supported shape profile

## Acceptance criteria

- The SHX header is cross-checked with SHP version, shape type, bounds, and declared length.
- Each big-endian offset/length pair is converted with checked word-to-byte arithmetic and validated
  for alignment, header overlap, file bounds, monotonicity policy, and matching SHP record length.
- Indexed lookup returns the same record identity and geometry as sequential iteration.
- Query execution uses the index only for record addressing; result ordering remains stable and
  follows the G4 query contract.
- Missing, truncated, duplicate, out-of-order, or inconsistent index entries follow the approved
  fallback/failure policy and emit stable sidecar diagnostics.
- Index storage is immutable, bounded by the configured record/allocation limits, and uses packed
  primitives rather than one object per entry.
- Closing the source invalidates indexed access without leaking file handles.

## Required tests

- Unit tests for valid SHX header and entry decoding, including the first and last legal offsets.
- Paired sequential-versus-indexed equivalence tests.
- Negative tests for overflow, misalignment, overlap, truncation, count mismatch, and SHP/SHX
  disagreement.
- Query-order and source-lifecycle integration tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not conceal a bad index by silently returning partial data. Any fallback must be explicitly
permitted by G5-001 and observable through diagnostics.
