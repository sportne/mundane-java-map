# G18-030 — Browser interaction and tool routing

Status: Proposed
Depends on: G18-022, G3-003
Gate: G18
Type: AFK

## Goal

Add map-coordinate events, deterministic hover/selection/hit behavior, keyboard navigation, and a
browser host for the existing toolkit-neutral map-tool router.

## Context

The rendered browser scene has stable logical IDs and complete symbol footprints. Interaction must
use the same ordering, identity, capture, cancellation, and cursor contracts as the desktop adapter
while keeping gesture feedback local.

## Scope

- Convert closed pointer, wheel, keyboard, focus, resize, cancellation, and semantic-command client
  events into validated toolkit-neutral events with strictly increasing sequences.
- Add browser hit plans/queries matching reverse paint order and symbol-aware tolerance.
- Add hover/selection state, events, overlay portrayal, coordinate observers, cursor intents, and
  accessible default pan/zoom controls.
- Host one `MapToolRouter` per component and reconcile client pointer capture with router capture,
  default navigation suppression, stale scenes, focus loss, disable, detach, and close.

## Out of scope

Measurement state, editing commands, multi-selection, lasso/box queries, touch gestures beyond the
approved pointer profile, collaborative presence, or unthrottled server hover traffic.

## Acceptance criteria

- Topmost hit, hover, and selection results agree with accepted paint order for every geometry,
  symbol, multipart child, label policy, and repeated display reference in scope.
- Client events cannot nominate absent/stale logical IDs or bypass Java hit/scene validation.
- Hover traffic is bounded and coalesced; click/selection order is deterministic and listener
  mutation retains existing semantics.
- Tool activation, replacement, pointer capture, release, user cancel, focus loss, disable,
  detach/reattach, and close follow `MapToolRouter` lifecycle and quarantine behavior.
- Mouse, touch/pointer, wheel, and keyboard navigation preserve finite viewport bounds and do not
  interfere when an active tool suppresses defaults.

## Required tests

Reverse-order symbol-aware hits; multipart identity; hover coalescing; selection overlays/listener
mutation; stale/forged client events; pointer capture/quarantine; keyboard/focus/disable/detach
lifecycle; event rate and coordinate-domain boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

High-frequency pointer feedback may remain client-local, but any state visible through the public
Java component must be reconciled to one accepted scene generation.
