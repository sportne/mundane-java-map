# G19-157 — Builder-driven GeoPackage creation

Status: Proposed
Depends on: G19-150, G19-151, G19-152, G19-153, G19-154, G19-155, G19-156
Gate: G19
Type: AFK

## Goal

Create new conforming GeoPackage 1.4 files through immutable builders with safe derived defaults.

## Context

The adapter is read-only. Creation requires coordinated schemas, headers, core rows, extension declarations,
user tables, constraints, indexes, matrices and resources rather than ad hoc SQL calls.

## Scope

- Add package and typed feature/attribute/tile/coverage/relation/community-profile builders.
- Supply standards-compliant defaults for version/application ID, core SRS rows, timestamps, identifiers,
  extents, schemas, geometry flags, matrices, constraints, extension records, indexes and journal policy.
- Validate the complete proposed schema/reference/extension/limit graph before creating a target.
- Generate only fixed reviewed prepared SQL/DDL with validated quoted identifiers and deterministic object order.
- Stage files beside the target, initialize/populate transactionally, validate, sync and atomically replace.
- Bound schema objects/rows/blobs/indexes/temp disk/SQL/commit work and aggregate cleanup failures.

## Out of scope

- Arbitrary DDL/SQL, implicit extension invention, in-place replacement of an existing target and encryption.

## Acceptance criteria

- Minimal and full-profile builder outputs pass declared OGC tests and independent readers.
- Identical builder input produces semantically deterministic schema/data independent of process locale/time except explicit fields.
- Validation/disk/I/O/cancellation/cleanup failure preserves any prior target and removes owned staging safely.

## Required tests

- Builder/default/override/schema/profile/table/index/extension/empty/populated/golden-database matrices.
- Identifier/schema conflicts, invalid graphs, exact limits, disk-full, cancellation, failed sync/move and cleanup.

## Validation

Run module/writer/OGC/independent-reader checks, qualityGate, and `git diff --check`.

## Notes

None.
