# G19-153 — RTree indexes and bounded spatial query

Status: Proposed
Depends on: G19-152
Gate: G19
Type: AFK

## Goal

Implement the registered RTree extension, geometry SQL helpers, maintenance, validation, and query parity.

## Context

Feature queries currently scan tables. Expert direct-use requires standard spatial indexes without trusting
stale/corrupt index state or exposing unsafe dynamic SQL and SQLite trigger behavior.

## Scope

- Implement exact `gpkg_rtree_index` virtual/shadow tables, extension rows, trigger templates and SQL functions.
- Add create/rebuild/drop/validate/maintain operations and transaction integration for all geometry mutations.
- Use prepared envelope/index queries with exact geometry predicate verification, CRS conversion and stable ordering.
- Guarantee indexed/full-scan parity for null/empty/Z/M/curved/wrapped/boundary cases and bounded fallback.
- Validate identifiers/catalog ownership and reject shadowing, altered triggers, stale rows and corrupt structures.
- Bound candidates/scans/index rows/rebuild bytes/geometry decodes/query results/temp storage/work.

## Out of scope

- Arbitrary SQL spatial functions, unreviewed triggers, and silently trusting or repairing a corrupt index.

## Acceptance criteria

- Applicable OGC RTree tests pass and indexed/full-scan logical results/order are identical.
- CRUD and rollback maintain extension/index invariants exactly; explicit rebuild is deterministic and atomic.
- Malicious/stale/corrupt/over-budget indexes cannot broaden access or cause partial publication/commit.

## Required tests

- Present/absent/create/drop/rebuild/trigger/CRUD/query-boundary/full-scan parity matrix.
- Shadow objects, modified triggers/functions, stale/corrupt rows, cancellation, locking and exact limits.

## Validation

Run module/RTree/performance/database checks, qualityGate, and `git diff --check`.

## Notes

None.
