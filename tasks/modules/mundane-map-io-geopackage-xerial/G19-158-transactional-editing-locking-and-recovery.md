# G19-158 — Transactional editing, locking, and recovery

Status: Proposed
Depends on: G19-157
Gate: G19
Type: HITL

## Goal

Provide failure-safe transactional CRUD, schema evolution, locking, cancellation, validation and recovery.

## Context

GeoPackage is a direct-use format. Create-only output is incomplete, while careless updates can corrupt
catalogs, indexes, extensions, relationships or unknown vendor state despite SQLite transactionality.

## Scope

- Add one explicitly owned writer lane with transactions/savepoints and typed feature/attribute/tile/coverage/
  metadata/relation/style/vector-profile insert/update/delete/bulk operations.
- Add schema-safe create/rename/drop/add-column/constraint/index/extension operations where GeoPackage permits.
- Maintain contents/extents/timestamps, geometry/table constraints, matrices, ancillary data, RTree and relations atomically.
- Define isolation, WAL/journal/synchronous modes, busy timeout/retry, readers/writer concurrency, cancellation/
  interrupt, commit/rollback, disk-full/I/O/corruption handling, close aggregation and lock release.
- Check unknown extension scopes prospectively and preserve unrelated objects exactly.
- Provide validation and explicit bounded repair-plan APIs; never auto-repair or migrate on open.

## Out of scope

- Arbitrary SQL, concurrent uncoordinated writers, transparent repair/migration, encryption and remote syncing.

## Acceptance criteria

- Every successful mutation leaves a conforming package; every failed/cancelled mutation restores the exact prior transaction state.
- Concurrent readers/writer and busy/interrupt/crash/disk scenarios have documented deterministic outcomes and no leaked locks.
- Unknown-extension governed mutations fail before transaction start; unrelated state survives byte/semantic checks.

## Required tests

- CRUD/bulk/schema/index/relation/coverage/community/transaction/savepoint/isolation/locking matrices.
- Constraint, busy, deadlock, cancel, disk-full, process-crash recovery, corrupt journal, cleanup failure and unknown preservation.

## Validation

Run module/database fault-injection/concurrency/OGC checks, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve concurrency, journal/durability, repair, backup and crash-recovery contracts.
