# G14-001 — MapLibre Style profile decision

Status: Proposed
Depends on: G13-006, G10-025
Gate: G14
Type: HITL

## Goal

Approve a bounded MapLibre Style Specification v8 root/source/layer/property/expression profile and
its isolated Jackson Core adapter boundary.

## Context

The G14 design proposes detached source IDs, circle/line/fill/basic symbol layers, explicit catalogs,
a closed typed expression subset, and no network/sprite/glyph behavior. G13 provides the ordered
standards-neutral portrayal bridge; G10-025 proves the locked Jackson/native pattern.

## Scope

Review `design/G14-maplibre-style.md`; freeze root/source/layer/property/filter/expression matrices,
zoom semantics, label/icon policy, unknown-member behavior, JSON limits/diagnostics, source and
catalog registries, dependency/license record, fixture provenance, and module/publication support
wording.

## Out of scope

Production code or module creation; Mapbox-specific extensions; remote sources/tiles; sprite or
glyph fetching; vector-tile source layers; 3D/terrain/heatmaps; arbitrary expressions; databind; or
complete MapLibre renderer compatibility.

## Acceptance criteria

- Every v8 root, source, supported layer, paint/layout property, filter, and expression family is
  classified as supported, retained, warned, or rejected.
- Zoom/CRS, missing/null/coercion, ordering, overlap, label, and resource semantics are exact.
- Jackson coordinates, graph, direct-construction/native policy, licenses, and notices are pinned.
- Limits, diagnostics, duplicate keys, hostile JSON, and fixture rights are explicit.
- G14-002 through G14-007 are actionable vertical slices with no empty adapter task.

## Required tests

No production tests. Review representative MapLibre examples, type/evaluation tables, source/catalog
binding sketches, dependency evidence, malformed/limit cases, and the complete task graph.

## Validation

```bash
./gradlew :modules:mundane-map-io-geojson-jackson:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G14 MapLibre v8 profile and Jackson boundary approval**. This approves a named
subset, not full MapLibre or Mapbox style compatibility.
