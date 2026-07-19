# G12-003 — First frame and entity rendering slice

Status: Proposed
Depends on: G12-002
Gate: G12
Type: AFK

## Goal

Resolve representative approved SIDCs into recognizable standard-identity frames and one entity
icon, then render and hit-test them through the real map stack.

## Context

G12-002 supplies canonical identifiers and support classification. G2 supplies the complete vector,
placement, composite, rendering, and hit-testing path required without a custom renderer.

## Scope

Add toolkit-neutral normalized frame/status/entity vector data, light/dark palettes, explicit resolver
factories, composite construction, a small example fixture, AWT integration tests through public
symbols, and tolerant render-regression scenarios.

## Out of scope

The complete approved entity inventory, sector modifiers, text amplifiers, custom AWT renderers,
font glyphs, portrayal from attributes, tactical graphics, or full conformance claims.

## Acceptance criteria

- The approved standard identities and statuses for the first entity produce distinct expected
  frames and ordered vector components under both palettes.
- Caller placement controls size, anchor, rotation, and opacity through ordinary G2 behavior.
- Paint and hit testing use the same composite footprint with no military-specific AWT branch.
- Unsupported entities fail with the approved diagnostic or approved recognizable-frame fallback.

## Required tests

Path normalization, component order, palette, placement, opacity, unsupported fallback, map paint,
hit testing, tolerant render regression, and architecture tests prohibiting AWT/module leakage.

## Validation

```bash
./gradlew :modules:mundane-map-symbology-milstd2525:check :modules:mundane-map-awt:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The slice must use built-in symbol renderer keys and explicit ordinary registries; it does not reserve
a military renderer key.
