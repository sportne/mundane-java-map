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

- Private SHX parsing, packed index storage, and opener integration in
  `modules/mundane-map-io-shapefile`
- Internal indexed-record addressing and query integration with the existing shapefile source
- Hand-built SHP/SHX fixture pairs, mismatch/fault fixtures, and equivalent viewer coverage

## Out of scope

- Building or repairing SHX files
- Spatial trees, geometry simplification, DBF lookup, or other vendor index formats
- Changing the supported shape profile
- Public random-record/count APIs, SHX repair/writing, payload validation during preflight, or
  mid-cursor fallback

## Acceptance criteria

- The SHX header enforces file code `9994`, five zero reserved words, version `1000`, its own exact
  declared/captured length, and finite ordered XY bounds. Its fixed version therefore matches the
  already validated SHP version; shape type and canonical XY bounds are cross-checked with SHP, while
  SHX and SHP file lengths are not compared to each other.
- Each big-endian offset/length pair is converted with checked word-to-byte arithmetic and validated
  before publication. The first offset is 50 words, later offsets are strictly increasing and exactly
  match the next SHP frame, content lengths are at least two words and match SHP, and the final frame
  ends at exact SHP EOF.
- Opening preflights captured SHX size, trailing eight-byte alignment, entry count, Java-array capacity,
  component/physical-record/parser-allocation limits, and all fixed buffers before publishing one
  immutable packed `long[]`. It validates SHP framing but not record numbers or payloads.
- Indexed lookup returns the same record identity and geometry as sequential iteration.
- Query execution uses the index only for record addressing; result ordering remains stable and
  follows the G4 query/accounting contract. Indexed cursors re-read and verify every SHP record header
  before using the shared payload decoder and never spatially prune or expose random access. Existing
  G5-002 framing/limit failures precede index-length disagreement for a mutated record.
- Missing SHX warns and scans sequentially. Any unreadable, truncated, duplicate, out-of-order, or
  inconsistent present index is closed/discarded whole with exactly one approved
  `SHAPEFILE_SHX_IGNORED` reason and uses the same sequential path; a partially trusted index is never
  published. SHX-only limit/allocation failure recovers the same way without also reporting a limit
  error, while SHP I/O and cancellation remain terminal.
- Index storage is immutable, bounded by the configured record/allocation limits, and uses packed
  primitives rather than one object per entry.
- A cursor-time index/SHP disagreement is terminal and does not retry sequentially after partial
  publication. Closing the source invalidates indexed access, and no SHX handle survives opening.
- Missing/ignored warnings precede later staged DBF/CPG/PRJ behavior and appear only in the bounded
  opening report. No public API or viewer command changes.

## Required tests

- Unit tests for exact SHX header/size/entry validation, including all five reserved words,
  signed-zero bounds, empty indexes, first/last legal offsets, and ignored Z/M bytes.
- Paired indexed, missing-index, and ignored-index equivalence tests for IDs, geometry, null records,
  query filtering/accounting, order, payload diagnostics, and viewer fit/rendering.
- Negative tests for minus/equal/plus-one limits, allocation and arithmetic overflow, misalignment,
  duplicate/decreasing/gapped/overlapping offsets, truncation, count/EOF mismatch, and every SHP/SHX
  disagreement.
- Exact diagnostic tests for `SHAPEFILE_SHX_MISSING` and every ignored reason, warning order/cap,
  short header/entry/SHP-frame reads, final size mutation, SHX-versus-SHP I/O classification, and
  cursor-time mutation without fallback.
- Cancellation tests before/after each resource, read, allocation, validation, close, and publication
  boundary; cleanup-failure/custom-token tests; query/source lifecycle and file-release tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not conceal a bad index by returning partial data. Fallback happens only during opening, is
observable once through the source report, and uses G5-002's unchanged sequential oracle. A valid SHX
is an internal packed address table, not a public random-record facility or a spatial index. Preserve
the exact validation/diagnostic order in the G5 design.
