# G19-074 — GeoTIFF elevation, no-data, scale, and vertical metadata

Status: Proposed
Depends on: G19-072, G19-073
Gate: G19
Type: HITL

## Goal

Complete numeric elevation interpretation, no-data/mask precedence, scale/offset behavior, units,
and bounded vertical/3D metadata interoperability without overstating GeoTIFF 1.1 conformance.

## Context

The reader accepts one signed/float band and a caller-provided unit. It does not expose raw scale/
offset or vertical CRS metadata, and GeoTIFF 1.1's compound/3D guidance is informative rather than a
complete normative encoding model.

## Scope

- Support the declared signed/unsigned integer and IEEE float elevation profiles through lossless raw
  access and checked physical-value scale/offset conversion.
- Freeze GDAL no-data, NaN, alpha, transparency-mask, and missing-block precedence for elevation.
- Preserve supported vertical keys, units, datums, citations, and GeoTIFF Annex D 3D/compound
  recommendations as explicitly labelled interoperability metadata.
- Require caller policy when vertical meaning is absent or cannot be represented by the core CRS.
- Integrate exact elevation windows and core warping with bounded precision/work/no-data behavior.

## Out of scope

- Claiming normative GeoTIFF 1.1 conformance for informative Annex D behavior, geoid-model lookup,
  implicit vertical conversion, and terrain void filling.

## Acceptance criteria

- Raw samples, physical elevations, no-data masks, units, and supported vertical metadata agree with
  independent tools for the declared profiles.
- Missing/ambiguous vertical semantics never silently become a different datum or unit.
- Scale/offset overflow, non-finite values, and mask conflicts have stable atomic outcomes.

## Required tests

- Integer/float extremes, scale/offset, finite/NaN no-data, alpha/mask precedence, missing blocks,
  vertical unit/datum/citation, Annex D examples, unsupported vertical operations, precision,
  cancellation, and limits.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, elevation/GeoTIFF corpus lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the vertical/3D interoperability statement, sample-conversion
policy, and independent comparison tolerances before completion.
