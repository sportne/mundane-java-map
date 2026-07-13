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

- New `modules/mundane-map-io-image` with a static facade, immutable open/limit values, fixed header
  profiles, explicit unplaced/axis-aligned placement, one owned-channel source, and stable diagnostics
- Operation-scoped toolkit-neutral decoder context and immutable explicit registry in the API
- Explicit PNG/JPEG `ImageIO` decoder and packed-pixel conversion in `modules/mundane-map-awt`
- New `examples/raster-viewer`, module registration, publication, and architecture tests
- Narrow G0 architecture-policy clarification for the unavoidable JDK ImageIO registry initialization

## Out of scope

- World files, rotated affine placement, request interpolation controls, caches, GeoTIFF, remote
  imagery, color-profile policy, and external file-mutation guarantees
- Image writing, general codec support, application ImageIO plug-in discovery, or native execution
- JPEG entropy/EOI/trailing-data policy and concatenated-image rejection, which remain G6-004 work

## Acceptance criteria

- ASCII-case-insensitive `.png`/`.jpg`/`.jpeg` suffix and signature agree on the format. PNG accepts
  the standard <=8-bit grayscale/truecolor/indexed/alpha pairs; JPEG accepts 8-bit SOF0/SOF2 with
  one or three components. Other profiles fail explicitly.
- Sources expose bounded dimensions, stable source identity, explicit unplaced or caller-supplied
  axis-aligned bounds/CRS, and owned-channel close behavior through `RasterSource`; the cancellable
  opener publishes no partial source.
- Encoded bytes, width, height, pixel count, decoded bytes, channel count, and request dimensions are
  checked with prospective arithmetic against immutable image and G4 request limits before project-
  controlled allocation; opaque JDK decode has explicit before/after checks.
- Decoder selection is an immutable registry keyed only by `PNG`/`JPEG`. Duplicate registration has
  a stable configuration code/context; missing registration, unsupported profiles, corrupt/truncated
  headers, decoder mismatches, I/O, overflow, and limit violations have stable source diagnostics.
- `mundane-map-io-image` is JDK-only and AWT-free, including no `ImageIO`, `BufferedImage`,
  `ColorModel`, or Java2D types in its production code or public contracts.
- Built-in PNG/JPEG decoding and conversion to the toolkit-neutral packed-raster contract live only
  in `mundane-map-awt`; the operation-scoped decoder boundary exposes only a bounded borrowed JDK
  input stream plus API request, accounting, and cancellation values.
- The AWT factory explicitly registers one JDK ImageIO decoder for both formats. The adapter accepts
  only an eligible `java.desktop` reader, never explicitly scans/registers or selects application
  providers, calls `setInput(..., true, true)`, and disposes reader/image-stream state on every
  outcome. G0 records the narrow opaque JDK registry-initialization qualification honestly.
- G6-001 provides correct uncached full decode plus strict-window nearest output. Cancellation is
  checked around opaque decode and within conversion; source/read/decoder streams and buffers close
  or discard predictably, and a known failed/cancelled read leaves the open source reusable.
- Each read prospectively accounts for the full decoded pixel count and intermediate capacity
  `encodedBytes + 8 * fullImagePixels + 4 * outputPixels`, plus four published bytes per output
  pixel, before the opaque decode. The source charges once; the decoder only claims suballocations
  from that reservation, and the source validates the final claim/result/cancellation state.
- Operation input is positionally read in at most 4,096-byte chunks and has hard logical EOF at the
  captured encoded length, including for bulk read, skip, and available.
- The viewer loads one supplied image through `RasterSource`, labels its explicit normalized
  EPSG:3857 placement as non-georeferenced, and never uses a direct `ImageIcon`/`BufferedImage`
  shortcut.
- New public APIs have complete Javadocs and defensive collection/array handling.
- The new public module is included in the authoritative project inventory and publication dry run;
  examples and fixtures remain non-published.

## Required tests

- Exact PNG/JPEG header-profile, suffix/signature agreement, limit-minus/equal/plus-one and overflow,
  corrupt/truncated, unsupported-profile, exact retained header-snapshot mutation, changed length,
  close/reuse, and cancellation tests.
- Decoder-registry/context tests for declaration order, duplicates, missing entries, accounting,
  source-owned reservation/decoder claims, non-null/exact result shape, hard input fence, cleanup,
  and before/after opaque-stage cancellation.
- PNG/JPEG strict-window/nearest source and offscreen integration tests using tiny checksummed BSD
  fixtures; PNG colors/alpha are exact and JPEG/render colors use bounded tolerance.
- Large ordinary/compressed ancillary metadata fixtures prove the ignore-metadata reader setup stays
  within the fixed reservation; concatenated/trailing JPEG bytes are recorded as a non-claim.
- Architecture tests proving `mundane-map-io-image` is AWT-free and AWT types remain confined.
- Viewer argument/loading tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check :examples:raster-viewer:check --console=plain
./gradlew publicationDryRun --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use only JDK facilities at Level 1. Application decoder selection is explicit; the narrow AWT bridge
may use the JDK ImageIO reader registry only to retain one reader implemented by `java.desktop`, and
must ignore every classpath/module-path provider. This is the exact G0 opaque-JDK qualification, not
permission for a project `ServiceLoader`, provider scan/mutation, or fallback. Do not run native,
rendering-regression, corpus, or performance lanes in this task.
