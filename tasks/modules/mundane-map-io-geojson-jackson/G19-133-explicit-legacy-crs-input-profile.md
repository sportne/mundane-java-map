# G19-133 — Explicit legacy CRS input profile

Status: Proposed
Depends on: G19-010, G19-012, G19-132
Gate: G19
Type: HITL

## Goal

Accept common obsolete GeoJSON `crs` input only through an explicit registered, audited, bounded reprojection profile
while keeping strict RFC 7946 behavior and output unchanged.

## Context

RFC 7946 removed `crs` and mandates WGS 84, but older datasets often use named or linked CRS objects. Strict rejection
is correct by default yet prevents controlled migration of common legacy data.

## Scope

- Add strict/default versus legacy-input options; strict mode continues rejecting `crs` at every scope.
- Parse the documented old `name`/`link` object shapes, cardinality/scope/conflict rules, and retain original metadata for audit.
- Resolve only exact caller-registered identifiers/media; a link is an identifier and never network/file/schema fetch authority.
- Reproject coordinates/bboxes to RFC 7946 WGS 84 using core CRS/raster-vector operations with explicit axis/unit/domain/
  precision/Z/tail/topology/antimeridian behavior and prospective transformation/work limits.
- Publish only the fully transformed candidate; expose stable unknown/ambiguous/incompatible/transform/limit diagnostics.

## Out of scope

- Legacy `crs` writing, automatic CRS detection, URL fetching, arbitrary WKT parsing at this boundary, and silent axis guessing.

## Acceptance criteria

- Registered legacy data transforms reproducibly to strict RFC 7946 semantics and retains a complete audit record.
- Strict mode and unknown/unregistered/linked-resource cases perform no ambient I/O and reject before publication.
- Transformation/topology/limit failures leave no partial source/document state.

## Required tests

- Strict/legacy scope, name/link object, registry, axis/unit/Z/bbox/antimeridian/reprojection/audit matrices.
- Unknown/ambiguous identifiers, SSRF/file/schema probes, invalid domains/topology, precision/work limits, independent legacy fixtures.

## Validation

Run module and CRS/reprojection/corpus tests, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact legacy shapes/registry, transformation policy/tolerances, and fixture evidence.
