# G8-001 — Level 1 Native Image and CI hardening

Status: Proposed
Depends on: G2-007, G3-004, G5-010, G6-005, G7-004
Gate: G8
Type: HITL

## Goal

Aggregate representative Level 1 success and diagnostic paths into a required Linux Java 21
GraalVM Native Image lane and make that evidence a release blocker.

## Context

Earlier native tasks prove symbols, shapefiles, and rasters individually. This gate validates their
coexistence with interaction/measurement and optimized rendering under the final Level 1 dependency
graph.

## Scope

- `modules/mundane-map-native-tests` aggregate smoke application and semantic assertions
- Root Native Image wiring and `.github/workflows/native-image.yml`
- Architecture checks and only narrow compatibility fixes required by aggregate reachability

## Out of scope

- Windows/macOS Native Image support claims without separate evidence
- Native performance acceleration, broad reflection metadata, and executing corpus/performance lanes
  inside the native binary
- Publishing release artifacts

## Acceptance criteria

- One bounded native executable exercises geometry/projection, symbols and explicit raster resource,
  tool/measurement calculation, shapefile query, PNG/JPEG affine request, and offscreen rendering.
- The smoke also verifies stable representative diagnostics for an invalid registration, malformed
  shapefile record, and rejected raster input without depending on exception message text.
- Assertions validate semantic outputs and lifecycle behavior, not only class reachability or exit
  status.
- Registries and resources are explicit; the aggregate path uses no reflection, classpath/resource
  scanning, dynamic proxies, Java serialization, JNI, `Unsafe`, or internal JDK APIs.
- The Linux CI job installs Java 21 GraalVM, runs only the separate `nativeSmoke` lane, retains useful
  failure reports, and blocks Level 1 release when it fails.
- Normal JVM CI remains separate and does not masquerade as Native Image evidence.
- Documentation claims Linux Native Image compatibility only. Windows/macOS are described as
  unverified until their own build-and-run evidence exists.
- **HITL checkpoint:** a maintainer reviews a successful clean Linux CI run, failure diagnostics, and
  the exact Native Image platform support wording.

## Required tests

- JVM tests for every aggregate smoke branch and required resource.
- Architecture tests over all Level 1 production modules for prohibited native-targeted mechanisms.
- A clean Java 21 GraalVM Native Image build-and-run in Linux CI.
- A workflow test/review ensuring the native lane remains distinct from normal/corpus/render/perf.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

A local native run is useful but does not replace the required Linux CI result. Do not add broad
reachability metadata to silence failures; fix explicit construction and resource wiring.
