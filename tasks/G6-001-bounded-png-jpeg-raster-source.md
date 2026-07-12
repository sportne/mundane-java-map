# G6-001 — Bounded PNG/JPEG raster source

Status: Proposed
Depends on: G4-002, G4-004
Gate: G6
Type: AFK

## Goal

Open bounded PNG and JPEG files as toolkit-neutral raster sources and display them in a runnable
raster viewer through an explicitly registered AWT decoder.

## Context

G4 defines raster metadata, requests, cancellation, lifecycle, diagnostics, and synthetic rendering.
This is the first task allowed to create `mundane-map-io-image`; it must deliver working decode and
render behavior rather than an empty format module.

## Scope

- New `modules/mundane-map-io-image` with encoded-file metadata, limits, and source lifecycle
- Explicit decoder contract/registration at the existing API boundary
- PNG/JPEG `ImageIO` decoder and packed-pixel conversion in `modules/mundane-map-awt`
- New `examples/raster-viewer`, module registration, publication, and architecture tests

## Out of scope

- World files, rotated affine placement, interpolation controls, caches, GeoTIFF, and remote imagery
- Image writing or arbitrary ImageIO plug-in discovery

## Acceptance criteria

- PNG and JPEG sources expose bounded dimensions, sample/color metadata needed by the renderer, a
  stable source ID, and explicit ownership/close behavior.
- Encoded bytes, width, height, pixel count, decoded bytes, channel count, and request dimensions are
  checked against immutable configurable limits before large allocation.
- Decoder selection is explicit and deterministic; unsupported media, duplicate registrations,
  corrupt headers, overflow, and limit violations produce structured diagnostics.
- `mundane-map-io-image` is JDK-only and AWT-free, including no `ImageIO`, `BufferedImage`,
  `ColorModel`, or Java2D types in its production code or public contracts.
- Built-in PNG/JPEG decoding and conversion to the toolkit-neutral packed-raster contract live only
  in `mundane-map-awt` and do not expose AWT types to `mundane-map-api`.
- Cancellation and close release streams promptly; requests after close fail predictably.
- The viewer loads a supplied or bundled image and renders it through `RasterSource`, not a direct
  `ImageIcon`/`BufferedImage` shortcut.
- New public APIs have complete Javadocs and defensive collection/array handling.

## Required tests

- Header/metadata, size-limit, corrupt/truncated, unsupported-media, close, and cancellation tests.
- Decoder-registry tests for explicit ordering, duplicates, and missing decoder diagnostics.
- PNG/JPEG source-to-offscreen-render integration tests using tiny legally generated fixtures.
- Architecture tests proving `mundane-map-io-image` is AWT-free and AWT types remain confined.
- Viewer argument/loading tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :examples:raster-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use only JDK facilities at Level 1. Do not depend on ImageIO service-provider scanning for
application decoder selection; construct and register the supported AWT decoder explicitly.
