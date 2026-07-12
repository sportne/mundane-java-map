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
- Supported character, numeric, floating, logical, and date fields follow the approved conversion
  rules for blank/null/invalid values; unsupported fields follow the approved skip/fail policy.
- Deleted rows and SHP/DBF count mismatches have deterministic feature and diagnostic behavior.
- Charset selection follows the exact CPG/caller/language-driver/fallback precedence from G5-001,
  with bounded CPG text and no platform-default charset.
- Attribute maps and values are immutable, preserve declared field order where observable, and
  defensively copy mutable inputs.
- Parse failures include stable sidecar, row, field name/index, and byte-offset context without
  leaking raw sensitive data.
- Closing the source closes DBF resources along with SHP resources.

## Required tests

- Hand-built tests for every supported field type and its blank, boundary, and malformed forms.
- Encoding tests for UTF-8 and at least one approved single-byte charset, conflicting hints, unknown
  CPG names, malformed bytes, and fallback behavior.
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

Use explicit `Charset` instances and locale-independent parsing. External DBF library types must not
enter `mundane-map-api`.
