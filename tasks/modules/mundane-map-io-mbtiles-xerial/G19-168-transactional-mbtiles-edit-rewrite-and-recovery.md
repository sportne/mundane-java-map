# G19-168 — Transactional MBTiles edit, rewrite, and recovery

Status: Proposed
Depends on: G19-167
Gate: G19
Type: HITL

## Goal

Add safe transactional CRUD for recognized writable layouts and explicit canonical rewrite for every other
read-compatible MBTiles 1.3 layout.

## Context

Compatible views can hide arbitrary physical designs. Guessing how to update them is unsafe, while refusing any
path forward makes direct-use support incomplete. The approved solution separates recognized updates from rewrite.

## Scope

- Open one owner-thread/lane read-write session only for exact recognized canonical/approved producer layouts;
  expose transactions/savepoints and metadata/raw/raster/MVT/UTFGrid insert/update/delete/bulk operations.
- Maintain indexes, deduplicated blobs, summaries, vector/grid metadata, cache generations and cross-table integrity;
  define duplicate/replace/merge policies and block format-changing edits without explicit whole-tileset rewrite.
- Classify unknown metadata/objects/dependencies. Preserve unrelated content exactly; block governed mutations when
  an unknown view/trigger/index/table depends on MBTiles objects unless an explicit reviewed policy owns it.
- Implement explicit safe rewrite by cloning to private storage, replacing only governed interfaces, copying
  validated logical content into a selected canonical layout, verifying, fsyncing and atomically installing.
- Define locks, busy timeout/retry, journal/WAL/synchronous modes, cancellation/interrupt, close aggregation,
  rollback, disk-full/I/O/corruption, backup, stale temp/recovery and concurrent-reader behavior.

## Out of scope

- Reverse-engineering arbitrary view SQL, arbitrary SQL access, silent repair/migration, syncing, remote databases,
  and preserving an unknown object that can execute against or corrupt a governed mutation.

## Acceptance criteria

- Recognized layouts update atomically; arbitrary compatible layouts are read-only until explicit rewrite.
- Rewrite preserves all logical MBTiles content, unknown metadata and unrelated objects/data while never executing
  an unknown governed trigger or leaving the source/target ambiguous.
- Every failure/cancellation/restart yields the previous valid file or one verified replacement with bounded recovery.

## Required tests

- CRUD/bulk/savepoint/index/dedup/summary/cache/metadata/vector/grid and flat/normalized transaction matrices.
- Compatible arbitrary views, recognized/unrecognized triggers/views/indexes, unknown object preservation/blocking,
  rewrite equivalence, concurrent access, busy/lock, disk-full/corruption/crash/fsync/rename/recovery and ownership tests.

## Validation

Run MBTiles edit/rewrite/fault-recovery and independent reopen lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve recognized writable schemas, unknown-object dependency policy, locking/durability defaults,
and crash-recovery evidence before enabling mutation of caller files.
