# G5-001 — Shapefile supported-profile decision

Status: Proposed
Depends on: G4-002, G4-003
Gate: G5
Type: HITL

## Goal

Approve a bounded, read-only shapefile profile so the first format module has explicit compatibility,
failure, and resource-limit rules before binary parsing begins.

## Context

The format-neutral feature-source and CRS work from G4 is the integration boundary. The decision
must be based on the ESRI Shapefile Technical Description and the dBASE field formats actually
needed by the Level 1 viewer, not on undocumented behavior from a third-party GIS library.

## Scope

- `DESIGN.md` and `ROADMAP.md` sections that define the Level 1 shapefile profile
- `modules/mundane-map-api` only if an already-approved source or diagnostic contract needs Javadocs
- Test-fixture conventions for the future `mundane-map-io-shapefile` module

## Out of scope

- Creating the shapefile module or an empty parser scaffold
- Writing shapefiles, editing records, spatial-index formats other than SHX, or arbitrary CRS parsing
- Z/M geometry support unless the approved profile explicitly promotes it

## Acceptance criteria

- The profile names the supported 2D shape codes: null, point, multipoint, polyline, and polygon.
- Required and optional behavior for SHP, SHX, DBF, CPG, and PRJ sidecars is explicit, including
  basename matching, missing files, record-count disagreements, and inconsistent sidecars.
- Supported DBF field types, blank/null values, deleted rows, numeric/date/logical conversion, and
  unsupported-field behavior are defined.
- Encoding precedence is deterministic across CPG, caller-supplied charset, DBF language driver, and
  a documented fallback; invalid or unknown encodings produce stable diagnostics.
- Z/M shape codes are either rejected or reduced according to one documented policy; they are never
  accepted accidentally.
- Default and configurable maxima are selected for file bytes, records, record bytes, parts, points,
  fields, field widths, text bytes, and total allocation.
- Diagnostic severity, stable code, source/sidecar, record number, field name/index, byte offset, and
  cause-summary requirements are fixed without exposing parser implementation types.
- Sequential/indexed access and resource ownership behavior are mapped to the G4 contracts.
- The decision records the exact specifications consulted and leaves no format-policy decision to
  G5-002 through G5-010.
- **HITL checkpoint:** a maintainer approves the profile, limits, encoding fallback, and Z/M policy
  before G5-002 starts.

## Required tests

- No new format tests; validate that any changed public Javadocs and source contracts still compile.
- Review the proposed hand-built fixture matrix against every accepted shape and sidecar case.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the profile deliberately smaller than general shapefile compatibility. Production parsing must
remain JDK-only, bounded, non-reflective, and suitable for Native Image. Do not add a module until
G5-002 delivers working behavior and tests.
