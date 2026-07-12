# G9-001 — Format-neutral elevation model

Status: Proposed
Depends on: G8-004
Gate: G9
Type: AFK

## Goal

Provide a toolkit- and format-neutral model for regularly sampled elevation grids so DTED and later
elevation-bearing formats can expose the same observable behavior.

## Context

Level 1 establishes raster requests, CRS boundaries, structured diagnostics, and lifecycle rules.
Elevation is sampled numeric terrain data rather than a generic image, and GeoTIFF and DTED must not
need separate public terrain models.

## Scope

`mundane-map-api` elevation metadata and access contracts, `mundane-map-core` immutable packed-grid
implementation, public Javadocs, and focused API/core tests. Model row and column counts, geographic
bounds, sample spacing and ordering, elevation units, an explicit no-data representation, cell access,
and close/lifecycle behavior. Defensively copy caller-provided arrays and collections.

## Out of scope

Coordinate interpolation, DTED parsing, image decoding, color ramps, hillshading, irregular terrain
meshes, and format-specific metadata. Do not add an I/O module.

## Acceptance criteria

- Public immutable metadata describes geographic bounds, grid dimensions, horizontal resolution,
  elevation units, sample ordering, and no-data semantics without AWT or external-library types.
- A core implementation stores samples in packed primitive storage and does not expose a mutable
  backing array.
- Valid row/column lookup, boundary lookup, no-data cells, invalid dimensions, arithmetic overflow,
  and closed-resource use have deterministic behavior and stable diagnostics where applicable.
- The model can represent a synthetic grid suitable for both a future DTED reader and an elevation
  GeoTIFF adapter without format-specific fields.
- Public API additions have complete Javadocs and pass the existing architecture constraints.

## Required tests

Unit tests for metadata invariants, defensive copies, packed sample access, each elevation unit,
no-data values, boundary indices, invalid dimensions, allocation limits, and lifecycle behavior;
architecture tests for API dependency purity.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep coordinate interpolation out of this task so G9-005 can define it explicitly. Prefer primitive
storage selected from demonstrated precision needs; do not introduce a native buffer or native code.

