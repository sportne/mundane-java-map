# G14-002 — Literal vector-layer slice

Status: Proposed
Depends on: G14-001
Gate: G14
Type: AFK

## Goal

Create a working optional MapLibre style adapter that reads literal circle, line, and fill layers and
produces ordered ordinary symbol portrayals.

## Context

G14-001 fixes the JSON profile and dependency. G13 supplies ordered rule plans; G2 supplies target
symbols. This first slice proves JSON-to-map behavior without source acquisition or expressions.

## Scope

Create `modules/mundane-map-io-maplibre-style-jackson`; add direct constrained Jackson parsing,
immutable style/layer descriptors, literal approved circle/line/fill paint/layout mapping, layer
order/visibility, stable diagnostics, Javadocs, dependency locks/notices, architecture/publication
inventory, and one in-memory-source rendering example.

## Out of scope

Filters, data/zoom expressions, symbol labels/icons, source opening, URLs, sprite/glyphs, vector-tile
source layers, unsupported layer types, databind, or service discovery.

## Acceptance criteria

- A bounded version-8 style produces ordered ordinary circle/line/fill symbols and real rendering.
- Duplicate keys, invalid JSON/version/colors/enums/numbers, and unsupported rendering members fail
  with stable diagnostics and no partial plan.
- Parser factories are direct, constrained, non-discovered, and confined to the optional module.
- The new published module has the exact locked dependency graph and no AWT/Jackson type leakage.

## Required tests

Root/layer grammar, literal property/default matrices, order/visibility, hostile JSON basics, direct
factory controls, rendering, Javadocs, dependency/license/lock verification, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-maplibre-style-jackson:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Module creation lands with working parse-to-render behavior; it is not a schema/model scaffold.
