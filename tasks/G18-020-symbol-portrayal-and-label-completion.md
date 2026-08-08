# G18-020 — Browser symbol, portrayal, and label completion

Status: Proposed
Depends on: G18-011, G14-007
Gate: G18
Type: AFK

## Goal

Render the existing bounded vector symbol, portrayal, raster-icon, and point-label profiles through
the browser component without introducing a second style engine.

## Context

G18-011 supplies source records and solid role rendering. Project-native, OGC SE, MapLibre-style,
and MIL-STD-2525 workflows all converge on existing immutable symbols and portrayal resolvers, which
the browser adapter should consume directly.

## Scope

- Complete vector marker paths, placement/anchors, map/screen units, rotation, strokes, opacity,
  composites, endpoint markers, fill outlines, and bounded hatches.
- Resolve fixed, categorical, graduated, interpolated, filtered, and rule portrayals using current
  Java evaluators and exact required-attribute projection.
- Transfer explicit-catalog raster icons through one bounded same-origin immutable resource path.
- Add the approved browser text-measurement and bounded point-label placement handshake with stable
  generation semantics.
- Capture the accepted settled vector scene as an existing detached `VectorExportSnapshot` for the
  current SVG encoder, without AWT or a second SVG implementation.
- Map unsupported custom/legacy symbols and fonts to exact stable diagnostics with no code or URL
  execution.

## Out of scope

CSS/executable style expressions, browser parsing of SE/MapLibre files, remote sprites/glyphs/fonts,
arbitrary SVG, cross-platform glyph-pixel identity, raster map layers, or interaction overlays.

## Acceptance criteria

- Every accepted built-in vector role renders in deterministic layer/feature/role/composite order
  with project-equivalent transforms, opacity, holes, endpoints, and hatch bounds.
- Project-native, SE, and MapLibre portrayals select the same accepted symbols and omission outcomes
  for the same attributes, geometry, scale, and zoom context.
- Raster-icon resources are bounded, immutable, same-origin, unguessable where required, released on
  scene/component expiry, and never interpret source attributes as URLs.
- Labels use browser-measured text with bounded candidates/collisions and retain deterministic
  selection/order for equal measurements; stale measurements cannot affect a newer scene.
- Vector snapshot capture retains accepted viewport, paint order, symbols, and placed labels and
  rejects non-representable content according to the existing export profile.
- Unsupported symbols, recursion, metrics, catalogs, limits, and client failures publish no partial
  portrayal and expose stable component/source diagnostics.

## Required tests

Complete built-in marker/line/fill/composite/endpoint/hatch matrix; portrayal parity across native,
SE, MapLibre, and MIL-STD-2525 fixtures; icon-resource lifecycle/security; label measurement,
collision, stale-generation, and limit cases; tolerant Canvas structure fixtures.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The bundled client may implement Canvas drawing primitives, but Java remains authoritative for
portrayal selection and never forwards source style syntax for browser evaluation.
