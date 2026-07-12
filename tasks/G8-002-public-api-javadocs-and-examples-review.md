# G8-002 — Public API, Javadocs, and examples review

Status: Proposed
Depends on: G2-006, G3-004, G5-009, G6-004, G7-004
Gate: G8
Type: HITL

## Goal

Make the Level 1 public surface coherent and documented, and prove each primary capability through a
small runnable example and non-brittle rendering evidence.

## Context

All Level 1 capability slices are implemented. This is a release review and correction pass, not an
opportunity to redesign APIs or add formats.

## Scope

- Public Javadocs/package documentation in Level 1 published modules
- Basic viewer, symbol gallery, measurement, shapefile viewer, and raster viewer
- Example tests, doclint configuration where needed, and rendering-regression coverage
- `README.md`/`DESIGN.md` corrections required to describe observed Level 1 behavior accurately

## Out of scope

- New public capabilities, speculative convenience APIs, Level 2 formats, and a new documentation
  hierarchy
- Pixel-perfect cross-platform golden images or release publication

## Acceptance criteria

- Every exported public type/member has useful Javadocs covering immutability, units, coordinate
  space/CRS, limits, ownership/close/cancel behavior, nullability, diagnostics, and exceptions where
  relevant.
- Package docs explain API/core/AWT/format boundaries and explicit registry use without promising
  unsupported formats or automatic discovery.
- Public collections/arrays are defensively copied, public values are immutable, and external/AWT
  types do not leak across their established boundaries.
- The five examples are minimal consumers of published APIs, contain no duplicate production parser
  or renderer logic, start with deterministic bundled/generated data, and document any file argument.
- The symbol gallery covers built-ins/composites/catalog/resource behavior; measurement covers planar
  and recognized-geographic behavior; both format viewers expose structured load diagnostics.
- Rendering regression checks use geometry, bounds, topology, tolerant colors, and invariants rather
  than whole-image cross-platform equality.
- README, design, and examples distinguish verified current behavior from Level 2 plans.
- Javadocs pass with Java 21 doclint and all examples compile/test.
- **HITL checkpoint:** a maintainer manually runs and visually reviews all five examples and approves
  the public API/Javadocs for release compatibility.

## Required tests

- Javadoc/doclint for every published Level 1 module and link checks for local cross-references.
- Unit/integration checks for every example, including headless/offscreen paths.
- Architecture tests for public API dependencies, immutability conventions, and AWT confinement.
- Rendering-regression lane plus manual visual review on a supported desktop.

## Validation

Manual HITL validation (run and close each application before starting the next):

```bash
./gradlew :examples:basic-viewer:run
./gradlew :examples:symbol-gallery:run
./gradlew :examples:measurement-viewer:run
./gradlew :examples:shapefile-viewer:run
./gradlew :examples:raster-viewer:run
```

```bash
./gradlew :modules:mundane-map-api:javadoc :modules:mundane-map-core:javadoc :modules:mundane-map-awt:javadoc :modules:mundane-map-io-shapefile:javadoc :modules:mundane-map-io-image:javadoc :examples:basic-viewer:check :examples:symbol-gallery:check :examples:measurement-viewer:check :examples:shapefile-viewer:check :examples:raster-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Fix accidental API inconsistencies only when compatibility impact is understood and recorded. Avoid
adding convenience abstractions solely to make the examples shorter.
