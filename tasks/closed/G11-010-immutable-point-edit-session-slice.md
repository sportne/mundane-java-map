# G11-010 — Immutable point-edit session slice

Status: Complete
Depends on: G11-001
Gate: G11
Type: AFK

## Goal

Render a working application-owned point-edit session whose atomic create, replace, and delete
transactions publish immutable revisioned snapshots through an editable map binding.

## Context

G11-001 fixes the owner-thread session, closed command/result/problem model, atomic staging,
notification, and borrowed-binding semantics in the authoritative G11 design.

## Scope

Add the immutable edit values to `mundane-map-api`, the apply/event portion of
`FeatureEditSession` to `mundane-map-core`, an editable `MapLayerBinding` path in
`mundane-map-awt`, focused architecture tests, and a point-edit viewer that programmatically creates,
replaces, and deletes points through the real render stack.

## Out of scope

Undo/redo history, snapping, interactive edit tools, source mutation or write-back, line/polygon
editing, persistence, and placeholder methods for later slices.

## Acceptance criteria

- Valid multi-command transactions publish one complete snapshot at revision plus one; rejected or
  unchanged transactions mutate no session state and unchanged transactions emit no event.
- Feature IDs, command order, expected revision, limits, owner thread, and defensive-copy rules match
  the approved profile and use the stable `EDIT_*` problem shapes.
- Editable binding attachment, selection reconciliation, hover invalidation, repaint, EDT ownership,
  listener failure aggregation, and detach cleanup are deterministic.
- The viewer renders create/replace/delete operations without copying records into a source or
  introducing an editing module.

## Required tests

API invariant/copy tests; core atomicity, rejection, revision, limit, owner-thread, and listener tests;
AWT binding claim, CRS, selection/hover/repaint, detach, and viewer integration tests; architecture
tests for JDK-only API/core and AWT confinement.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check :examples:point-edit-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The session owns ordinary bounded state but no source, cursor, path, executor, or closeable resource.
Introduce only surfaces exercised by this slice; G11-011 through G11-013 add later behavior.
