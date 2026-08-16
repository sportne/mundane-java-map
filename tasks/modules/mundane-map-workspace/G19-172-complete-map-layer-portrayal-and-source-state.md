# G19-172 — Complete map, layer, portrayal, and source state

Status: Proposed
Depends on: G19-013, G19-014, G19-171
Gate: G19
Type: AFK

## Goal

Persist and restore every approved committed map composition, layer, source, portrayal, label, raster,
elevation, CRS, viewport, and wrap value through workspace v2.

## Context

V1 stores only a small feature/raster composition. A native project format is incomplete if ordinary
public map configuration silently disappears or requires application-private side data.

## Scope

- Model map/display CRS, viewport, wrap, ordered groups/layers, identities, visibility, opacity/blend/
  scale constraints, source binding types/options and deterministic derived/default state.
- Persist complete neutral portrayal/rules/expressions/symbols/labels/fonts/catalog references plus raster/
  elevation presentation, resampling, no-data/color, cache policy and tile-matrix selections.
- Define explicit adapter state codecs for every registered source/style format without hard dependencies or
  foreign public types; unknown media/options remain inert extension/resource data.
- Restore transactionally into toolkit-neutral values and prove AWT/Vaadin semantic parity and exact layer order.
- Bound layers/groups/rules/expressions/symbol trees/labels/resources/options/state bytes and restoration work.

## Out of scope

- Cache contents, runtime source sessions, private renderer protocols, implicit adapter installation,
  format data duplication outside explicit resources and visual pixel identity across engines.

## Acceptance criteria

- Every approved committed public map/layer/source/portrayal value round-trips or is documented as derived/forbidden.
- Missing adapters/resources produce stable preflight failures without partially changing a live map.
- Standalone and later packaged manifests restore equivalent toolkit-neutral composition in AWT and Vaadin.

## Required tests

- Exhaustive state-inventory read/write/reopen matrices across geometry, source, portrayal, labels, raster/
  elevation, CRS/viewport/wrap, ordering/visibility and adapter options.
- Missing/colliding adapters/catalogs/resources, deep/large style state, limits, cancellation, failure atomicity,
  AWT/Vaadin tolerant parity and representative format integration fixtures.

## Validation

Run workspace/state plus AWT/Vaadin integration checks, then qualityGate and `git diff --check`.

## Notes

None.
