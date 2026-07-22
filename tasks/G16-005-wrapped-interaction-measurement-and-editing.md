# G16-005 — Wrapped interaction, measurement, and editing

Status: Proposed
Depends on: G16-004, G11-013
Gate: G16
Type: AFK

## Goal

Hit, hover, select, measure, snap, create, move, delete, undo, and redo logical features naturally
through any visible world copy while storing only canonical source/edit coordinates.

## Context

G16-004 completes wrapped vector presentation. G3 interaction uses stable logical feature IDs and
G11 point editing uses immutable commands, bounded history, and same-CRS snapping. The visual copy
must guide the active gesture without becoming persistent feature identity.

## Scope

Carry paint-scoped copy information through reverse-order hit candidates; canonicalize pointer and
snap coordinates before source/edit commands; keep hover on the pointed copy and persistent
selection visible on all copies; make measurement paths visually continuous while retaining the
existing antimeridian-normalized geographic distance; preserve pointer capture, navigation, edit
preview, undo/redo, command events, invalidation, and limits. Extend the point-edit and measurement
examples with explicit wrap modes.

## Out of scope

Line/polygon editing, topology repair, cross-CRS snapping, persisted copy indices, track selection,
or changes to distance formulas.

## Acceptance criteria

- The same copied feature produces the same public hit/selection identity from every visible world.
- Hover/preview stays at the active visual copy; logical selection and edits remain coherent after
  crossing the seam or navigating to another copy.
- Create/move/snap commands store canonical coordinates, and undo/redo exactly restores logical
  state without a presentation-copy artifact.
- Geographic measurement uses the existing shortest antimeridian distance and draws continuously
  beneath the pointer; planar non-wrapped behavior is unchanged.
- Navigation capture, zoom anchors, event ordering, repaint invalidation, limits, and close remain
  deterministic.

## Required tests

Reverse-paint hits by copy; hover transitions; selection overlays; seam create/move/delete;
snap-to-vertex/segment copies; undo/redo; measurement exact/near seam; pointer capture/cancel;
navigation anchors; mixed wrapped/local layers; examples, rendering regression, and native-safe core
math.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :examples:measurement-viewer:check :examples:point-edit-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Copy indices are transient internal gesture context. Existing public feature, selection, edit, and
workspace values must not grow a wrap-copy field.
