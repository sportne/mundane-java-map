# G6-005 — Native Image raster smoke

Status: Proposed
Depends on: G6-004
Gate: G6
Type: HITL

## Goal

Decode, georeference, request, and render PNG and JPEG fixtures in the actual GraalVM Native Image
smoke application using explicit decoder/resource registration.

## Context

G6-004 completes the JVM raster path. Native Image verification must exercise both codecs and affine
rendering through the real format-neutral source boundary.

## Scope

- `modules/mundane-map-native-tests` raster fixtures and semantic smoke assertions
- Explicit AWT decoder and resource wiring for Native Image
- Narrow compatibility fixes in raster production modules

## Out of scope

- GeoTIFF, corpus/performance benchmarking, arbitrary ImageIO plug-ins, and reflection-based codec
  discovery
- Cross-platform Native Image support claims

## Acceptance criteria

- The native executable explicitly registers the Level 1 AWT decoder and decodes one PNG and one JPEG
  without application classpath scanning or implicit plug-in discovery.
- At least one fixture uses a world file with nontrivial affine placement; window request,
  interpolation, opacity, and offscreen rendering traverse production code.
- Assertions cover dimensions, bounds/transform, representative decoded samples with JPEG tolerance,
  request result shape, and nonempty bounded render output.
- Corrupt or oversized input produces the same stable diagnostic family as the JVM path.
- All streams, sources, requests, and cached entries obey close/cancel behavior in the native
  executable.
- Required fixtures and runtime resources are explicitly declared and minimally scoped.
- Native-targeted code uses none of the prohibited reflection, scanning, proxy, serialization, JNI,
  `Unsafe`, or internal-JDK mechanisms.
- **HITL checkpoint:** a maintainer reviews a successful Java 21 GraalVM `nativeSmoke` run (local or
  CI), including evidence that both PNG and JPEG paths executed.

## Required tests

- JVM tests of the expanded smoke entrypoint and resource/decoder registration.
- Semantic PNG/JPEG/affine assertions shared where practical between JVM and native paths.
- The actual Native Image build-and-run lane on Java 21.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

ImageIO and Java2D remain confined to `mundane-map-awt`; `mundane-map-io-image` must stay AWT-free.
Use tiny generated fixtures with explicit licensing and stable semantic checks.
