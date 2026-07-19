# G10-039 — Encoded raster byte decoder

Status: Proposed
Depends on: G6-004, G10-006
Gate: G10
Type: AFK

## Goal

Decode one bounded in-memory PNG or JPEG into an independent toolkit-neutral RGBA buffer so HTTP and
approved container adapters can reuse G6 without temporary files or raster sources.

## Context

G10-006 independently authorizes this dependency-neutral helper. G6-004 provides the complete local
PNG/JPEG validation, explicit decoder registry, accounting, cancellation, and diagnostic behavior.

## Scope

Add `RasterImages.decode(byte[], SourceIdentity, EncodedRasterDecodeOptions,
EncodedRasterDecoderRegistry, CancellationToken)` and immutable options to
`mundane-map-io-image`; reuse complete PNG/JPEG validation and explicit decoding; add expected-format
and paired expected-dimension checks, defensive-copy/complete-decode accounting, cancellation, stable
closed diagnostics, public Javadocs, and architecture tests.

## Out of scope

Files, suffixes, world files, placement, caches, `RasterSource` lifecycle, Java2D/ImageIO types in the
I/O module, decoder discovery, HTTP, SQLite, GeoPackage, or MBTiles modules.

## Acceptance criteria

- Valid PNG/JPEG arrays decode synchronously at native size into independently owned RGBA buffers;
  absent/present expected format and exact paired dimensions follow the approved signature policy.
- The helper defensively copies input and prospectively charges exactly `2 * E + 16 * P` primitive
  payload bytes plus the existing fixed reference accounting before publication.
- Only the closed G10 design diagnostics can escape; dimension/format mismatch context is exact, raw
  bytes/messages do not leak, cancellation cleans up, and custom token/runtime failures retain their
  contract behavior.
- The image module remains AWT-free, dependency-neutral, explicitly registered, and reusable without
  implying approval of G10-004 or G11-004.

## Required tests

PNG/JPEG success, defensive ownership, expected-format/signature matrix, paired-dimension arguments,
dimension mismatch before decode allocation, exact/one-over encoded/pixel/working limits,
cancellation, decoder-contract failures, diagnostic shape/leak canaries, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-image:check :modules:mundane-map-awt:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

This card creates no module. Keep the helper synchronous and smaller than an unplaced temporary
source; G10-040 through G10-044 remain gated by their own unapproved HITL dependencies.
