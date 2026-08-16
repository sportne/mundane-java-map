# G19-152 — Complete GeoPackageBinary geometry profile

Status: Proposed
Depends on: G19-001, G19-011, G19-150, G19-151
Gate: G19
Type: HITL

## Goal

Implement every core and registered non-linear GeoPackage geometry, dimension, envelope, and assignment rule.

## Context

The decoder accepts a narrow 2D simple-feature subset and cannot retain GeometryCollection, Z/M,
empties, curves or surfaces as canonical domain values suitable for lossless read/write.

## Scope

- Parse/write complete GeoPackageBinary headers, versions, flags, endian forms, SRS IDs, empty bits and envelopes.
- Support XY/XYZ/XYM/XYZM core geometries and registered CircularString, CompoundCurve, CurvePolygon,
  MultiCurve, MultiSurface, Curve and Surface SQL-MM encodings, empties and nested collections.
- Enforce geometry-column declared type/assignability/Z/M/SRS/envelope/table constraints.
- Preserve canonical curved/surface data and provide explicit bounded linearization/tessellation for 2D consumers.
- Bound nesting/parts/rings/coordinates/ordinates/envelopes/topology/linearization/temp storage/owned bytes/work.

## Out of scope

- Deprecated user-defined geometry encoding and silently replacing stored curves with simple approximations.

## Acceptance criteria

- Applicable OGC geometry/non-linear-extension fixtures round-trip in both byte orders and all dimensions.
- Neutral conversions preserve type/dimensions or report explicit non-representability and tolerance.
- Invalid headers/types/assignment/envelopes/nesting/limits fail atomically with stable indexed diagnostics.

## Required tests

- Full type/header/endian/empty/envelope/Z/M/SRS/collection/curve/surface matrix and independent fixtures.
- Invalid/truncated/recursive/huge geometry, topology/linearization boundaries and fuzz corpus.

## Validation

Run module/geometry/corpus/fuzz checks, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve curve/surface domain APIs, linearization tolerances and external fixture evidence.
