# G14-005 — Symbol icons and point labels

Status: Proposed
Depends on: G14-004
Gate: G14
Type: AFK

## Goal

Apply the approved MapLibre symbol-layer icon and point-label subset using caller catalogs and the
existing bounded G11 label-placement path.

## Context

G14-004 supplies typed expressions. G11-024 supplies fixed logical font metrics, point-label values,
global collision placement, and render/export/native evidence.

## Scope

Implement approved icon-name/size/rotation/opacity/anchor/offset/overlap properties, exact immutable
catalog resolution, simple text-field property/literal expressions, fixed label style/placement
mapping, placement priority/order, renderer preflight, and source-backed icon/label examples.

## Out of scope

Sprite sheets, glyph URLs, font stacks, rich/formatted text, curved/line labels, image expressions,
dynamic icon decoding, text shaping, arbitrary fonts, or changing G11 collision algorithms.

## Acceptance criteria

- Icon names resolve only through the supplied catalog and preflight role/renderer compatibility.
- Supported layout properties map exactly to existing marker placement and label values.
- Label text extraction, missing values, ordering, collision, and visibility follow the approved
  profile and agree with paint/export capture.
- Missing/wrong-role icons and unsupported font/text properties fail transactionally and stably.

## Required tests

Catalog resolution and preflight, icon placement/property expressions, label value/style/priority,
collision and layer ordering, source projection, paint/export agreement, tolerant rendering, and
architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-maplibre-style-jackson:check :modules:mundane-map-awt:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The fixed G11 `SansSerif` metric profile is explicit; MapLibre font stacks are not silently mapped to
it unless G14-001's supported matrix says so.
