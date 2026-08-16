# G19-086 — SVG embedded and catalog raster images

Status: Proposed
Depends on: G19-044, G19-080, G19-081, G19-082
Gate: G19
Type: AFK

## Goal

Render static SVG `image` content from bounded embedded data or explicitly catalogued PNG/JPEG
resources with deterministic viewport, aspect, opacity, color, and ownership behavior.

## Context

The importer rejects images. The project already has an explicit decode-only static PNG/common JPEG
boundary that SVG should reuse rather than duplicate or broaden.

## Scope

- Add `image` geometry, href, preserveAspectRatio, overflow, image-rendering, opacity, transform,
  clipping/masking, and static compositing behavior.
- Resolve only approved embedded data or exact catalog entries; validate declared media type and use
  explicit registered image decoders with no sniffing or URI fetch.
- Reuse static/default PNG and common JPEG output/color/orientation rules without enabling APNG
  playback, SVG recursion outside the approved fragment policy, or another decoder.
- Define immutable decoded-resource sharing, cancellation, failure atomicity, cache ownership, and
  exact release across repeated `use`/pattern/mask instances.
- Bound encoded/decoded bytes, dimensions, pixels, copies, transforms, caches, and aggregate image
  work before decode/paint.

## Out of scope

- Animated images, remote/file resources, implicit MIME detection, video, and JPEG/PNG encoding.

## Acceptance criteria

- Embedded/catalog images match independent SVG renderers for every aspect/alignment/opacity mode.
- Wrong media, decoder failure, bombs, cancellation, and reference cycles publish no partial scene.
- Shared images decode/cache/release once under the documented ownership policy.

## Required tests

- PNG/JPEG embedded/catalog, data URI grammar, aspect/overflow/image-rendering/transform/opacity,
  repeated use/pattern/mask, wrong media/sniff/traversal, decode bomb, cancellation, cache/resource
  cleanup, aggregate limits, and renderer comparisons.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, image/SVG rendering lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
