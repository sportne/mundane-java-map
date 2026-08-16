# G19-228 — Static WebP decode and neutral raster integration

Status: Proposed
Depends on: G19-227
Gate: G19
Type: AFK

## Goal

Complete static lossy, lossless, and alpha WebP decoding and integrate it through the project's
explicit raster-decoder and source boundaries.

## Context

A dependency-wired first slice is not a useful common-interchange claim. Static WebP includes lossy
VP8, lossless VP8L, extended containers, alpha, profiles, and metadata combinations that must yield
the same neutral raster semantics everywhere they are consumed.

## Scope

- Decode bounded simple and extended static WebP using TwelveMonkeys' explicit reader construction,
  including lossy VP8, lossless VP8L, alpha, odd dimensions, common chroma, cropping/subsampling where
  safely supported, and accepted chunk ordering.
- Convert every accepted Java2D result deterministically into the project's declared packed color,
  alpha, precision, and premultiplication policy; never expose or retain `BufferedImage`.
- Apply bounded ICC/Exif/XMP handling consistent with the approved profile, including explicit
  orientation and color-precedence behavior and stable rejection of unsupported constructs.
- Register the decoder directly with image, HTTP tile, GeoPackage, MBTiles, MapLibre, Vaadin, and AWT
  consumers without changing their default dependency graph; validate declared media type and bytes.
- Preserve cancellation, cache identity, placement, source ownership, aggregate accounting, and
  atomic scene/publication behavior across direct, tiled, stored-database, and browser resource paths.

## Out of scope

- Animated frames, timing/loop metadata, encoding, arbitrary ImageIO operations, and implicit decoder
  selection from file extension, media type, plugin registry, or classpath.

## Acceptance criteria

- Independent static lossy/lossless/alpha WebP fixtures decode to the approved neutral raster and
  render equivalently in every registered consumer.
- Media-type declarations, magic bytes, dimensions, color/alpha metadata, orientation, limits, and
  cancellation are checked before any result/cache/scene becomes visible.
- Consumers without the optional adapter retain their current stable unsupported-media behavior and
  production graphs.

## Required tests

- Lossy/lossless/alpha/extended-container/color/profile/orientation/odd-size/subsample fixture matrix
  with libwebp or another independent decoder comparison.
- Direct/image-source/HTTP/GeoPackage/MBTiles/MapLibre/AWT/Vaadin registration, cache, cancellation,
  lifecycle, and tolerant render-parity tests.
- Wrong media/magic, animation, conflicting chunks, corrupt payload, bombs, aggregate concurrency,
  exceptional conversion, and no-partial-publication tests.

## Validation

Run adapter and affected consumer checks plus rendering/browser lanes where applicable, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

None.
