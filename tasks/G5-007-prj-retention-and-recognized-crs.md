# G5-007 — PRJ retention and recognized CRS

Status: Proposed
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

## Out of scope

- General WKT parsing, EPSG database lookup, axis-order guessing, datum transformation, and PROJ
- Silently treating missing or unknown PRJ text as Web Mercator or WGS 84

## Acceptance criteria

- PRJ text is read with an explicit charset and byte/character limits, normalized only as required
  for the fixed structural matchers, and retained verbatim or losslessly for metadata access.
- Only the exact approved ESRI WKT1 structures for EPSG:4326 and EPSG:3857 are recognized; other
  bounded syntactically valid definitions remain retained unknown metadata.
- Missing, blank, oversized, malformed, and unrecognized PRJ inputs have distinct stable diagnostic
  behavior and do not trigger an inferred transform.
- Recognized source CRS participates in the G4 query/rendering boundary and mismatches with the map
  CRS fail predictably or use an explicitly available transform.
- Unknown definitions remain inspectable metadata without external parser types in the public API.
- Recognition is directly constructed and reflection-free; no public/general recognizer registry is
  introduced for two built-in profiles.

## Required tests

- Structural tests for the two recognized WKT trees and near-miss names, constants, order, and extras.
- Tests for missing, blank, mixed-case/whitespace, unknown, oversized, malformed, and conflicting
  definitions.
- Integration tests for recognized geographic/projected rendering and unsupported/mismatched CRS
  diagnostics.
- Architecture tests for explicit registration and absence of a format-to-AWT dependency.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Recognition must be deliberately narrow. Preserve input for future adapters, but do not claim that
retention means semantic understanding.
