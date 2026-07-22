# G16-001 — Horizontal world-wrap profile decision

Status: Proposed
Depends on: G4-002, G7-004, G15-008
Gate: G16
Type: HITL

## Goal

Approve one explicit, bounded horizontal world-wrap profile that enables continuous longitude
navigation without weakening CRS domains or repeating local layers accidentally.

## Context

The G16 design separates strict projection from display repetition and proposes canonical
coordinates, checked world-copy indices, binding-level opt-in, wrapped query planning, seam-aware
geographic geometry, canonical interaction, and bounded raster repetition. Existing Web Mercator,
GeoJSON, source, editing, export, and G7 cache decisions constrain the profile.

## Scope

Review `design/G16-dateline-and-continuous-world-wrap.md`; freeze public type/module placement,
constructor and binding ergonomics, canonical half-open seam convention, copy-index precision bound,
default/hard visible-copy limits, query/order/deduplication rules, geographic shortest-path tie
behavior, polygon-hole failure policy, raster compatibility tolerance, diagnostics, export behavior,
and support wording.

## Out of scope

Production code; automatic CRS inference; arbitrary topology repair; projected seam guessing;
vertical/polar wrap; tile-format implementation; external dependencies; or a globe.

## Acceptance criteria

- The profile makes wrapping disabled by default and explicit at both view and layer boundaries.
- Projection domains, canonical source coordinates, logical feature identity, and format semantics
  remain unchanged.
- Exact copy, query, split, coordinate, allocation, precision, and raster-compatibility limits and
  stable diagnostic precedence are approved.
- Interaction, editing, measurement, labels, export, Native Image, and performance obligations are
  unambiguous.
- G16-002 through G16-007 remain meaningful one-to-five-day vertical slices.

## Required tests

No production tests. Review representative seam, multiple-world, over-zoom, local-layer,
dateline-line, polygon-hole, raster, interaction, and precision-bound cases against the design.

## Validation

```bash
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **horizontal world-wrap API, numeric limits, dateline geometry policy, and support
wording approval**. A maintainer must approve the exact profile before G16-002 begins.
