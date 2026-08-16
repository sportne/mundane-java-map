# G19-177 — Transactional save, locking, backup, and recovery

Status: Proposed
Depends on: G19-176
Gate: G19
Type: HITL

## Goal

Make standalone and packaged workspace saves durable, conflict-aware, bounded, and recoverable across failures.

## Context

V1 atomically replaces one file but has no cross-process lease, source-generation conflict detection, bounded backup
policy or crash-recovery inventory. Complete project persistence must not silently overwrite another editor or lose both versions.

## Scope

- Define immutable save snapshots, document/resource generations/fingerprints, optimistic conflict checks and explicit
  save/save-as/force policy for both forms.
- Implement confined lock/lease naming, owner identity/expiry/stale policy, one writer lane, concurrent readers and
  race-free acquisition/release without metadata-selected arbitrary lock paths.
- Stage canonical output privately in the target directory, force files and directory metadata where supported,
  verify/reopen, atomically install and never silently weaken durability or replace behavior.
- Add policy-bounded complete verified backups and recognized temp/backup/journal recovery; validate identity/generation/
  integrity and require explicit choice when outcomes conflict. Never silently repair content.
- Aggregate primary/rollback/cleanup failures and handle cancellation, disk-full, permission, I/O, close, crash phase,
  process death and concurrent replacement while retaining one valid artifact or bounded recovery choice.

## Out of scope

- Collaboration/sync/version control, unbounded revision history, cloud locking, transparent repair and filesystem-
  independent guarantees stronger than the declared platform evidence.

## Acceptance criteria

- Successful save is durable to the declared platform boundary; conflicts never silently overwrite external changes.
- Every injected failure leaves the prior/current artifact valid or a deterministic explicitly selectable recovery set.
- Locks/backups/temps cannot escape the target scope, leak indefinitely, or disclose paths in public diagnostics.

## Required tests

- XML/package save/save-as/force, generations/fingerprints, cross-process lock/lease/stale-owner, concurrent read/write,
  backup retention and every recovery choice matrix.
- Disk-full/permission/I/O/fsync/rename/atomic-move unsupported/crash-at-phase/cancel/close/cleanup failures, symlink/
  replacement races, lock spoofing, duplicate recovery artifacts and supported filesystem/OS evidence.

## Validation

Run workspace fault-injection/locking/recovery/platform lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve locking identity/expiry, durability guarantees, backup retention defaults, recovery UX facts
and platform evidence before existing caller files can be updated under v2.
