# G18-022 — Browser labels and vector snapshot capture

Status: Proposed
Depends on: G18-021, G11-043
Gate: G18
Type: AFK

## Goal

Add bounded browser-measured point labels and capture the accepted settled browser vector scene for
the existing detached SVG export path.

## Scope

- Implement the approved text-measurement and point-label placement handshake with closed fonts,
  stable generations, candidate limits, deterministic collision order, and cleanup.
- Capture the accepted settled scene as `VectorExportSnapshot`, retaining viewport, paint order,
  symbols, catalog icons where representable, and placed labels without AWT or a second SVG writer.
- Complete font, metrics, stale-measurement, representability, limit, and client-failure diagnostics.

## Out of scope

Remote fonts/glyphs, arbitrary CSS, cross-platform glyph-pixel identity, a new SVG encoder, raster
map layers, browser-side export, or interaction overlays.

## Acceptance criteria

- Bounded label candidates use browser measurements and deterministic placement/order for equal
  measurements; stale or malformed measurements cannot affect a newer scene.
- Font selection is closed and source values cannot introduce CSS, URLs, or executable content.
- Vector capture is immutable, agrees with the accepted viewport and paint order, and rejects
  non-representable content using existing export diagnostics without partial output.
- Replacement, detach, disable, and close clear pending measurements and captured adapter state.

## Required tests

Measurement, collision, tie, generation, font, and candidate limit cases; tolerant Canvas label
fixtures; vector snapshot parity, representability, failure atomicity, and lifecycle cleanup.

## Validation

```bash
./gradlew :modules:mundane-map-vaadin:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The SVG module remains the only encoder. G18 claims structural and tolerant placement agreement,
not identical glyph pixels across operating systems.
