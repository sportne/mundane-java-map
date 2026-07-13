# G11-001 — Editing, undo, and snapping model

Status: Proposed
Depends on: G8-004
Gate: G11
Type: HITL

## Goal

Approve a bounded, point-first editing design in which an application-owned session atomically
replaces immutable feature records, preserves revision-checked undo/redo, and snaps through an
explicit same-CRS vertex/segment query before production editing work begins.

## Context

Level 1 public values are immutable, sources are read-only, and interaction tools have explicit
lifecycle. The authoritative architecture is the
[G11 editing design](../design/G11-editing-styling-persistence-adapters-export.md#editing-undo-and-snapping-model-g11-001),
which preserves those properties instead of making geometries, layers, or source adapters mutable.

## Scope

Approve immutable create/replace/delete commands, snapshot and result values, one externally serialized
owner-thread core session, atomic revision semantics, stable edit problems, bounded snapshot/delta
history accounting and whole-entry eviction, non-reentrant event ordering, an explicit borrowed AWT
binding, and a view-bound point controller. Fix the first snap profile to bounded cancellable same-CRS
vertex/segment candidates with explicit forward/reverse operations, inclusive pixel tolerance, and
deterministic ties. Define point create/move/delete, selection, preview, cancellation, and four later
vertical slices.

## Out of scope

Production editing APIs, implementation task files, collaborative editing, line/polygon editing,
topology engines, persistence, format write-back, cross-CRS/live-source snapping, unbounded history,
hidden mutation of source features, and coupling public APIs to JTS.

## Acceptance criteria

- The named checkpoint approves owner-thread session/binding/controller ownership, closed immutable
  commands, atomic staging, monotonic revisions, rejection/unchanged behavior, and complete rollback.
- Current snapshots and undo/redo have deterministic logical-payload accounting; whole-entry eviction,
  branch replacement, reverse/forward delta replay, selection continuity, reentrant-mutation rejection,
  and post-commit listener failure behavior are fixed.
- Binding replacement clears an active dependent controller before detach, while a captured viewport
  mismatch suppresses preview and rejects release without touching session state.
- Move captures only when the current complete-snapshot point selection is the existing symbol-aware
  topmost hit; invalid selection and delete outcomes use one stable reasoned rejection.
- Snapping fixes same-CRS vertex/segment candidates, inclusive logical-pixel tolerance, distance/type/
  explicit-reference-order/geometry-index ties, exact forward/reverse CRS operations, exclusion,
  bounded cancellation,
  gesture snapshots, limits, and mismatch rejection.
- G11-010 through G11-013 each leave a runnable point-edit behavior through the real stack; no empty
  module, mutable geometry API, source write-back path, or speculative topology framework is planned.

## Required tests

No production tests. The design specifies later API immutability, owner-thread/session state,
snapshot/history bounds, atomic rollback, eviction, notification failure, controller/selection/tool
lifecycle, binding/view invalidation, render integration, and snapping geometry/cancellation/boundary/
tie/limit tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 editing command and snapping profile approval**. The maintainer approves the
immutable-record session, transaction/history/event semantics, same-CRS snap policy, point-tool scope,
and four-slice decomposition before public API implementation.
