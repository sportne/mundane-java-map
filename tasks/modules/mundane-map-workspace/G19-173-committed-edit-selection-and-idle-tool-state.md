# G19-173 — Committed edit, selection, and idle tool state

Status: Proposed
Depends on: G19-172
Gate: G19
Type: AFK

## Goal

Persist committed editable state, stable selection identities, active tool kind, and safe preferences while
excluding undo history and unfinished runtime interactions.

## Context

Useful project restore includes edit configuration and user mode, but serializing gestures, callbacks, workers,
or undo implementation internals is unsafe and not reproducible after resources change.

## Scope

- Persist committed editable content through explicit adapter resource/state codecs or guarded references,
  editable binding/limits/snap/wrap configuration and current committed revisions/fingerprints.
- Persist logical selection identities with missing/changed-feature reconciliation, active tool kind and stable
  measurement/edit/navigation preferences; every restored tool starts idle and uncaptured.
- Explicitly classify and discard undo/redo stacks, unfinished create/move/measure gestures, hover, pointer state,
  capture, transient preview/overlays, pending queries/work, workers, clocks and caches.
- Restore atomically only after edit/source revisions, identities, visibility/snap references, CRS/wrap and limits
  validate; report stable non-secret reconciliation outcomes.

## Out of scope

- Cross-session undo/redo, resuming a gesture, serializing tool/controller objects, arbitrary UI layout and
  private browser/AWT event state.

## Acceptance criteria

- Committed edit/selection/tool preferences round-trip deterministically and restored tools are ready but idle.
- Stale/missing revisions or identities follow explicit reconcile/reject policy before changing live state.
- No transient/runtime-only value or secret appears in XML/package bytes or extension values.

## Required tests

- Editable content/reference, revision, selection, tool kind/preferences, measurement/edit/snap/wrap restore matrix.
- In-progress gesture/hover/capture/preview/pending/cache/undo exclusion, stale identities/revisions, missing sources,
  limits, cancellation, close/replacement and no-runtime-state-leak tests.

## Validation

Run workspace/edit/tool/AWT/Vaadin checks, then qualityGate and `git diff --check`.

## Notes

None.
