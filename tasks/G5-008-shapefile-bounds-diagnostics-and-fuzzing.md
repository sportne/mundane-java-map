# G5-008 — Shapefile bounds, diagnostics, and fuzzing

Status: Proposed
Depends on: G5-003, G5-005, G5-006, G5-007
Gate: G5
Type: AFK

## Goal

Harden every supported shapefile and sidecar path against malformed or hostile inputs with uniform
limits, stable diagnostics, and deterministic fuzz tests.

## Context

G5-002 through G5-007 implement all Level 1 records and sidecars. This task closes cross-cutting
parser gaps before real-world corpus and Native Image validation.

## Scope

- Shared limits, checked arithmetic, and diagnostic mapping in `mundane-map-io-shapefile`
- Adversarial hand-built fixtures and deterministic mutation/generation tests
- Architecture checks for prohibited parser/runtime mechanisms

## Out of scope

- Real-world corpus acquisition, nondeterministic long-running fuzz infrastructure, and performance
  tuning
- Recovery of corrupt geometry beyond the approved G5-001 policy

## Acceptance criteria

- One immutable limits value controls file bytes, records, record bytes, parts, points, fields, field
  widths, sidecar text, decoded text, and aggregate allocation with safe documented defaults.
- Every untrusted count, offset, length, multiplication, and addition is range-checked before seeking,
  slicing, or allocating.
- SHP, SHX, DBF, CPG, and PRJ failures map to stable structured codes with source, sidecar, record,
  part/field, byte offset, severity, and bounded cause context as applicable.
- Truncated, overlong, malformed, mixed-endian, inconsistent-sidecar, non-finite, and integer-overflow
  inputs terminate within configured work/allocation bounds without hangs, `OutOfMemoryError`, stack
  overflow, leaked resources, or unclassified parser exceptions.
- Deterministic fuzz tests use committed seeds, fixed iteration/work limits, reproducible failure
  output, and exercise both generated records and mutations of valid hand-built fixtures.
- A failure in one opened source cannot alter registry, charset, limit, or diagnostic behavior for a
  later source.
- Valid fixtures from earlier tasks retain identical observable features and diagnostics.

## Required tests

- Parameterized boundary tests at limit-1, limit, and limit+1 for every configured maximum.
- Targeted hostile fixtures for truncation at structural boundaries, endian swaps, offset/count
  overflow, huge declarations, invalid encodings, inconsistent sidecars, and malformed multipart
  tables.
- Fixed-seed fuzz tests with an assertion that outcomes are success or a known diagnostic family.
- File-handle/lifecycle tests after success, parser rejection, cancellation, and cursor abandonment.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep fuzzing deterministic and short enough for the normal JVM gate. Store only minimal reproducer
bytes. Never catch fatal VM errors to disguise an unchecked allocation.
