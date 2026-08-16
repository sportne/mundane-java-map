# G19-156 — Community vector-tile and style profiles

Status: Proposed
Depends on: G19-099, G19-136, G19-148, G19-151, G19-154, G19-164
Gate: G19
Type: HITL

## Goal

Implement separately named GeoPackage community profiles for vector tiles and embedded styling/symbology.

## Context

These useful extensions are not adopted GeoPackage 1.4 conformance classes. They must not be inferred or
reported as standard support, and their versioned declarations and evidence must remain isolated.

## Scope

- Freeze the OGC Vector Tiles Pilot generic/MVT 2.1/GeoJSON/attribute-correlation extension revisions and names.
- Implement vector layer/field metadata, tile data, optional deflate, identity/schema, Related Tables correlation,
  and selected compatible Releasable Basemap Tiles conventions through shared MVT/GeoJSON engines.
- Freeze and implement the community Styling and Symbology revision: style/symbol/resource records,
  associations/defaults/order/inheritance, MIME, SE/SLD and MapLibre codecs, SVG/raster resources.
- Preserve unknown style media opaquely; prohibit scripts/external authority and keep QGIS project storage excluded.
- Add independent opt-in/read-write options, diagnostics, limits, CRUD lifecycle, conformance reports and docs.

## Out of scope

- Counting either profile as GeoPackage 1.4 conformance, arbitrary community extensions, QGIS projects and code execution.

## Acceptance criteria

- Each profile activates only with exact declarations and has independent tests/support reporting.
- Vector/style records round-trip with compatible producers/consumers without weakening core integrity or authority.
- Unknown/malformed/mixed-version/over-budget community data cannot partially bind or commit.

## Required tests

- Declaration/version/vector encoding/layer/field/attribute/RBT/style/association/MIME/codec matrices.
- Absent/ambiguous extensions, malformed compressed tiles/styles/resources, security/limits and independent fixtures.

## Validation

Run module/community/MVT/style integration checks, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve the exact frozen community revisions, separate wording, codecs and interoperability evidence.
