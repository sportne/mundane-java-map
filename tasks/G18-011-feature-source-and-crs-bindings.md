# G18-011 — Feature-source and CRS browser bindings

Status: Proposed
Depends on: G18-010, G4-003
Gate: G18
Type: AFK

## Goal

Render bounded feature-source viewport queries through the Vaadin component with explicit CRS,
ownership, cancellation, diagnostics, multipart geometry, and stable logical identity.

## Context

The first slice displays detached snapshot layers only. The existing format modules become useful in
a browser once the adapter consumes their common `FeatureSource` boundary rather than adding
format-specific frontend code.

## Scope

- Add owned/borrowed feature bindings, visibility/order, required-attribute selection, and per-UI
  serialized query generations.
- Transform settled browser viewports into checked source queries using the explicit CRS registry.
- Support point, multipoint, line, multiline, polygon, and multipolygon records with holes and
  deterministic child-to-logical-ID mapping.
- Add cancellation/supersession, aggregate limits, atomic result publication, source reports,
  stable diffs only if measured transfer evidence qualifies them, and complete cleanup.

## Out of scope

Format-specific APIs, unknown-CRS guessing, arbitrary projections, source mutation/write-back,
portrayal expressions beyond the current solid slice, raster, or browser-side source loading.

## Acceptance criteria

- Any existing compatible `FeatureSource` can be attached without the Vaadin adapter depending on
  its format module.
- Settled pan/zoom issues bounded visible-envelope queries, requests only needed attributes, and
  never permits two live cursors on one source.
- A newer generation cancels or supersedes older work; stale completion cannot replace the accepted
  scene or source report.
- EPSG:4326 and EPSG:3857 source/map/display combinations agree with core projection and strict
  domain behavior; missing, unknown, and unsupported operations fail predictably.
- Multipart browser primitives retain one logical selection identity and deterministic source/layer
  order.
- Owned sources close exactly once on binding replacement/component close; borrowed sources remain
  caller-owned.

## Required tests

All geometry families and holes; viewport/query envelope parity; CRS success/failure boundaries;
attribute projection; generation races and cancellation; cursor serialization; diff/full-scene
equivalence if applicable; diagnostics and owned/borrowed cleanup.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Format viewers remain authoritative format evidence. This task proves one common browser binding,
not separate shapefile/GeoJSON/GPX/KML implementations.

