# G2-003 — Symbol Placement and Composition

Status: Proposed
Depends on: G2-002
Gate: G2
Type: AFK

## Goal

Make vector symbols placeable and composable with deterministic anchoring, units, offset, rotation,
opacity, and draw order through the public map-rendering path.

## Context

G2-002 supplies normalized toolkit-neutral paths and an AWT marker renderer. G2-001 defines the
placement and migration semantics. `MapViewport` owns projected-world-to-screen conversion, so map
unit sizing and map-relative rotation must use viewport state without moving that concern into the
public symbol values.

## Scope

- Placement, size-unit, rotation-mode, and composite-symbol values in `mundane-map-api`.
- Placement calculations in `mundane-map-core` where toolkit-neutral math is reusable.
- AWT composition/render integration and focused offscreen tests.
- Public Javadocs and migration coverage for existing feature styles.

## Out of scope

- Line endpoint markers, hatch fills, raster icons, catalogs, hit testing, and render caches.
- Geographic ground-distance sizing or scale-dependent style expressions.

## Acceptance criteria

- Every anchor and finite offset approved by G2-001 has a tested, exact relationship to the marker's
  normalized bounds.
- Screen-pixel sizes remain visually constant across zoom; map-unit sizes follow viewport scale.
- Screen-relative rotation remains fixed to the display, while map-relative rotation follows map
  orientation according to the approved clockwise/zero-axis convention.
- Placement transformations are applied in a documented order so anchor, offset, scale, and rotation
  cannot vary by renderer.
- Composite symbols defensively copy children and draw in declared order; nested opacity multiplies
  predictably and fully transparent children do no painting.
- Invalid sizes, opacity, rotations, offsets, null children, and disallowed empty composites fail
  deterministically.
- Existing basic features still render through the G2-001 compatibility path.

## Required tests

- API tests for immutability, equality, validation, child ordering, and defensive copies.
- Core transform tests for each anchor, both size units, offsets, and both rotation modes.
- Offscreen AWT tests for composite draw order, nested opacity, zoom behavior, and rotation.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use immutable transform inputs and ordinary affine math. Do not store viewport or graphics state in
symbols, and restore all mutated `Graphics2D` state around each renderer invocation.

