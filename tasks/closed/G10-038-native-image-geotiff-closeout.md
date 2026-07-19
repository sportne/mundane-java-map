# G10-038 — Native Image GeoTIFF closeout

Status: Complete
Depends on: G10-037
Gate: G10
Type: HITL

## Goal

Prove the complete bounded GeoTIFF reader and renderer on the required Linux Native Image path
without native codecs or discovery.

## Context

G10-037 completes JVM functionality, fixtures, viewers, and performance evidence. The module is
designed as JDK-only/native-targeted, but compatibility requires executable evidence.

## Scope

Extend the shared native executable and exact resource inventory with None, PackBits, and Deflate;
raster and elevation open/query/render success; cleanup; and one stable malformed outcome. Audit the
module for prohibited mechanisms and record the exact Linux toolchain/platform claim.

## Out of scope

Windows/macOS claims, native compression libraries, JNI, reflection configuration, resource scanning,
general TIFF support, or inclusion of the optional SQLite adapters.

## Acceptance criteria

- `nativeSmoke` exercises real raster and elevation fixtures across all three compressions plus one
  exact malformed diagnostic and resource cleanup.
- The executable uses direct code paths and explicit resources with no reflection, discovery,
  dynamic proxy, JNI, `Unsafe`, internal JDK API, or native codec.
- The recorded claim names only the tested Linux Native Image toolchain/platform and approved
  GeoTIFF profile.

## Required tests

Native raster/elevation success, query/render, compression coverage, malformed diagnostic, cleanup,
explicit-resource inventory, prohibited-mechanism architecture tests, and JVM parity tests.

## Validation

```bash
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoTIFF Native Image claim review**. The maintainer approved this checkpoint
in advance for the requested implementation sequence.

Implementation evidence (2026-07-19): the one shared native executable now directly runs one
package-private GeoTIFF scenario. Its exact four-resource addition reuses the independently authored,
SHA-256-pinned G10-037 corpus fixtures: RGB strips/None and grayscale tiles/Deflate through the raster
route, plus Int16 strips/PackBits and Float32 tiles/Deflate through the elevation route. JVM parity
and the executable both assert public open/read/query/render behavior, owned-source and workspace
cleanup, and the exact `GEOTIFF_HEADER_INVALID {field=version, reason=value}` malformed outcome.
Architecture checks retain literal resource registration, direct construction, and the prohibited-
mechanism audit; the module inventory now classifies GeoTIFF as native-targeted.

A supplemental no-fallback run used GraalVM CE Java 21.0.2+13.1 / Native Image 21.0.2 on Ubuntu
24.04.1 WSL2 Linux x86-64. It built the 48.26 MiB executable in 26.8 seconds and printed
`mundane-map native smoke: OK`; the complete Gradle lane took 40 seconds. This evidence supports only
the approved bounded GeoTIFF profile on that exact Linux toolchain/platform. Windows, macOS, Linux
AArch64, other distributions, and cross-platform Native Image compatibility remain unclaimed. The
project's pinned Ubuntu 24.04 x86-64 Oracle GraalVM Java 21 CI lane remains authoritative once this
candidate runs there.
