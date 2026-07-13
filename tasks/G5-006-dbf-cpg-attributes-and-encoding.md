# G5-006 — DBF/CPG attributes and encoding

Status: Proposed
Depends on: G5-002
Gate: G5
Type: AFK

## Goal

Attach bounded, typed DBF attributes to shapefile features with deterministic CPG/charset handling
and stable record/field diagnostics.

## Context

G5-001 fixes the supported DBF field and encoding profile. G5-002 supplies stable SHP record identity
to which DBF rows are aligned.

## Scope

- DBF header, descriptor, and row parsing in `modules/mundane-map-io-shapefile`
- CPG sidecar parsing and charset resolution
- Public format-local `DbfEncoding` plus an immutable caller override in `ShapefileOpenOptions`
- Immutable attribute mapping and hand-built SHP/DBF/CPG fixture sets
- Integration with the existing source/cursor, metadata, viewer, diagnostic, and close paths

## Out of scope

- Memo files, indexes, DBF writing, locale-dependent coercion, and arbitrary code-page detection
- Changing source feature IDs based on DBF contents

## Acceptance criteria

- Header length, row length, row count, field count, descriptors, terminators, and field slices are
  validated against file size and all G5-001 limits before allocation.
- Memo-free dBASE III/IV/5 compatibility headers `0x03`, `0x04`, and `0x05` plus the approved `F`
  extension follow the exact header/descriptor ignore and validation rules. Supported scalar values
  use nullable G4 mappings; invalid values warn/substitute `AttributeNull`, while unsupported
  descriptors warn and are omitted.
- Deleted rows consume their physical SHP partner and yield no feature/warning. Too few or too many DBF
  rows terminate with the same stable count-mismatch diagnostic regardless of detection time.
- Encoding selection is caller-supplied `DbfEncoding`, recognized CPG, recognized language-driver
  byte, then explicit Windows-1252 fallback. UTF-8/ISO-8859-1 use `StandardCharsets`; Windows-1252/
  IBM437/IBM850 use committed exhaustive byte-to-code-point tables with explicit undefined-byte
  behavior and no `Charset.forName`/provider discovery.
- Attribute maps and values are immutable, preserve declared field order where observable, and
  defensively copy mutable inputs.
- The package-private field plan uses packed arrays in the existing shapefile package. Cursor reads
  pair one DBF row with each physical SHP ordinal, decode only selected fields for live matched
  records, preserve physical-field warning order and requested output order, and never read a whole
  row merely to project a subset.
- A missing DBF produces a present empty schema and empty attributes; a present DBF produces a present
  nullable schema containing supported fields in descriptor order. Unsupported fields cannot be
  selected through a known schema. The missing-table cursor performs no DBF row/count/value work and
  otherwise preserves the complete SHP validation/query path.
- Parse failures include stable sidecar, row, field name/index, and byte-offset context without
  leaking raw sensitive data.
- Opening and cursor allocations, decoded UTF-16 characters, DBF/CPG I/O, and cancellation use the
  approved prospective counters/checkpoints. Closing the source closes DBF before SHP, continues
  cleanup after a failure, and preserves the first failure with later failures suppressed.

## Required tests

- Hand-built tests for every supported field type and its blank, boundary, and malformed-to-null forms.
- Per-version header/descriptor tests pin ignored dBASE III reserved bytes, its byte-29 Esri LDID
  extension, and dBASE IV/5 transaction/encryption/MDX/index-flag behavior.
- Encoding tests for UTF-8 and at least one approved single-byte charset, conflicting hints, unknown
  CPG names, malformed bytes, and fallback behavior.
- CPG alias tests include accepted Esri `88591` and rejected near-miss `28591`.
- Three-way conflicts prove warnings occur once per differing physical hint in CPG-then-LDID order,
  including two equal lower hints that both differ from the caller selection.
- Exhaustive 256-byte mapping/checksum tests for each manual single-byte table, including every
  undefined Windows-1252 byte.
- Tests for deleted rows, count disagreement, duplicate/invalid field names, oversized descriptors,
  truncated rows, numeric overflow, invalid dates/logicals, and total-allocation limits.
- Source integration tests that match attributes to the correct SHP record.
- The shapefile-viewer fixture proves one non-ASCII typed attribute survives opening, a deleted row is
  suppressed, surviving geometry still fits/renders, and all paired files are released.
- Projection tests cover `ALL`, `NONE`, reordered `ONLY`, unknown/unsupported names, filtered records,
  physical-field diagnostic order, and absence of warnings from unselected values.
- Lifecycle tests cover DBF size mutation, short positional reads, early cursor close, source reuse
  after known failure/cancellation, reverse close order, a successful CPG read followed by close
  failure, and primary/suppressed cleanup precedence.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use only the approved `StandardCharsets` and committed manual single-byte tables with locale-
independent scalar parsing. Keep every package-private DBF/CPG peer in the existing
`io.github.mundanej.map.io.shapefile` package; do not create public `internal.*` types or external DBF
dependencies. External DBF library types must not enter `mundane-map-api`.
The task is independently implementable from G5-002. If G5-003 is already integrated, preserve its
validated SHX count optimization; otherwise use the required sequential DBF/SHP mismatch path and let
the shared-facade integrator compose the branches.
