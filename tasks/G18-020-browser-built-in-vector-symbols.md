# G18-020 — Browser built-in vector symbol completion

Status: Complete
Depends on: G18-011, G14-007
Gate: G18
Type: AFK

## Goal

Render the complete existing bounded built-in vector symbol profile through the browser component.

## Context

G18-011 supplies source records and solid role rendering. This slice completes the drawing vocabulary
before portrayal selection, catalog resources, labels, and export are added separately.

## Scope

- Complete vector marker paths, placement/anchors, map/screen units, rotation, strokes, opacity,
  composites, endpoint markers, fill outlines, and bounded hatches.
- Map unsupported custom/legacy vector symbols to exact stable diagnostics with no code or URL
  execution and no partial scene replacement.

## Out of scope

Portrayal selection, raster icons, labels, SVG capture, CSS/executable style expressions, browser
parsing of style files, arbitrary SVG, raster map layers, or interaction overlays.

## Acceptance criteria

- Every accepted built-in vector role renders in deterministic layer/feature/role/composite order
  with project-equivalent transforms, opacity, holes, endpoints, and hatch bounds.
- Unsupported symbols, recursion, path/coordinate limits, and client failures publish no partial
  scene and expose stable component/source diagnostics.

## Required tests

Complete built-in marker/line/fill/composite/endpoint/hatch matrix; transform and unit boundaries;
unsupported/recursive/over-limit symbols; tolerant Canvas structure fixtures.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The bundled client implements only the closed Canvas drawing primitives. Java remains authoritative
for symbol construction and never forwards source style syntax for browser evaluation.

Implemented with a bounded private operation stream that flattens role-homogeneous composites in
authoritative paint order. Marker placement stays unit-aware during local navigation; endpoint
bearings are derived from transformed geometry; hatch work is preflighted before scene replacement
and again before paint. Unsupported legacy, raster, and custom values remain closed failures.
