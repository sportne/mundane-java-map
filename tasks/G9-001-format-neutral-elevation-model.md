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
need separate public terrain models. The approved G4 model distinguishes retained unknown CRS
metadata from recognized transformable definitions and treats raster bounds as pixel edges; elevation
posts therefore need a separate boundary.

## Scope

`mundane-map-api` `ElevationSource`, immutable metadata, typed limits, and metre/international-foot/
US-survey-foot units; `mundane-map-core` `PackedElevationGrid`; public Javadocs; focused API/core and
architecture tests. Define at least two-by-two, axis-aligned sample-center bounds in a required
recognized-or-retained-unknown CRS; west-to-east columns, north-to-south rows, row-major ordering,
derived spacing and sample coordinates, finite samples, a separate packed no-data mask, checked
allocation limits, stable source diagnostics, defensive copies, and explicit close behavior.

## Out of scope

Coordinate interpolation, DTED parsing, image decoding, color ramps, hillshading, irregular terrain
meshes, affine/curvilinear/wrapped grids, bulk/window access, caches, and format-specific metadata. Do
not adapt elevation publicly to `RasterSource`, add an I/O module, or introduce inverse coordinate
lookup, nearest/interpolated position queries, native buffers, memory mapping, or background work.

## Acceptance criteria

- `ElevationSource` is a synchronous, externally serialized, closeable contract independent of
  `RasterSource`; immutable metadata, limits, and opening diagnostics survive close while sampling
  after close fails deterministically. Close inherits G4's idempotence, mark-before-cleanup,
  `SOURCE_CLOSE_FAILED`, primary/suppressed ordering, and no-retry behavior.
- Metadata requires finite positive-span sample-center bounds, at least two rows and columns, required
  CRS metadata, and an elevation unit; it derives checked sample count, positive representable
  spacing, and exact first/last sample coordinates under the documented row-major orientation.
- `PackedElevationGrid` defensively copies one exact-length `double[]` and `BitSet` into primitive
  row-major storage plus a packed mask, canonicalizes masked payloads and signed zero, rejects an
  unmasked non-finite value, and exposes only bounded `OptionalDouble` access.
- Typed positive limits default to 4,096 rows/columns, 16,777,216 samples, 136,314,880 retained sample
  bytes, and 256 warnings. Checked one-less/equal/plus-one and overflow failures use the existing
  `SOURCE_LIMIT_EXCEEDED` shape with `scope=elevationOpen`; the eager grid preflights the lower of the
  configured sample ceiling and `Integer.MAX_VALUE`, and supplied reports cannot retain more warnings
  than their effective ceiling. No new elevation diagnostic family is added.
- The model can represent a synthetic grid suitable for both a future DTED reader and an elevation
  GeoTIFF adapter without format-specific fields.
- No mutable storage, AWT/external/format type, hidden worker, generic source superclass, raster
  inheritance, or empty format module crosses the architecture boundary.
- Public API additions have complete Javadocs and pass the existing architecture constraints.

## Required tests

Unit tests for every unit conversion; metadata invariants, exact endpoints, orientation, adjacent-post
representability, checked product, and derived coordinates; defensive array/mask ownership; finite,
no-data, and signed-zero samples; boundary/invalid indices; source-ID report validation; each limit at
one-less/equal/plus-one plus arithmetic overflow; packed-array addressability; warning retention at
one-less/equal/plus-one; idempotent close, `SOURCE_CLOSE_FAILED` primary/suppressed ordering and
no-retry conformance, storage release, retained metadata/diagnostics, and closed sampling.
Architecture tests prove API/core dependency purity, prohibited-mechanism absence, no raster
inheritance, and no new I/O module.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep coordinate interpolation out of this task so G9-005 can define it explicitly. The sole HITL-free
implementation choice is the approved `double[]` plus packed-bit mask; a lazy/windowed representation
requires G9-007 evidence. See `design/G9-elevation-and-dted.md` for the authoritative contract.
