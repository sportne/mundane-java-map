# G19-002 — Advanced portrayal and structured attributes

Status: Completed
Depends on: G19-001
Gate: G19
Type: HITL

## Goal

Provide a standards-neutral, immutable portrayal and attribute model capable of representing the
remaining common SE, MapLibre, KML, and raster styling concepts.

## Context

The present flat scalar attributes and small symbol set cannot carry nested properties, geometry
expressions, advanced strokes/fills/text, graphic paints, or band/color-map semantics.

## Scope

- Specify bounded structured attribute values while preserving the simple scalar API.
- Add cap, join, dash, offset, graphic fill/stroke, text-placement, halo, and raster-band concepts.
- Define expression inputs and evaluation results without importing a format-specific AST.
- Specify composite ordering, units, opacity, fallback, validation, and stable diagnostics.
- Update public Javadocs and renderer/adapter extension contracts.

## Out of scope

- Embedding JavaScript, reflection-based property access, or format-specific unknown nodes.
- Claiming visual identity across rendering engines without tolerance rules.

## Acceptance criteria

- The neutral model can losslessly represent the G19 SE and MapLibre supported matrices.
- Values remain immutable, explicitly bounded, deterministic, and safe for native compilation.
- Simple existing symbols and flat attributes retain their behavior and ergonomic factories.
- Every renderer can explicitly accept, approximate under a named policy, or reject each construct.

## Required tests

- Exhaustive value/symbol validation and ordering/unit tests.
- Expression and structured-value depth/size hostile-input tests.
- Cross-renderer golden fixtures for every new portrayal primitive.

## Validation

Run `./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

Human review freezes the neutral model before format-specific mappings become compatibility promises.

Completed with bounded structured attributes and expressions; advanced cap, join, dash, offset,
graphic paint, text placement, halo, raster band, and color-map values; and an explicit renderer
accept/approximate/reject capability contract. Existing scalar attributes and simple symbols remain
unchanged.
