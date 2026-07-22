# G16-004 — Dateline vector geometry and export

Status: Proposed
Depends on: G16-003
Gate: G16
Type: AFK

## Goal

Render and export dateline-touching and crossing lines, polygons, holes, and multipart geometry on
the short wrapped path without long chords, invalid rings, or unstable copy ordering.

## Context

G16-003 supplies explicit wrap configuration, canonical query results, and point copies. Geographic
segments require unwrapping and seam splitting before strict projection. G7 clipping/simplification,
G11 portrayal/labels, and detached vector export must consume the same valid planned geometry.

## Scope

Implement the approved bounded geographic shortest-path unwrapping, seam intersection, line
multipart splitting, ring clipping/closure, polygon-part and hole retention, packed output, limits,
cancellation, and record diagnostics. Render repeated line/fill/endpoint/hatch/composite portrayal,
labels, hover-independent overlays, and capture visible copies in vector export with aggregate
snapshot accounting. Cover in-memory, shapefile, and GeoJSON literal-versus-explicit-wrap behavior.

## Out of scope

Self-intersection or topology repair, geodesic densification, automatic wrapping, projected-source
seam inference, interaction/editing, rasters, or changes to format serialization.

## Acceptance criteria

- Supported geographic lines and polygons crossing ±180 render as bounded seam-adjacent pieces with
  preserved multipart/ring/hole semantics.
- Exact seam touches, repeated seam coordinates, half-period ties, empty fragments, and unsupported
  ambiguous/invalid rings follow the approved deterministic result or stable diagnostic.
- Already projected and literal non-wrapped sources retain existing behavior unless explicitly
  configured otherwise.
- Labels, symbols, clipping/simplification, and SVG export use the same visible copies and enforce
  existing plus aggregate wrap limits atomically.

## Required tests

Point/line/multiline/polygon/hole/multipolygon seam fixtures; winding and closure; exact ties;
hostile crossing/part/coordinate limits; cancellation; source types; portrayal and label ordering;
screen optimization; export inventories and deterministic SVG; rendering regression and diagnostics.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-io-svg:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use packed primitive geometry and checked prospective inventories. Do not introduce JTS or another
topology dependency; inputs that require repair remain outside the supported profile.
