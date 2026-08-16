# G19-073 — GeoTIFF 1.1 GeoKeys, georeferencing, and horizontal CRS

Status: Proposed
Depends on: G19-010, G19-070
Gate: G19
Type: HITL

## Goal

Implement the applicable OGC GeoTIFF 1.1 GeoKey, raster-to-model transformation, and horizontal CRS
requirements against the expanded core CRS model.

## Context

Current support handles a small sorted key subset, basic tiepoint/scale or affine placement, and only
EPSG:4326/EPSG:3857 identities.

## Scope

- Inventory and implement applicable OGC GeoTIFF 1.1 requirements/tests for TIFF, configuration
  GeoKeys, raster-to-model transformations, registered CRS, and user-defined CRS targets.
- Parse/validate complete key-directory headers/entries, SHORT/DOUBLE/ASCII locations, citations,
  sorting, duplicates, private/unknown values, and GeoTIFF 1.0 backward compatibility.
- Support PixelIsArea/PixelIsPoint, tiepoint/scale, transformation matrices, and exact control-point
  validation/precedence.
- Map declared geographic/projected/geocentric CRS, units, datums, ellipsoids, prime meridians,
  coordinate-operation methods/parameters, and representable user-defined definitions to core CRS.
- Preserve well-formed unsupported definitions and report operation unavailable without silently
  substituting or querying a runtime authority database.

## Out of scope

- Vertical/3D interoperability, runtime network/database lookup, and approximate datum operations.

## Acceptance criteria

- Every claimed GeoTIFF 1.1 requirements class passes its applicable tests for reader behavior.
- Cross-vendor control points, axes, units, methods, and user-defined definitions agree with the core
  CRS model or retain stable unsupported metadata.
- Conflicting or malformed tags/keys fail deterministically without discarding diagnosable metadata.

## Required tests

- Complete key/method/unit/datum/ellipsoid/prime-meridian/user-defined/raster-type matrix and OGC
  ATS fixtures; conflicting, malformed, duplicate, private, oversized, and numeric-boundary cases.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, OGC/corpus lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact OGC requirements-class statement, EPSG snapshot/
mapping evidence, and external fixtures before completion.
