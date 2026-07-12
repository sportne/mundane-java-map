# G3-003 — Hover and Selection Rendering

Status: Proposed
Depends on: G3-002
Gate: G3
Type: AFK

## Goal

Expose stable hover and selection changes and render both as non-destructive overlays without
mutating source features or their symbols.

## Context

G3-002 supplies ordered hit results and single-selection state. `MapView` currently paints only source
feature styles and has no interaction overlay pass. Hover must not emit repeated transitions while
the topmost hit is unchanged, and overlay painting must preserve the original immutable feature and
symbol values.

## Scope

- Immutable hover/selection event values and listener contracts in `mundane-map-api`.
- State transitions in `mundane-map-core` where toolkit-neutral.
- Hover tracking, repaint invalidation, and overlay painting in `mundane-map-awt`.
- Event-order and offscreen rendering tests plus public Javadocs.

## Out of scope

- Multi-selection, editing handles, tooltips, accessibility narration, thematic styles, and animated
  effects.
- Performance indexing or render caches.

## Acceptance criteria

- Pointer movement emits enter/leave or old/new hover transitions only when the topmost feature ID
  changes; repeated movement over the same feature is silent.
- Pointer exit, layer replacement, disabled interaction, and view disposal clear hover exactly once.
- Selection listeners observe programmatic and click-driven old/new state in deterministic order,
  including clear and selected-content removal.
- Listener collections are mutation-safe during callbacks and follow the Swing event-dispatch-thread
  contract.
- Default hover and selection overlays are visually distinct, configurable through immutable
  toolkit-neutral symbols, and painted after source content without modifying source feature state.
- Overlay bounds account for stroke/symbol expansion so state changes repaint all affected pixels.
- Hidden, removed, or fully non-painted features cannot leave a stale overlay.

## Required tests

- State-machine tests for enter, unchanged move, transition, exit, removal, and clear sequences.
- Listener add/remove/duplicate/mutation tests and EDT assertions.
- Offscreen tests showing source pixels remain unchanged outside overlays and hover/selection draw in
  the documented order.
- Repaint-region tests for large strokes, offsets, and rotated symbols.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Store stable layer/feature keys rather than mutable renderer objects. Overlay defaults should remain
small and deterministic; richer theming belongs to Level 2.

