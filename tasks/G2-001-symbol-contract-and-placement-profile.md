# G2-001 — Symbol Contract and Placement Profile

Status: Proposed
Depends on: G1-001
Gate: G2
Type: HITL

## Goal

Approve a small toolkit-neutral symbol model and migration policy that can represent Level 1 marker,
line, fill, and composite graphics without leaking Java2D types or leaving placement semantics
ambiguous.

## Context

`Feature` currently owns one `FeatureStyle`, whose point diameter and shared stroke/fill fields are
rendered directly inside `MapView`. `DESIGN.md` requires immutable public values, explicit renderer
registration, and AWT confinement. This decision must precede vector paths and custom renderers so
later tasks do not encode incompatible assumptions about size, rotation, or style migration.

## Scope

- The symbol and placement decision record in `DESIGN.md`.
- Proposed public contract sketches against `modules/mundane-map-api` and rendering responsibilities
  in `modules/mundane-map-awt`.
- A source-compatibility and deprecation plan for `FeatureStyle` and existing examples.

## Out of scope

- Adding or changing production APIs.
- Implementing vector paths, raster icons, catalogs, renderers, or SVG import.
- General expression-based or thematic styling.

## Acceptance criteria

- The approved profile assigns immutable contracts for marker, line, fill, and ordered composite
  symbols, with no AWT types in public toolkit-neutral values.
- Placement defines the supported anchor positions, finite x/y offsets, opacity composition, positive
  sizes, and the exact meaning of screen-pixel versus map-unit sizes.
- Rotation is defined in degrees with a documented direction and zero axis, and distinguishes
  screen-relative from map-relative rotation under viewport changes.
- Composite ordering, inherited opacity, placement composition, and behavior for empty composites
  are unambiguous.
- Line endpoint markers and fill-pattern extension points can be added without a parallel style
  hierarchy or runtime type discovery.
- The decision states whether `FeatureStyle` is adapted, deprecated, or replaced, and how existing
  source and rendered output migrate during the pre-1.0 period.
- Public equality, defensive-copy, invalid-input, unknown-symbol, and diagnostic expectations are
  recorded for subsequent tests.
- The maintainer completes the HITL checkpoint by approving the contract boundaries, units,
  rotation rules, and `FeatureStyle` migration before G2-002 begins.

## Required tests

- Compile-only contract sketches or examples covering one marker, line, fill, and composite symbol.
- Baseline API/AWT tests proving the decision-only change does not alter current behavior.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-awt:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep Level 1 deliberately small. Do not design arbitrary SVG, style expressions, mutable symbol
builders, reflection-based visitors, or extension types whose only purpose is a speculative format.

