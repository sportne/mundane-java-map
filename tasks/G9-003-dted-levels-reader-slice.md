# G9-003 — DTED Levels 0, 1, and 2 reader slice

Status: Proposed
Depends on: G9-001
Gate: G9
Type: AFK

## Goal

Read valid DTED Level 0, Level 1, and Level 2 files into the shared elevation model through one working,
dependency-free vertical slice.

## Context

DTED stores elevation profiles with UHL, DSI, ACC, and data records. It is elevation data, not a generic
image. G9-001 defines the shared grid model; detailed hostile-input hardening follows in G9-004.

## Scope

Create `modules/mundane-map-io-dted` only as part of delivering the working reader, tests, Gradle
registration, and Javadocs. Parse the required headers and elevation records for valid Level 0, 1, and
2 inputs, expose geographic bounds/resolution/units/no-data through G9-001, and include small
deterministically hand-built fixtures for all three levels.

## Out of scope

GeoTIFF, compressed archives, DTED writing, network retrieval, terrain rendering, coordinate
interpolation, a real-world corpus, lazy/windowed access, and native code.

## Acceptance criteria

- A public read-only DTED source opens valid Level 0, 1, and 2 files and produces the same
  format-neutral elevation contract.
- Header coordinates, dimensions, intervals, profile ordering, signed-magnitude elevations, and void
  samples are interpreted correctly for valid fixtures.
- The reader owns and releases file resources explicitly and never relies on finalization or implicit
  resource discovery.
- Production code is JDK-only, AWT-free, reflection-free, and contains no DTED-specific type in
  `mundane-map-api`.
- The module is not added until its reader, tests, and a successful end-to-end sample query exist in
  the same change.

## Required tests

Unit tests for each header section and sample conversion; integration tests that open one hand-built
fixture per DTED level, inspect metadata, query known cells, recognize voids, and close the source;
architecture tests for module boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use positional or buffered JDK file I/O with explicit byte order and checked arithmetic. Do not add a
dependency merely to parse fixed-width DTED records; any later dependency proposal needs a measured,
substantial complexity reduction.

