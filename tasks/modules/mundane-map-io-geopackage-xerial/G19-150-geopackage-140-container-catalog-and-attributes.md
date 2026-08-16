# G19-150 — GeoPackage 1.4 container, catalog, and attributes

Status: Proposed
Depends on: G19-010
Gate: G19
Type: AFK

## Goal

Complete GeoPackage 1.4 core container, spatial-reference, contents, feature/tile catalog, and attributes-table support.

## Context

The reader recognizes feature/tile subsets but lacks complete core/configuration integrity, attribute content,
catalog relationships, WKT2 CRS handling, and the typed model required for safe creation and editing.

## Scope

- Freeze OGC 12-128r19 applicable core/features/tiles/attributes requirement inventories and fixtures.
- Validate SQLite format/header/application/user version, required pragmas/configuration, core tables and integrity.
- Model complete `gpkg_spatial_ref_sys` including WKT2/epoch extension data and explicit CRS registry mapping.
- Model `gpkg_contents`, identifiers/descriptions/timestamps/extents/SRS, feature/tile/attribute cross-references.
- Add typed immutable attribute table/schema/row/cursor/query values with safe identifier and SQL-type semantics.
- Bound schemas/tables/columns/rows/text/blobs/CRS/catalog references/query output/owned bytes and work.

## Out of scope

- Ambient CRS lookup, arbitrary SQL, transparent repair, encryption, and remote database files.

## Acceptance criteria

- Applicable core and attribute conformance requirements pass for independent producer files.
- Catalog/CRS/table relationships are complete and stable across inspection, read, and writer preparation.
- Malformed/version/configuration/schema/identifier/limit failures occur before partial session publication.

## Required tests

- Core header/pragmas/SRS/WKT2/contents/feature/tile/attribute schema and empty-package matrices.
- Corrupt catalogs, malicious identifiers/SQL types, huge schema/data, CRS conflicts and exact limits.

## Validation

Run module/OGC/database corpus checks, qualityGate, and `git diff --check`.

## Notes

None.
