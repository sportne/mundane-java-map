# G18-041 — Browser horizontal world wrap

Status: Proposed
Depends on: G18-040, G16-006
Gate: G18
Type: AFK

## Goal

Carry the existing explicit continuous-world profile through vector, raster, labels, interaction,
measurement, and editing in the Vaadin component.

## Context

G16 keeps strict canonical CRS/source coordinates and applies checked display repetition only when
both the view and binding opt in. The browser adapter must reuse that Java policy rather than relying
on implicit client wrapping.

## Scope

- Expose explicit horizontal-wrap configuration and binding-level `NONE`/`REPEAT_X` choices.
- Reuse core wrap planning, split/full-world queries, stable deduplication, seam geometry,
  copy/precision limits, raster compatibility, and canonical edit/measurement behavior.
- Transfer deterministic copy-scoped display references while retaining logical selection identity.
- Cover local/non-wrapped layer isolation and multi-world browser navigation.

## Out of scope

Automatic global-layer inference, vertical/polar wrap, globe/terrain, topology repair, projected
seam guessing, widening CRS domains, or client-owned canonicalization.

## Acceptance criteria

- Disabled and local-layer behavior is unchanged; repetition requires explicit compatible view and
  binding configuration.
- Points, lines, polygons, holes, endpoints, hatches, labels, rasters, and elevation display the
  same bounded visible copies and seam behavior as the approved G16 profile.
- Hit/hover/selection keep logical identity while interaction references the visual copy under the
  pointer; edits and measurements commit canonical coordinates.
- Browser pans and anchored zooms remain continuous across repeated east/west worlds and respect
  copy-index, visible-copy, precision, query, coordinate, allocation, and raster limits.
- Stale generations, incompatible rasters, hostile copy indices, and precision failures produce the
  approved stable outcomes without partial repeated content.

## Required tests

Disabled/local isolation; east/west seam and multi-world vector/raster rendering; split/full-world
query equivalence; copy-scoped hits and logical selection; measurement/edit canonicalization;
precision/copy/compatibility limits and cleanup.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The client draws checked copies supplied by Java. It does not decide whether a source is global or
apply modulo arithmetic to source geometry.

