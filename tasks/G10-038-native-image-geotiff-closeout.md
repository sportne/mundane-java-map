# G10-038 — Native Image GeoTIFF closeout

Status: Proposed
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

HITL checkpoint: **G10 GeoTIFF Native Image claim review**. A maintainer reviews the exact executable
evidence and platform wording before compatibility is claimed.
