# G18-021 — Browser portrayal and catalog icon resources

Status: Complete
Depends on: G18-020, G14-007
Gate: G18
Type: AFK

## Goal

Resolve every current portrayal family to the accepted browser symbols and transport explicit-catalog
raster icons without introducing a second style engine or remote resource path.

## Scope

- Resolve fixed, categorical, graduated, interpolated, filtered, and rule portrayals with current
  Java evaluators and exact required-attribute projection.
- Prove native, OGC SE, MapLibre-style, and MIL-STD-2525 workflows converge on the same accepted
  symbols and omission outcomes.
- Transfer explicit-catalog raster icons through one bounded, immutable, same-origin resource path
  with component/session ownership and expiry.
- Complete portrayal, catalog, recursion, resource, and unsupported-symbol diagnostics.

## Out of scope

Browser parsing of source style syntax, executable expressions, arbitrary URLs/SVG, remote
sprites/glyphs/fonts, labels, vector snapshot capture, raster map layers, or interaction overlays.

## Acceptance criteria

- Each portrayal family selects the same symbols and omissions for identical attributes, geometry,
  scale, and zoom context across all accepted input adapters.
- Attribute projection is exact and no source style syntax reaches JavaScript.
- Raster-icon resources are bounded, immutable, same-origin, unguessable where required, and
  released on scene replacement, detach, session close, and component close.
- Source attributes can never become resource URLs or executable content; failures publish no
  partial portrayal and use stable diagnostics.

## Required tests

Complete portrayal parity fixtures; exact attribute projection; scale/zoom and omission boundaries;
MIL-STD-2525 resolution; icon authorization, limit, expiry, replacement, and cleanup cases.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Java remains the only portrayal evaluator. The resource path accepts adapter-owned catalog bytes,
never a caller- or feature-supplied URL.

Implemented with exact resolver-driven source projection and scale/zoom/geometry context, followed
by one closed scene protocol shared by native, SE, MapLibre-style, and MIL-STD-2525 portrayals.
Explicit-catalog raster icons use bounded immutable same-origin session resources, atomic browser
preload, stable diagnostics, and cleanup on replacement, detach, session destruction, and close.
