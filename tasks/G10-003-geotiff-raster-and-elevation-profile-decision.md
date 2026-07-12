# G10-003 — GeoTIFF raster and elevation profile decision

Status: Proposed
Depends on: G8-004, G9-001
Gate: G10
Type: HITL

## Goal

Choose a bounded GeoTIFF profile and an implementation strategy that routes imagery to `RasterSource`
and elevation samples to the shared elevation model.

## Context

Level 1 provides bounded raster requests and affine georeferencing; G9-001 provides terrain data.
GeoTIFF is intentionally Level 2 and must remain separate from the dependency-free DTED reader even
when both produce elevation grids.

## Scope

Decide classic TIFF versus BigTIFF, byte order, strips/tiles, sample organizations/types, compression,
photometric interpretations, alpha/no-data behavior, required GeoKeys and transform tags, recognized
CRS handling, overview/window behavior, and parser/decode limits. Compare JDK-only, `ImageIO`-backed AWT
adapter, and isolated external adapter options. Define deterministic routing of imagery and elevation
samples and decompose approved work into vertical tasks.

## Out of scope

Production GeoTIFF code or module creation, arbitrary TIFF conformance, DTED changes, writing, cloud-
optimized remote range access, JNI/native codecs, and exposing TIFF/library types in public contracts.

## Acceptance criteria

- A maintainer approves a supported/rejected/deferred profile covering tags, compression, layout,
  sample types, CRS/georeferencing, no-data, and explicit resource limits.
- The decision defines how metadata determines raster versus elevation output without making DTED a
  generic image reader.
- Dependency options are compared for complexity, licensing, security, AWT confinement, Native Image,
  and maintenance; native code requires a separate evidence-backed decision.
- Follow-up tasks deliver tested read-to-render or read-to-query slices and add no empty format module.

## Required tests

No production tests. Identify minimal hand-built and redistributable corpus cases for every accepted
layout/compression/sample branch and map them to later malformed, rendering, performance, and native
tasks.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves the format matrix, decoder strategy, imagery/elevation routing,
and follow-up task split before implementation. GeoTIFF stays Level 2.
