# G10-004 — SQLite container adapter profiles

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Approve separate, bounded read-only GeoPackage 1.4.0 and MBTiles 1.3 profiles plus one pinned,
Linux-only Xerial JNI deployment boundary before either format is implemented.

## Context

Both formats use SQLite but expose different map semantics. The Level 1 runtime is JDK-only; a JDBC or
native SQLite dependency must live in optional adapters and its types must not leak into map APIs.
The approved G0 optional-adapter rule permits a recorded third-party JAR to contain dormant discovery
or native mechanisms only when project code constructs the implementation directly and the support
claim is explicit. The normative profiles are OGC GeoPackage 1.4.0 and MBTiles 1.3.

## Scope

Record `mundane-map-io-geopackage-xerial` and `mundane-map-io-mbtiles-xerial`, the exact Xerial
classifiers/checksums/licenses/platform, direct-construction mechanism, read-only immutable-file and
SQLite session policy, lifecycle, cancellation, limits, diagnostics, and external-allocation
qualification. Lock the extension-free GeoPackage feature/PNG-JPEG tile profile and the raster-only
MBTiles metadata/TMS profile. Define the one synchronous defensive byte-array decode helper and exact
G6 image-codec composition edges, publication/consumer and JVM support evidence, and separate working
follow-up slices.

## Out of scope

Production modules, database writing/migrations, arbitrary SQL, encrypted or remote databases,
pooling, views/virtual tables/extensions, vector MBTiles, Native Image support, non-Linux platform
claims, project-owned/repacked native binaries, and any public API exposing JDBC or driver types.

## Acceptance criteria

- The **G10 SQLite container profile approval** checkpoint records the GeoPackage and MBTiles profile
  matrices, Xerial artifact/license/JNI decision, and Java 21 Linux x86-64/glibc JVM-only claim.
- The adapter design uses static format facades returning G4 contracts, direct
  `JDBC4Connection` construction, fixed SQL, immutable local files, bounded VM progress, and stable
  translated diagnostics without leaking external types.
- GeoPackage features, embedded tiles, CRS behavior, and binary geometry plus MBTiles metadata,
  TMS-to-XYZ conversion, sparse raster behavior, and PNG/JPEG decoding are implementation-ready.
- Embedded tiles reuse G6 through one bounded full-image byte-array decode helper; no database module
  calls ImageIO, exposes a decoder implementation, or constructs a temporary raster source per tile.
- Limits account for file/schema/row/text/BLOB/geometry/tile/cache/project-owned work and explicitly
  qualify opaque SQLite/JNI allocation and native extraction.
- Publication keeps Xerial out of MundaneJ artifacts and uses one exact checksummed build-only mirror
  so a fresh offline consumer resolves the two staged adapters and only their approved classifiers.
- Shared G10-039 first delivers the working bounded byte-array image helper; G10-040 through G10-044
  then deliver working behavior before adding either format module and depend on the G11-004 adapter
  approval; no generic SQLite module or Native Image claim is planned.

## Required tests

No production tests. Define later generated and independent fixtures for valid tables, all supported
geometry and tile paths, schema/profile mismatch, corrupt/hostile contents, exact/one-over limits,
VM cancellation, mutation/sidecars, query/render/cache behavior, resource closure, classified
dependency isolation, publication consumers, and the supported Linux JVM path.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 SQLite container profile approval**. Approval covers both independent profiles,
the `3.53.0.0` code-only plus Linux-native Xerial classifiers, licensing/native-extraction tradeoff,
strict read-only policy, two module boundaries, JVM-only platform wording, and later task graph.
