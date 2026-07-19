# G11-041 — AWT capture and complete vector profile

Status: Proposed
Depends on: G11-023, G11-040
Gate: G11
Type: AFK

## Goal

Capture the currently visible vector map on the EDT and export every approved geometry and built-in
vector symbol behavior without retaining live AWT, source, or view state.

## Context

G11-023 supplies real portrayal/label placement and G11-040 supplies detached snapshots and basic SVG
encoding. G11-005 fixes authoritative capture, supported symbols, transforms, hatches, traversal, and
paint order.

## Scope

Add `MapView.captureVectorExportSnapshot` in `mundane-map-awt`; extend SVG export across all six
geometry families, composites, endpoint markers, arrowheads, fill outlines, and hatch fills; add the
allocation-free `HatchLayouts.candidateSegmentCount` helper in core; create
`examples/vector-export` displaying and exporting the same viewport.

## Out of scope

Raster/elevation layers, custom renderers, raster icons, interaction/edit/measurement overlays,
source/CRS metadata, asynchronous or background-thread capture, retained display lists, alternate
output targets, and silent effect degradation.

## Acceptance criteria

- EDT capture snapshots content/view/CRS/registries once, preflights unsupported layers before source
  I/O, performs one bounded query per source layer, closes cursors, and publishes all-or-nothing.
- Capture transforms authoritative geometry rather than G7 optimized paths, resolves portrayal once,
  uses the same fixed label metrics/placement, and retains no AWT/live/source value.
- SVG output preserves exact component/child/endpoint/fill/outline/hatch order, holes, opacity,
  rotations/units, clipping, and deterministic local clip IDs for all approved built-ins.
- The example exports the same displayed viewport and reports structured failure without opening a
  browser automatically.

## Required tests

AWT snapshot/source/editable capture, attribute/query/cursor/EDT/cancellation and no-retention tests;
all geometry and symbol order/transform/opacity tests; hatch count/cover equivalence and clip tests;
example smoke and architecture boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-io-svg:check :examples:vector-export:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Capture excludes every non-vector layer and transient overlay terminally. The writer never invokes an
AWT renderer or application callback.
