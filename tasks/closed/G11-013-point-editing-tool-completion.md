# G11-013 — Point editing tool completion

Status: Complete
Depends on: G11-011, G11-012
Gate: G11
Type: HITL

## Goal

Complete the point-edit workflow with interactive create, move, delete, undo, redo, snapping,
selection, preview, and cancellation behavior through the real Swing map stack.

## Context

G11-011 supplies revisioned bounded history and G11-012 supplies immutable snap results. G11-001
defines the view-bound controller, target-binding lifecycle, captured-gesture, and result-listener
semantics.

## Scope

Implement `PointEditController` and its overlays in `mundane-map-awt`; finish the point-edit viewer,
public Javadocs, architecture coverage, tolerant rendering regressions, and one resource-free
representative point-edit scenario in the shared Linux Native Image executable.

## Out of scope

Line/polygon vertex editing, topology repair, multi-selection, source/file write-back, collaborative
editing, persistent history, platform key-binding policy, and Windows/macOS Native Image claims.

## Acceptance criteria

- Create, move, and delete obey current editable selection/topmost-hit rules and produce one atomic
  transaction or one stable rejection; successful create/move selection is reconciled from the
  committed snapshot.
- Pointer capture, wheel consumption, snapped/unsnapped preview, viewport-change rejection, stale
  revision, cancel/deactivate/focus-loss cleanup, and target-binding replacement preserve tool and
  session invariants.
- Editing overlays paint in the approved order and tolerant regression evidence avoids glyph or
  cross-platform pixel identity.
- The completed viewer passes maintainer review and the representative edit/session path succeeds in
  the required Linux Native Image lane.

## Required tests

Controller activation/ownership, selection reasons, topmost hit, gesture/capture/conflict/cancel,
viewport change, target removal, listener failure, overlay ordering, rendering regression, Javadocs,
architecture, viewer, JVM/native smoke, and manual workflow review.

## Validation

```bash
./gradlew :modules:mundane-map-awt:check :examples:point-edit-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 point-edit workflow and Linux Native Image review**. The maintainer reviews the
viewer behavior and the exact Linux evidence before point editing is described as complete or native
compatible.
