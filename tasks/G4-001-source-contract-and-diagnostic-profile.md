# G4-001 — Source Contract and Diagnostic Profile

Status: Proposed
Depends on: G1-001
Gate: G4
Type: HITL

## Goal

Approve the minimal format-neutral feature/raster source, query, lifecycle, limit, and diagnostic
contracts that concrete Level 1 adapters can implement without exposing parser or toolkit types.

## Context

The current `Layer` returns a complete in-memory `List<Feature>`, `Feature` accepts a copied but
otherwise unconstrained `Map<String, Object>`, and no raster or structured diagnostic contract exists.
`DESIGN.md` requires format modules to depend only on API/core, explicit limits for untrusted input,
and immutable public values. This decision precedes both in-memory source rendering and every format
module.

## Scope

- A source-contract and diagnostic decision record in `DESIGN.md`.
- Compile-level public contract sketches against `modules/mundane-map-api`.
- Compatibility policy for `Layer`, `InMemoryLayer`, `Feature`, and existing examples.

## Out of scope

- Adding or changing production APIs.
- Concrete format modules, parser limits, raster decoding, caching, or asynchronous background I/O.
- CRS transformation policy beyond identifying metadata needed by G4-002.

## Acceptance criteria

- The approved feature profile defines source metadata, envelope/attribute query inputs, immutable
  feature values, bounded closeable cursors, stable ordering, cancellation, and source ownership.
- The approved raster profile defines metadata, pixel/grid dimensions, bounds/CRS linkage, window
  and output-size requests, immutable pixel-buffer ownership, cancellation, and close semantics.
- Cursor/request behavior after source close, partial iteration, early close, cancellation, and
  failure is explicit and implementable without finalizers or implicit background threads.
- Attribute values are limited to a documented immutable scalar/value set; byte/array/collection
  values have explicit defensive-copy rules and external adapter types are forbidden.
- Limits distinguish defaults from per-open/per-query overrides, validate before allocation, and
  cover record counts, dimensions, pixels, bytes, text, nesting, and allocations as applicable.
- Structured diagnostics have stable code, severity, source identity, optional record/field/offset
  location, and human message; ordering, collection bounds, exception mapping, and fatal versus
  recoverable behavior are defined.
- Public contracts contain no AWT, parser-library, shapefile, image-codec, or optional-adapter type.
- The maintainer completes the HITL checkpoint by approving cursor pull semantics, resource
  ownership, cancellation granularity, attribute types, limit configuration, and diagnostic shape.

## Required tests

- Compile-only sketches for an in-memory feature source, synthetic raster source, early cursor close,
  cancellation, one recoverable diagnostic, and one fatal diagnostic.
- Baseline API/core tests confirming the decision-only change does not alter current behavior.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer synchronous pull contracts for Level 1. Do not introduce reactive streams, plugin discovery,
generic parser abstractions, or an empty I/O module while recording this decision.

