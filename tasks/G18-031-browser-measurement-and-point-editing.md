# G18-031 — Browser measurement and point editing

Status: Proposed
Depends on: G18-030, G11-013
Gate: G18
Type: AFK

## Goal

Provide usable browser measurement and bounded point editing with snapping, undo/redo, canonical
world coordinates, and deterministic overlays.

## Context

G18-030 supplies the browser tool host. Measurement and point editing already have toolkit-neutral
state, distance, snap, transaction, and history foundations; only browser controllers and overlays
should be added.

## Scope

- Add a browser measurement controller using existing planar/geographic strategies, measurement
  state, semantic undo/cancel, and bounded overlay values.
- Add a browser point-edit controller for create/move/delete, current selection, same-CRS
  vertex/segment snapping, preview, commit, bounded history, undo, and redo.
- Reuse `FeatureEditSession`, edit/snap limits, canonical feature records, event causes, and stable
  diagnostics; integrate editable bindings and redraw generations transactionally.
- Add keyboard-accessible tool activation/commands and complete cancel/close cleanup.

## Out of scope

Line/polygon editing, topology repair, source-file write-back, concurrent/multi-user edit merging,
server database persistence, arbitrary geometry creation, or a new command framework.

## Acceptance criteria

- Planar and recognized-geographic measurement results match existing strategies for clicks,
  preview movement, completion, undo, cancel, and dateline cases.
- Point create/move/delete and undo/redo produce the same immutable edit snapshots and revision
  events as direct core sessions.
- Snapping uses only approved visible reference bindings, exact same-CRS behavior, stable ordering,
  bounded tolerance/candidates, and canonical coordinates.
- Preview overlays never mutate committed state; stale scene/edit revisions cannot commit.
- Tool replacement, focus loss, detach, source failure, and component close cancel gestures and
  release controller/session resources deterministically.

## Required tests

Measurement command/state/distance parity; point edit transactions/events/history; snap success,
ties, limits, and failures; stale revision rejection; wrapped canonical coordinates; keyboard and
pointer workflows; cancellation and lifecycle cleanup.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Browser controllers are presentation adapters over existing state. Do not move Flow or DOM concepts
into edit, snap, measurement, or tool API values.

