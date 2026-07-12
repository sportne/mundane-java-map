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
- Public `Shapefiles` opener plus immutable open-options and complete approved format-limit values
- Finite same-stem lower/upper sidecar probing, ambiguity detection, and staged rejection without
  parsing sidecar contents
- New `examples/shapefile-viewer` using the normal API/core/AWT rendering stack
- Hand-built SHP fixtures for null, point, and multipoint records

## Out of scope

- SHX/DBF/CPG/PRJ content parsing, polylines, and polygons; path probing/staged rejection is in scope
- Corpus, fuzz, Native Image, writing, and editing support
- Adding an empty module for any other format

## Acceptance criteria

- The reader validates the 100-byte SHP header, file code, version, declared file length, shape type,
  and mixed big-/little-endian fields before returning records.
- Opening has an operation-local cancellation-token overload, cleans every partial resource, and
  returns only the existing `FeatureSource`; parser/channel types remain package-private.
- Polyline/polygon and recognized sidecar inputs whose owning slice has not landed fail with the
  profile's staged `SHAPEFILE_PROFILE_NOT_IMPLEMENTED`; they are not accepted and ignored. Z/M and
  MultiPatch **header** codes retain permanent `SHAPEFILE_SHAPE_TYPE_UNSUPPORTED`. A Z/M/MultiPatch
  record code beneath an accepted header is instead `SHAPEFILE_RECORD_TYPE_MISMATCH`.
- Null, point, and multipoint records are exposed according to the approved null-record policy with
  stable source record identity and packed primitive coordinates.
- Sequential cursor iteration is lazy, closeable, single-owner, and deterministic; EOF, early close,
  double close, and access after close follow the G4 lifecycle contract.
- Applicable G5-001 component, physical-record, record-byte, point, parser-allocation, G4 query, and
  cancellation limits are enforced before work/allocation. Other approved format ceilings are
  immutable in the shared value but gain parser behavior only in their owning slices.
- Truncation, length disagreement, unsupported record types, non-finite coordinates, and malformed
  point counts produce structured diagnostics with record number and byte offset.
- The format module is JDK-only and has no dependency on AWT; all public values are immutable and
  defensively copy collections.
- A clean sidecar-free source has absent schema and empty opening diagnostics. Missing SHX/DBF warnings
  begin only when G5-003/G5-006 own those policies; discovered sidecars still fail staged in this task.
- The viewer opens a supplied or bundled small fixture with an explicit recognized CRS override, fits
  its extent, and renders point and multipoint records through the real `FeatureSource` path.
- New public APIs have complete Javadocs and architecture tests cover the new module boundary.

## Required tests

- Unit tests with hand-built bytes for valid headers and null/point/multipoint records in mixed
  endian order.
- Header/point fixtures prove unused Z/M header bits are ignored, XY signed zero canonicalizes to
  positive zero, and non-finite XY is rejected.
- A supported Point header paired separately with Z/M and MultiPatch record codes proves record
  mismatch remains distinct from unsupported header type.
- Path-selection tests for lower/upper names, same-file aliases, ambiguous pairs, discovered staged
  sidecars, and partial-open cleanup.
- Negative tests for every header/record validation and configured allocation limit introduced here.
- Opening/cursor cancellation, short-read, source reuse, mutation-size, single-cursor, and
  primary/suppressed cleanup tests through the package-private JDK file-access seam.
- Cursor lifecycle and feature-source integration tests.
- Viewer argument/loading tests and an offscreen integration render of the fixture.
- Architecture tests proving the format module is JDK-only and AWT-free.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew publicationDryRun --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use explicit byte-order reads and checked arithmetic. Do not memory-map by default or allocate from
untrusted counts. SHX and DBF absence must not be simulated by placeholder abstractions. The viewer
requires a sidecar-free SHP and explicit EPSG:4326/EPSG:3857 argument until PRJ behavior lands.
