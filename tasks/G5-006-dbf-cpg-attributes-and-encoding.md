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
- Immutable attribute mapping and hand-built SHP/DBF/CPG fixture sets

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
- Parse failures include stable sidecar, row, field name/index, and byte-offset context without
  leaking raw sensitive data.
- Closing the source closes DBF resources along with SHP resources.

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

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use only the approved `StandardCharsets` and committed manual single-byte tables with locale-
independent scalar parsing. External DBF library types must not enter `mundane-map-api`.
