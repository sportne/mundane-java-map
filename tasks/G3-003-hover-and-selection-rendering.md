# G3-003 — Hover and Selection Rendering

Status: Complete
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

- Toolkit-neutral immutable hover/selection events, listeners, and overlay symbol values in
  `mundane-map-api`.
- MapView-owned hover/selection transitions, full repaint invalidation, logical source-paint
  presence, and overlay painting in `mundane-map-awt`.
- Event-order and offscreen rendering tests plus public Javadocs.

## Out of scope

- Multi-selection, editing handles, tooltips, accessibility narration, thematic styles, and animated
  effects.
- Performance indexing or render caches.

## Acceptance criteria

- Pointer movement emits old/new hover transitions only when the complete topmost
  `(layerId, featureId)` key changes; repeated movement over the same key is silent.
- Pointer exit, layer replacement, disabled interaction, and view disposal clear hover exactly once.
- Selection listeners observe programmatic and click-driven old/new state in deterministic order,
  including clear and selected-content removal.
- Listener collections are mutation-safe during callbacks and follow the Swing event-dispatch-thread
  contract.
- Default hover and selection overlays are visually distinct, configurable through immutable
  toolkit-neutral symbols, and painted after source content without modifying source feature state.
- Every real interaction-state or overlay-symbol change requests one conservative full-component
  repaint, including old content after removal; no speculative retained-bounds protocol is added.
- `PRESENT` source paint permits an overlay; `EMPTY`, `UNKNOWN`, removed content, or content skipped
  by the source pass suppresses that overlay without erasing the stable identity state implicitly.

## Required tests

- MapView state tests for enter, unchanged move, cross-layer same-feature-ID transition, exit,
  removal, and clear sequences.
- Listener add/remove/duplicate/mutation tests and EDT assertions.
- Offscreen tests showing source pixels remain unchanged outside overlays and hover/selection draw in
  the documented order.
- Repaint-manager tests proving full-component invalidation for large strokes, offsets, rotated
  symbols, and removed old content.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Store stable layer/feature keys rather than mutable renderer objects. Overlay defaults should remain
small and deterministic; richer theming belongs to Level 2.

Completed on 2026-07-14. Immutable hover/selection transitions and role-complete overlay bundles now
feed one EDT-confined FIFO notification path. MapView tracks stable identities and a screen probe,
reports built-in logical source-paint presence, clears hover across interaction/lifecycle boundaries,
and paints source content followed by eligible hover and selection overlays without mutating source
features or participating in hit testing.
