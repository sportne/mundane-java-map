# G9-005 — Elevation position-query policy

Status: Proposed
Depends on: G9-001, G9-003
Gate: G9
Type: AFK

## Goal

Query elevation at a source-CRS position using explicit, deterministic nearest-sample and bilinear
policies shared by DTED and future elevation sources.

## Context

G9-001 deliberately limits the base model to cell access. G9-003 supplies DTED grids. Coordinate
queries must define boundary, tie, no-data, and interpolation behavior instead of hiding format-specific
choices in readers. G4-002 supplies recognized/retained-unknown CRS metadata, strict coordinate
domains, and reusable `CrsException` outcomes.

## Scope

Add API `ElevationQueryMode` and finite/unit-bearing `ElevationValue`, plus stateless core
`ElevationQueries.query(source, sourceCrs, position, mode)`. Define explicit source-CRS assertion,
domain/error precedence, exact metadata-coordinate binary search, lower-numeric-index nearest ties,
overflow-safe deterministic bilinear order, positive-weight no-data behavior, inclusive sample-center
bounds, lifecycle/ownership, API/core tests, and DTED integration tests using all three generated
levels.

## Out of scope

Bicubic interpolation, alternate no-data policy, typed absence reasons, terrain-surface modeling,
unit/datum conversion, implicit or explicit reprojection inside the query, slope/aspect, cancellation,
lazy/windowed I/O, caches, source default methods, strategy/SPIs, and format-specific query APIs or
DTED production changes.

## Acceptance criteria

- The exact three-type surface is implemented: two immutable JDK-only API values and one stateless
  core utility returning `Optional<ElevationValue>` without retaining or closing the source.
- Every position explicitly supplies its source `CrsDefinition`; recognized metadata must equal it,
  retained-unknown metadata accepts it only as the caller's explicit boundary, domain failures reuse
  exact G4 CRS outcomes, and no transformation, axis swap, wrapping, or CRS guess occurs.
- CRS-domain validation precedes inclusive grid bounds; a CRS-valid outside position and required
  no-data both return empty, while present results retain the source's exact declared unit.
- Binary searches reuse exact metadata post coordinates, handle near-`Integer.MAX_VALUE` indexes, and
  make immediately outside ULPs empty without tolerance, clamp, extrapolation, or half-post extension.
- Nearest resolves each exact tie to the lower numeric index (north for rows, west for columns) and
  reads only the selected post; successful bilinear queries read one, two, or four positive-weight
  posts in NW/NE/SW/SE order and stop with empty at the first no-data contributor.
- Bilinear interpolation runs west/east then north/south with the documented `Math.fma` convex forms,
  stays finite for extreme opposite-sign inputs, canonicalizes zero, and never renormalizes voids.
- Closed/source-contract failure precedence, external serialization, deliberate absence of
  cancellation/reports/limits, exactly-once metadata access, null metadata/sample and non-finite
  sample outcomes, public Javadocs, and architecture boundaries are directly tested.
- Level 0/1/2 integration tests query the format-neutral source; no interpolation logic or new public
  type is added to DTED production code.

## Required tests

API value/Javadoc tests; analytic non-square plane, binary-search, edge/corner/tie, contributor-order,
no-data, extreme arithmetic, large-index, lifecycle, ownership, CRS equality, retained-unknown
assertion, domain/precedence, and contract-violation core tests; Level 0/1/2 public-opener integration
queries; architecture tests
for API/core purity and absence of AWT/DTED/format-specific leakage.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-io-dted:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not silently choose interpolation based on zoom or source format. The DTED orientation evidence is
MIL-PRF-89020B sections 3.10.1, 3.10.4, and 3.10.5; G9-003 already owns the sole transpose. If another
no-data policy is later needed, design a separate typed policy rather than overloading interpolation
mode. Native, corpus, rendering, performance, and publication lanes do not run in this task.
