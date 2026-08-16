# G19-097 — SE RasterSymbolizer and CoverageStyle

Status: Proposed
Depends on: G19-012, G19-074, G19-094
Gate: G19
Type: HITL

## Goal

Implement the approved SE 1.1 RasterSymbolizer and CoverageStyle surface through the neutral bounded
raster/elevation portrayal contracts.

## Context

RasterSymbolizer and CoverageStyle are currently rejected, leaving a major standard portrayal family absent.

## Scope

- Implement coverage-style/rule orchestration, opacity, overlap behavior, channel selection, source-channel
  expressions, grayscale/RGB mapping, and missing-channel semantics.
- Add normalize/histogram contrast enhancement and gamma behavior with explicit statistics availability,
  numeric precision, and deterministic fallback/failure policy.
- Add categorized/interpolated color maps, quantities, labels, opacities, fallback values, interpolation
  modes, and approved value-domain/no-data behavior.
- Implement shaded relief, brightness-only semantics, relief factor, scale/unit/vertical metadata, and
  interaction with source elevation/raster transforms.
- Bound color-map entries, statistics reads, raster samples/windows, resampling, intermediate pixels/bytes,
  and relief work; preserve atomic portrayal and stable diagnostics.

## Out of scope

- Vendor raster functions, GPU shaders, arbitrary image processing, and ambient coverage retrieval.

## Acceptance criteria

- Approved raster/coverage styles render deterministically and agree across AWT/Vaadin within tolerances.
- Channel, contrast, color-map, overlap, no-data, and relief semantics match the pinned SE contract.
- Missing metadata/resources and all work/byte limits fail before partial scene publication.

## Required tests

- Channel/contrast/gamma/color-map/interpolation/opacity/overlap/no-data/relief/UOM matrix.
- Raster/elevation goldens, statistics-present/absent paths, numeric extremes, hostile maps/windows, and parity.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, raster/rendering/corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves raster/color/relief tolerances and independent-renderer evidence.
