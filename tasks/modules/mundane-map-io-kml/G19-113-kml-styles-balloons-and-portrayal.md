# G19-113 — KML styles, balloons, and portrayal

Status: Proposed
Depends on: G19-002, G19-044, G19-089, G19-110
Gate: G19
Type: HITL

## Goal

Complete KML 2.3 shared/inline style resolution and map its standard 2D portrayal/balloon surface to neutral rendering.

## Context

Style, StyleMap, icons, labels, lines, polygons, balloons, lists, and style URLs are currently rejected.

## Scope

- Implement shared/inline Style, StyleMap normal/highlight pairs, style URL/reference inheritance, overrides,
  cycles, fallback, and document/KMZ/network resource identity.
- Implement IconStyle, LabelStyle, LineStyle, PolyStyle, BalloonStyle, ListStyle, color/colorMode, scale,
  heading, hotspot, width, fill/outline, list-item, and KML 2.3 additions.
- Compile standard styles to neutral symbols/labels/interactions with exact KML color/alpha/random behavior,
  units, highlight state, draw order, world wrap, and resource fallback.
- Preserve bounded balloon/description markup; core renders escaped plain text and exposes optional sanitized HTML adapter hooks.
- Bound style graphs, references, random seeds, icons, labels, markup, resources, primitives, and portrayal work.

## Out of scope

- Unsanitized HTML/browser execution, scripts, system fonts, ambient icons, and vendor style extensions.

## Acceptance criteria

- Supported styles resolve deterministically and render equivalently across AWT/Vaadin within declared tolerances.
- Style cycles/missing resources/invalid markup and all limits fail or fallback according to explicit KML rules.
- Core balloon handling cannot execute active content or fetch resources.

## Required tests

- Style/StyleMap/reference/normal-highlight/icon/label/line/poly/balloon/list/color/hotspot matrix.
- Cross-renderer goldens, cycles/fallback/random determinism, hostile markup/resources, and limit boundaries.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, rendering/corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves portrayal/balloon tolerances, visuals, and external viewer evidence.
