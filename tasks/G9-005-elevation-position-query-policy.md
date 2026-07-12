# G9-005 — Elevation position-query policy

Status: Proposed
Depends on: G9-001, G9-003
Gate: G9
Type: AFK

## Goal

Query elevation at a geographic position using explicit, deterministic nearest-sample and bilinear
policies shared by DTED and future elevation sources.

## Context

G9-001 deliberately limits the base model to cell access. G9-003 supplies DTED grids. Coordinate
queries must define boundary, tie, no-data, and interpolation behavior instead of hiding format-specific
choices in readers.

## Scope

Format-neutral query contracts and core algorithms, DTED integration, public Javadocs, and focused
tests. Require callers to choose nearest-sample or bilinear mode. Nearest ties resolve toward the lower
row and then lower column. Bilinear interpolation ignores zero-weight neighbors but otherwise returns
no-data if any contributing sample is no-data. Bounds are inclusive at grid sample centers; positions
outside the represented grid return an explicit no-result rather than extrapolating.

## Out of scope

Bicubic interpolation, terrain-surface modeling, datum conversion, CRS reprojection beyond the
established boundary, slope/aspect queries, and format-specific query APIs.

## Acceptance criteria

- The query API requires an explicit interpolation mode and returns elevation with declared units or
  an explicit no-result.
- Exact samples, cell interiors, all four edges/corners, nearest ties, longitude/latitude ordering,
  no-data neighborhoods, and outside-grid positions follow the documented policy.
- Bilinear arithmetic is deterministic, rejects non-finite coordinates, and does not overflow indices.
- DTED callers use the shared algorithm; no interpolation logic is embedded in the DTED parser.
- Public contracts remain immutable, JDK-only, and independent of DTED and AWT types.

## Required tests

Unit tests with analytically checkable planes and no-data patterns; boundary/tie tests; non-finite and
outside-grid tests; integration tests querying known values from all three hand-built DTED levels.

## Validation

```bash
./gradlew :modules:mundane-map-core:test :modules:mundane-map-io-dted:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not silently choose interpolation based on zoom or source format. If another no-data policy is later
needed, add it as an explicit mode with its own tests.
