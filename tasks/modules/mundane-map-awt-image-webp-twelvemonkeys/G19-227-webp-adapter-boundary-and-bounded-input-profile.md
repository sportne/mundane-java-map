# G19-227 — WebP adapter boundary and bounded input profile

Status: Proposed
Depends on: G19-001, G19-044
Gate: G19
Type: HITL

## Goal

Create one working optional AWT adapter boundary for static WebP decoding with an exact dependency,
format profile, resource limits, and neutral public result contract.

## Context

WebP requires a RIFF container plus VP8, VP8L, alpha, color, and metadata behavior. Reimplementing
those codecs would add a large security-sensitive image stack without improving the project's map
model. TwelveMonkeys ImageIO provides a maintained pure-Java read path, but it depends on ImageIO and
Java2D and therefore cannot enter the toolkit-neutral `mundane-map-io-image` module.

## Scope

- Create `mundane-map-awt-image-webp-twelvemonkeys` only with a usable decoder, tests, package
  Javadocs, and `CAPABILITIES.md`; update architecture rules to permit Java2D only in this named AWT
  adapter in addition to `mundane-map-awt` and consumers.
- Pin `com.twelvemonkeys.imageio:imageio-webp` 3.14.0 and its exact transitive graph, checksums,
  licenses, offline repository, publication metadata, dependency verification, and upgrade policy.
- Instantiate the WebP reader explicitly. Do not use ImageIO service-provider discovery, global
  registry mutation, servlet listeners, reflection, classpath/resource scanning, or ambient codecs.
- Define the accepted static RIFF WebP profile, feature sniffing, animation rejection, color/alpha/
  metadata rules, neutral packed-raster output, cancellation, ownership, diagnostics, and limits.
- Snapshot input prospectively within exact encoded-byte limits before decode; preflight RIFF/chunk,
  canvas, frame, decoded-pixel, metadata/profile, owned-byte, concurrency, and work ceilings.

## Out of scope

- WebP encoding, animation playback/compositing, metadata editing, transcoding, custom VP8/VP8L
  codecs, JavaScript/native codecs, global ImageIO integration, and a Native Image support claim.

## Acceptance criteria

- A published optional adapter decodes at least one bounded static WebP through an explicit reader
  and returns only project-owned immutable raster values.
- Dependency and architecture tests prove TwelveMonkeys, ImageIO, and Java2D cannot enter API, core,
  toolkit-neutral format modules, Native tests, or consumers that did not request the adapter.
- Animated, malformed, unsupported, over-budget, cancelled, and closed inputs fail atomically with
  stable project diagnostics and no retained reader, stream, buffer, cache entry, or provider state.

## Required tests

- Dependency/license/checksum/offline/publication and forbidden-edge/API-leak tests.
- RIFF/chunk/dimension/animation/metadata/profile/truncation/overflow/bomb and cleanup boundary tests.
- Explicit-reader/no-service-scan/no-global-registry tests and a staged Java consumer smoke.

## Validation

Run the adapter and architecture checks, offline/publication consumer lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact TwelveMonkeys graph/license, static WebP profile,
limits, diagnostics, and the one-module Java2D boundary exception before implementation.
