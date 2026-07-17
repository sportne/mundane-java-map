# G5-007 — PRJ retention and recognized CRS

Status: Complete
Depends on: G5-002, G4-002
Gate: G5
Type: AFK

## Goal

Retain bounded PRJ metadata and associate explicitly recognized CRS definitions with shapefile
features without heuristic reprojection.

## Context

G4-002 defines immutable CRS metadata, explicit EPSG:4326/EPSG:3857 registration, and unsupported-CRS
diagnostics. PRJ is metadata input to that boundary, not permission to implement a general WKT
engine.

## Scope

- PRJ sidecar loading and metadata mapping in `modules/mundane-map-io-shapefile`
- Fixed format-local WKT1 tokenizer and the two approved structural matchers mapped to G4 definitions
- Fixtures for recognized and retained-unknown definitions
- Existing shapefile-viewer loading behavior for PRJ-derived CRS and an optional explicit override

## Out of scope

- General WKT parsing, EPSG database lookup, axis-order guessing, datum transformation, and PROJ
- Silently treating missing or unknown PRJ text as Web Mercator or WGS 84

## Acceptance criteria

- PRJ text is read as strict UTF-8 with byte/character limits; after an optional leading BOM, the
  exact decoded case and whitespace are retained while only matcher comparisons are normalized.
- No new public API is added: recognized/unknown results use G4 `CrsMetadata`, and the existing
  `ShapefileOpenOptions.crsOverride()` remains the only caller declaration.
- Only the exact approved ESRI WKT1 structures for EPSG:4326 and EPSG:3857 are recognized; other
  bounded syntactically valid definitions remain retained unknown metadata.
- Missing, blank, oversized, malformed, and unrecognized PRJ inputs have distinct stable diagnostic
  behavior and do not trigger an inferred transform.
- Recognized source CRS participates in the G4 query/rendering boundary and mismatches with the map
  CRS fail predictably or use an explicitly available transform.
- Unknown definitions remain inspectable metadata without external parser types in the public API.
- Recognition is directly constructed and reflection-free; no public/general recognizer registry is
  introduced for two built-in profiles.
- Override arbitration is exact: missing/blank PRJ may use the override, unknown PRJ may use it with
  a warning while retaining text, equal recognized input is clean, and a different recognized input
  is terminal. Malformed input is never concealed by an override.
- PRJ bytes, retained text, token spans, nesting state, cancellation, temporary channel close, and
  reverse partial-open cleanup follow the approved prospective accounting and lifecycle rules.

## Required tests

- Structural tests for the two recognized WKT trees and near-miss names, constants, order, and extras.
- Tests for missing, blank, mixed-case/whitespace, unknown, oversized, malformed, and conflicting
  definitions.
- UTF-8 tests cover absent/present BOM, malformed and truncated sequences, exact retention after BOM,
  the retained-character boundary, token/depth limits, and byte-accurate syntax diagnostics.
- Grammar tests pin bare WKT1 direction identifiers, `.5`/`5.`/exponent forms, controls, missing
  delimiters/arguments, trailing tokens, and exact first-failure/EOF offsets.
- Integration tests for recognized geographic/projected rendering and unsupported/mismatched CRS
  diagnostics.
- Shapefile-viewer tests exercise PRJ-derived CRS with no override and matching/conflicting explicit
  overrides without adding CRS guessing.
- Override-matrix, short-read/size-mutation, cancellation, temporary-close, partial-open cleanup, and
  metadata-after-close tests.
- Architecture tests for explicit registration and absence of a format-to-AWT dependency.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :modules:mundane-map-core:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Recognition must be deliberately narrow. Preserve input for future adapters, but do not claim that
retention means semantic understanding. Keep tokenizer, matcher, and reader peers package-private in
the existing `io.github.mundanej.map.io.shapefile` package; do not add a WKT model, parser SPI,
`internal.prj` package, external dependency, or registry alias.

Completed on 2026-07-14 with a package-private bounded PRJ reader, fixed-capacity WKT1 tokenizer, and
two exact structural matchers. Strict UTF-8 retention, syntax/token/depth/allocation limits, explicit
override arbitration, stable diagnostics, cancellation, and temporary-channel cleanup now feed the
existing immutable CRS metadata without aliases or heuristic transforms. Byte-authored fixtures and
the PRJ-aware viewer prove recognized EPSG:4326/EPSG:3857 paths, retained unknown definitions,
missing/blank/invalid distinctions, source-boundary failures, and ownership-safe rendering.
