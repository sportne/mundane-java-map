# G6-005 — Native Image raster smoke

Status: Complete
Depends on: G2-007, G5-010, G6-004
Gate: G6
Type: HITL

## Goal

Decode, georeference, request, and render PNG and JPEG fixtures in the actual GraalVM Native Image
smoke application using explicit decoder/resource registration.

## Context

G6-004 completes the JVM raster path. Native Image verification must exercise both codecs and affine
rendering through the real format-neutral source boundary.

## Scope

- Five fixed BSD raster resources, the shared fixed fixture workspace, semantic JVM/native assertions,
  and exact resource-inventory tests in `modules/mundane-map-native-tests`
- Explicit AWT decoder wiring, Java 21 resource metadata, and focused architecture tests
- Narrow compatibility fixes in raster production modules

## Out of scope

- GeoTIFF, corpus/performance benchmarking, arbitrary ImageIO plug-ins, and reflection-based codec
  discovery
- A second executable/job, tracing-agent or broad reachability metadata, JVM fallback, and Windows,
  macOS, all-Linux, or cross-platform Native Image support claims

## Acceptance criteria

- The native executable explicitly registers the Level 1 AWT decoder and decodes one PNG and one JPEG
  in exact PNG/JPEG order without application classpath scanning, provider configuration, or implicit
  plug-in discovery.
- Fixed PNG/JPEG world files use nontrivial affine placement. Direct nearest/bilinear requests,
  opacity, fit, parallelogram/background behavior, and offscreen rendering traverse production code.
- Assertions pin dimensions, coefficients/envelopes/CRS, representative exact/tolerant samples,
  independent repeated cache-path results, request shapes, and bounded tolerant render output.
- Both sources use exact recognized EPSG:3857 metadata and identity map/display views; fixed grid/world
  probe tables and expected over-white RGB values make the JVM/native oracle deterministic.
- One 70-byte IDAT-CRC fixture terminates with exact `IMAGE_CONTAINER_INVALID`, component `image`,
  byte offset 54, and context `format=PNG`, `reason=chunkCrc` before source publication.
- Already-cancelled read/reuse, owned view/source close, immutable retained values, exact workspace
  cleanup, and primary/suppressed failures match the JVM path.
- One private workspace engine creates separate fixed G5 and G6 inventory instances in sequence, so a
  G6 resource failure cannot alter the approved G5 scenario. Direct reads stay off-EDT; tiny
  paint-triggered synchronous source reads execute on the EDT as required by G4.
- The one resource configuration declares exactly the G2 icon, six G5 files, and five G6 files as 12
  literal includes; no wildcard, directory, service, provider, or unlisted resource is present.
- Native-targeted code uses none of the prohibited reflection, scanning, proxy, serialization, JNI,
  `Unsafe`, or internal-JDK mechanisms.
- Missing Native Image tooling or JVM fallback cannot pass the lane.
- **HITL checkpoint — G6 native raster approval:** a maintainer reviews a successful no-fallback Java
  21 GraalVM `nativeSmoke` run on Linux and records the exact environment, sentinel, both codec/affine
  paths, malformed diagnostic, resource provenance/hashes, and cleanup evidence.

## Required tests

- JVM tests of the expanded smoke entrypoint and resource/decoder registration.
- Shared semantic PNG/JPEG/affine/cache/cancellation/diagnostic assertions plus negative invariant,
  resource length/hash/metadata, fixed-workspace, EDT, lifecycle, and cleanup tests.
- Architecture tests for exact dependencies/resources and all native-targeted prohibitions.
- The actual Native Image build-and-run lane on Java 21.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

ImageIO and Java2D remain confined to `mundane-map-awt`; `mundane-map-io-image` must stay AWT-free.
Reuse the single native executable and exact resource configuration. Do not run corpus,
render-regression, performance, publication, or consumer lanes. This task also performs the G6
holistic simplicity closeout; it adds no production capability beyond a narrowly necessary Native
Image compatibility fix.

Completed on 2026-07-15. The one existing native executable now runs the G2 symbol, G5 Shapefile,
and G6 raster scenarios sequentially with separate hash-verified workspaces. Its resource metadata
contains exactly 12 literal includes: the G2 icon, six G5 files, and five G6 files. JVM and
architecture checks pin the explicit `PNG`, `JPEG` decoder order, off-EDT direct reads, EDT paint,
world-file transforms, repeated independent cache-path results, cancellation/reuse, ownership,
cleanup, diagnostics, provenance, and negative semantic controls.

The successful Linux x86-64 lane used GraalVM CE Java 21.0.2+13.1 and `native-image 21.0.2` on
Ubuntu 24.04.1 LTS under Linux 5.15.167.4 WSL2. The no-fallback image was 47,402,296 bytes
(SHA-256 `cd2830a83d48a7ec0101bdea2a1cbaf2de6d23ae825e6490909850b03ab4c424`), built in 27.7
seconds, executed the production PNG/JPEG decode, affine, cache, cancellation, render, diagnostic,
and cleanup paths, and ended with `mundane-map native smoke: OK`; the root lane completed in 33
seconds. An initial ambient OpenJDK 21.0.11 invocation failed before compilation because no Native
Image launcher was available. The first two GraalVM executions exposed only missing JDK AWT/ImageIO
JNI reachability: the final metadata adds the five fixed `ByteComponentRaster` fields and the exact
ten OpenJDK 21 JPEG-reader callbacks plus three JPEG table fields. Architecture tests pin that narrow
set; application and production code still use no JNI, reflection, scanning, internal JDK API, or
provider discovery.

The BSD-3-Clause repository-authored fixtures are `png-affine-smoke.png` (93 bytes,
`8a000426…f0749`), `.pgw` (21, `22957d91…d6ff`), `jpeg-affine-smoke.jpg` (368,
`ec45fd82…cb9`), `.jgw` (22, `a161caf5…4561`), and the 70-byte malformed PNG
(`ab60a5ad…c7c6`). Both EPSG:3857 paths retained their exact affine envelopes, samples, fit,
background, and bounded paint behavior; malformed open ended at byte 54 with
`IMAGE_CONTAINER_INVALID`, component `image`, and `format=PNG, reason=chunkCrc`. Every source/view
and both workspaces closed and deleted their known paths. The maintainer's advance approval of all
G6-G9 HITL checkpoints accepts this evidence as the named **G6 native raster approval**. This is
Linux evidence only and makes no Windows, macOS, all-Linux, or cross-platform claim.
