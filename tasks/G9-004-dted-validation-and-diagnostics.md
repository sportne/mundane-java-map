# G9-004 — DTED validation and diagnostics

Status: Proposed
Depends on: G9-003
Gate: G9
Type: AFK

## Goal

Make the DTED reader safe and predictable for malformed, truncated, inconsistent, or hostile inputs.

## Context

G9-003 establishes valid Level 0/1/2 reading. DTED mixes fixed-width text headers with binary data
records and signed-magnitude samples, so every count, offset, checksum, and allocation must be bounded
before corpus and Native Image verification.

## Scope

`mundane-map-io-dted` parser limits, checked arithmetic, validation, stable structured diagnostics,
and malformed-input tests. Validate UHL/DSI/ACC identifiers and fields, coordinate and interval
consistency, dimensions, profile sequence/counts, data sentinels, checksums, signed-magnitude values,
void values, trailing/truncated data, and configurable file/profile/sample/allocation limits.

## Out of scope

Repairing corrupt files, lenient undocumented dialects, automatic downloads, interpolation, rendering,
performance redesign, and native acceleration.

## Acceptance criteria

- User-configurable immutable limits are checked before allocation and have conservative defaults.
- Every malformed fixture fails or reports according to one documented policy with a stable diagnostic
  code, record/profile location when known, and a non-sensitive message.
- Truncation at every record boundary, impossible dimensions, overflowed offsets, invalid numeric
  fields, bad checksums, profile mismatch, invalid signed magnitude, and void samples are covered.
- Diagnostic ordering is deterministic and parser failures do not leak open channels or partially
  mutable results.
- A deterministic mutation/fuzz test uses a recorded seed and terminates within fixed input and
  allocation bounds.

## Required tests

Table-driven malformed-header and malformed-record tests; truncation tests; limit and overflow tests;
checksum and signed-magnitude boundary tests; deterministic fuzz/mutation tests; resource-leak checks.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Preserve enough location context to diagnose a bad profile without echoing arbitrary binary input.
Never allocate directly from an untrusted DTED count.

