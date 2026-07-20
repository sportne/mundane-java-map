# G11-011 — Bounded undo/redo slice

Status: Complete
Depends on: G11-010
Gate: G11
Type: AFK

## Goal

Make every committed point-edit transaction undoable and redoable through bounded delta history while
preserving monotonic revisions and exact record order.

## Context

G11-010 supplies atomic edits. G11-001 defines private before/after deltas, prospective logical-byte
accounting, whole-entry eviction, branch replacement, and non-reentrant event ordering.

## Scope

Extend `mundane-map-api`, `mundane-map-core`, and the point-edit viewer with
`FeatureEditHistoryLimits`, final session openers, undo/redo controls, bounded delta accounting,
deterministic eviction, rollback-safe replay, descriptions, and commit/undo/redo events.

## Out of scope

Whole-snapshot history, persistent command logs, cross-session transactions, arbitrary commands,
snapping, interactive pointer tools, and unbounded or system-property-configured limits.

## Acceptance criteria

- Mixed create/replace/delete deltas undo in reverse and redo forward while restoring exact positions
  and incrementing, never rewinding, the session revision.
- Every commit is initially undoable; oversized entries reject before commit and oldest whole undo
  entries are evicted prospectively to satisfy both history ceilings.
- A new successful commit clears redo; rejected/unchanged operations, failed replay, and revision
  exhaustion preserve content and both stacks exactly.
- Viewer controls expose undo/redo descriptions and stable conflict/empty-history outcomes.

## Required tests

Exact/one-over accounting, mixed-command replay, branch replacement, whole-entry eviction, revision
conflicts/exhaustion, event cause/order, rollback and notification failure tests, plus viewer control
integration.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :examples:point-edit-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

History stores bounded per-feature deltas, never complete snapshots. The G11-010 opener remains and
delegates to the approved default history limits once this behavior exists.
