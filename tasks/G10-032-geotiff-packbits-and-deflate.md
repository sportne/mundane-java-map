# G10-032 — GeoTIFF PackBits and Deflate

Status: Proposed
Depends on: G10-031
Gate: G10
Type: AFK

## Goal

Read and render the approved PackBits- and Deflate-compressed GeoTIFF raster segments under exact
bounded decompression semantics.

## Context

G10-031 completes the uncompressed raster matrix and segment/window planner. G10-003 selects only
None, PackBits, and Adobe Deflate and defines exact decoded lengths and failure precedence.

## Scope

Add private closed-switch PackBits and fresh-JDK-`Inflater` decoders to the existing raster read path;
enforce complete encoded consumption, exact planned output, compressed-segment accounting,
cancellation, source reuse after failed reads, and stable decompression diagnostics.

## Out of scope

LZW, old Deflate, JPEG, predictors, concatenated streams, codec plug-ins, elevation samples, or
compression-specific caching.

## Acceptance criteria

- Valid PackBits literal/replicate/no-op packets and one zlib Deflate stream decode to the same
  rendered RGBA result as uncompressed fixtures.
- Truncation, output overrun, unfinished/dictionary/trailing Deflate, and malformed PackBits packets
  fail with exact `GEOTIFF_DECODE_FAILED` context and no partial publication.
- Encoded/decoded segment and working-byte limits are checked prospectively, and cancellation leaves
  the open raster source reusable.

## Required tests

Hand-built valid codec fixtures, uncompressed parity, every closed malformed-stream outcome,
exact/one-over compressed and decoded limits, cancellation checkpoints, source-reuse, and render
integration tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geotiff:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use no native codec or public decoder extension point. A future compression method requires a new
profile decision and fixtures.
