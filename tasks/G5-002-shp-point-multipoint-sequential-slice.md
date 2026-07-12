# G5-002 — SHP point/multipoint sequential slice

Status: Proposed
Depends on: G5-001
Gate: G5
Type: AFK

## Goal

Open a bounded SHP file as a `FeatureSource`, iterate null/point/multipoint records sequentially, and
render the supported geometry in a runnable shapefile viewer.

## Context

G5-001 fixes the supported profile and limits. G4 supplies toolkit-neutral source, cursor,
diagnostic, CRS, and multipart geometry contracts. This task is the first point at which the
`mundane-map-io-shapefile` module may exist.

## Scope

- New `modules/mundane-map-io-shapefile` production and test sources
- Module registration, dependency-boundary checks, and publication configuration
- New `examples/shapefile-viewer` using the normal API/core/AWT rendering stack
- Hand-built SHP fixtures for null, point, and multipoint records

## Out of scope

- SHX random access, DBF attributes, CPG/PRJ sidecars, polylines, and polygons
- Corpus, fuzz, Native Image, writing, and editing support
- Adding an empty module for any other format

## Acceptance criteria

- The reader validates the 100-byte SHP header, file code, version, declared file length, shape type,
  and mixed big-/little-endian fields before returning records.
- Null, point, and multipoint records are exposed according to the approved null-record policy with
  stable source record identity and packed primitive coordinates.
- Sequential cursor iteration is lazy, closeable, single-owner, and deterministic; EOF, early close,
  double close, and access after close follow the G4 lifecycle contract.
- Per-file and per-record limits from G5-001 are enforced before allocation or iteration.
- Truncation, length disagreement, unsupported record types, non-finite coordinates, and malformed
  point counts produce structured diagnostics with record number and byte offset.
- The format module is JDK-only and has no dependency on AWT; all public values are immutable and
  defensively copy collections.
- The viewer opens a supplied or bundled small fixture, fits its extent, and renders point and
  multipoint records through the real `FeatureSource` path.
- New public APIs have complete Javadocs and architecture tests cover the new module boundary.

## Required tests

- Unit tests with hand-built bytes for valid headers and null/point/multipoint records in mixed
  endian order.
- Negative tests for every header/record validation and configured allocation limit introduced here.
- Cursor lifecycle and feature-source integration tests.
- Viewer argument/loading tests and an offscreen integration render of the fixture.
- Architecture tests proving the format module is JDK-only and AWT-free.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use explicit byte-order reads and checked arithmetic. Do not memory-map by default or allocate from
untrusted counts. SHX and DBF absence must not be simulated by placeholder abstractions.
