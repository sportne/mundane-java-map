# G2-005 — Raster Icons, Catalogs, and Renderer Registration

Status: Proposed
Depends on: G2-003
Gate: G2
Type: AFK

## Goal

Render bounded toolkit-neutral raster icons, resolve immutable named symbols, and select all symbol
renderers through an explicit registry with stable failure diagnostics.

## Context

G2-002 and G2-003 provide vector symbols and placement. `DESIGN.md` forbids runtime discovery and
confines Java2D to `mundane-map-awt`. Raster icons therefore need an immutable primitive pixel form
at the public boundary, while Java2D conversion and renderer implementations stay in AWT.

## Scope

- Raster-icon and named-catalog values in `mundane-map-api`.
- Any JDK-only catalog validation in `mundane-map-core`.
- Explicit renderer registry, raster conversion, and built-in registrations in
  `mundane-map-awt`.
- Focused model, registry, and offscreen rendering tests plus public Javadocs.

## Out of scope

- File-format raster sources, world files, remote icons, arbitrary image decoding, and implicit
  classpath/resource scanning.
- Mutable global catalogs, service loaders, annotation scanning, or reflection-based dispatch.

## Acceptance criteria

- A raster icon owns positive width/height and defensively copied packed RGBA pixels, with explicit
  maximum dimensions and total-pixel limits checked before allocation.
- Raster icons use the G2-003 anchoring, size-unit, offset, rotation, interpolation, and opacity
  semantics and render through `MapView` without exposing `BufferedImage` publicly.
- A named catalog is immutable, iteration-order stable, rejects blank names and duplicate names, and
  reports duplicates with a stable code plus the offending name.
- Catalog lookup distinguishes missing name from malformed catalog input without falling back to
  resource or classpath discovery.
- The AWT registry maps supported toolkit-neutral symbol types to explicitly supplied renderers,
  rejects duplicate registrations, and reports an unregistered type deterministically.
- Built-in vector, composite, and raster renderers are installed explicitly by normal construction or
  a documented factory; applications can create an isolated registry without global mutable state.

## Required tests

- API tests for pixel copies, limits, catalog ordering, duplicates, missing names, and immutability.
- Registry tests for built-in installation, custom explicit registration, duplicates, isolation, and
  unregistered symbols.
- Offscreen AWT tests for raster placement, alpha, rotation, both size units, and interpolation.
- Architecture tests proving no discovery API or AWT type leaks into API/core.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test :modules:mundane-map-architecture-tests:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use packed primitive pixel storage rather than one object per pixel. Encoded PNG/JPEG loading belongs
to the later raster-source slice; the native resource smoke may supply an explicitly decoded bounded
fixture through a narrow loader owned by AWT.

