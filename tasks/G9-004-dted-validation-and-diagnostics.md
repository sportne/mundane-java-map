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
before corpus and Native Image verification. Follow MIL-PRF-89020B sections 3.10 through 3.13 and the
approved strict profile in `design/G9-elevation-and-dted.md` without broadening it.

## Scope

Add immutable public `DtedLimits` and its `DtedOpenOptions` binding; checked cumulative allocation
accounting; complete fixed-field UHL/DSI/ACC grammar/profile/consistency validation; exact file and
data-record framing; mandatory unsigned-byte checksums; signed-magnitude range and void policy; stable
structured diagnostics and precedence; size-race/resource cleanup checks; table-driven malformed and
truncation fixtures; and a bounded deterministic public-facade mutation test.

## Out of scope

Repairing corrupt files, lenient undocumented dialects, variable-length partial profiles, enabling
partial SRTM Level 1 or accuracy subregions, automatic downloads, a real-world corpus, interpolation,
rendering, performance redesign, lazy/windowed I/O, publication changes, and native acceleration.

## Acceptance criteria

- `DtedLimits` has the exact six positive ceilings, defaults, withers, value behavior, Javadocs, and
  `DtedOpenOptions` integration in the design; format limits precede shared elevation limits and every
  project-owned primitive allocation/copy is prospectively charged.
- Every reached UHL, DSI, and supported-profile ACC byte is classified as consumed grammar, required
  blank, or bounded printable free text; fields are checked in physical order, origin/interval/
  dimension declarations agree exactly, accuracy flags agree by documented boolean equivalence, and
  a valid unsupported ACC discriminator terminates before its unused payload is parsed.
- Initial/final file size, checked expected length, complete fixed records, preamble/profile counts,
  and mandatory unsigned-byte checksum are enforced with no prefix, trailing-data, repair, or
  checksum-disable path.
- Signed magnitude canonicalizes the `0x8000` negative-zero encoding to positive zero, maps `0xffff`
  only to permitted partial-Level-2 no-data, and rejects other values outside `-12000..9000` with the
  exact documented diagnostic; complete-cell voids are terminal.
- The ten-code DTED diagnostic vocabulary, locations, bounded context, I/O mapping, limit scopes, and
  total precedence are asserted exactly; failures close transaction resources once, preserve
  primary/suppressed order, and never publish a partial source.
- Independent tables cover every header class, required/unsupported mismatch, limit and arithmetic
  boundary, fixed-section/Level-0-record truncation boundary, checksum, signed-magnitude, void,
  cancellation, size mutation, and cleanup outcome.
- The public-opener mutation test uses seed `0x4454454447393034`, exactly 64 bounded cases, two
  identical runs in fresh directories, a 65,536-byte input ceiling, a 2 MiB parser-allocation ceiling,
  normalized success/failure outcomes, and a 30-second aggregate timeout.
- Production remains JDK-only/AWT-free and adds no dependency, reader framework, cache, native code,
  public header/record type, or new verification lane.

## Required tests

Public-value/Javadoc tests; table-driven header/profile/cross-header and record diagnostics; exact
truncation, limit, allocation, overflow, checksum, signed-magnitude, void, file-size race,
cancellation, I/O, and cleanup tests; deterministic mutation repeatability/timeout tests; architecture
tests for module and prohibited-mechanism boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Preserve enough location context to diagnose a bad physical profile without echoing paths, raw bytes,
free text, datum/producer strings, or localized exceptions. Format limit failures use
`scope=dtedOpen`; shared elevation limit failures retain `scope=elevationOpen`. A fixed-array file
with the paired left-justified `SRTM` and ACC `X` markers is not rejected solely for that provenance,
but the pair enables no profile behavior; a mismatched pair is terminal header inconsistency.
Never allocate directly from an untrusted DTED count. Do not run publication, corpus, rendering,
performance, or Native Image lanes in this task.
