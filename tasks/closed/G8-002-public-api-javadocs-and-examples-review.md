# G8-002 — Public API, Javadocs, and examples review

Status: Complete
Depends on: G2-006, G3-004, G5-009, G6-004, G7-004
Gate: G8
Type: HITL

## Goal

Make the Level 1 public surface coherent and documented, and prove each primary capability through a
small runnable example and non-brittle rendering evidence.

## Context

All Level 1 capability slices are implemented. This is a release review and correction pass, not an
opportunity to redesign APIs or add formats. G0's project inventory and generated Javadoc indexes are
the exhaustive review inputs; no second API-manifest system is introduced.

## Scope

- Public Javadocs/package documentation in API, core, AWT, shapefile, and image I/O modules
- Basic viewer, symbol gallery, measurement viewer, shapefile viewer, and raster viewer
- `build-logic`/Checkstyle doclint and missing-Javadoc enforcement, architecture checks, example
  tests, and rendering-regression coverage
- `README.md`, `DESIGN.md`, and task/roadmap corrections required to describe observed Level 1
  behavior accurately and record compatibility dispositions

## Out of scope

- New public capabilities, speculative convenience APIs, Level 2 formats, and a new documentation
  hierarchy
- Pixel-perfect cross-platform golden images or release publication
- JPMS descriptors, nullability dependencies, aggregate Javadoc/API-manifest tooling, or compatibility
  claims against unreleased snapshots
- Source/binary-incompatible cleanup without an individually recorded maintainer decision

## Acceptance criteria

- Every project-declared public/protected type/member in the five published modules has useful
  Javadocs covering immutability, units, coordinate space/CRS, limits, ownership/close/cancel
  behavior, nullability, diagnostics, and exceptions where
  relevant. Record components use type-level `@param`; unchanged overrides may inherit docs.
- Package docs explain API/core/AWT/format boundaries and explicit registry use without promising
  unsupported formats or automatic discovery.
- Java 21 Javadocs run offline with all doclint groups, warning-as-error, UTF-8, and no timestamp. A
  separate `checkstylePublicApi` task applies exact missing type/method/field/package documentation
  checks only to each published module's main sources; ordinary shared Checkstyle still covers every
  existing main/test/support source.
- Public collections/arrays are defensively copied, public values are immutable, and external/AWT
  types do not leak across their established boundaries.
- `Layer`/`InMemoryLayer` remain supported small-snapshot APIs. Deprecated `FeatureStyle` remains
  supported for the first Level 1 `0.x` release and is documented for removal before `1.0`; approved
  G2/G4 migrations are not reopened and no unreleased binary-compatibility baseline is invented.
- The five examples are minimal consumers of published APIs and contain no duplicate production
  parser or renderer logic. Basic/gallery/measurement are deterministic no-argument examples;
  shapefile/raster remain real file consumers with exact documented arguments and review fixtures.
- The symbol gallery covers built-ins/composites/catalog/resource behavior; measurement covers planar
  and recognized-geographic behavior; both format viewers expose structured load diagnostics.
- Format viewers use fixed identities `shapefile-viewer` and `raster-viewer`; file arguments are never
  copied into source identities or diagnostic presentation, and sensitive-path sentinel tests prove
  they do not appear in the UI model.
- Rendering regression checks use geometry, bounds, topology, tolerant colors, and invariants rather
  than whole-image cross-platform equality.
- README, design, and examples distinguish verified current behavior from Level 2 plans.
- README records the exact five published modules, five examples, Java 21 baseline, registry/lifecycle
  setup, bounded format/CRS profiles, independent lanes, and pre-1.0 compatibility without saying APIs
  may change freely. Until release reconciliation it says the Ubuntu 24.04 Linux x86_64 GraalVM Java
  21 lane is the target but compatibility is unverified pending G8-001 approval; G8-004 alone replaces
  that provisional status from recorded evidence.
- Javadocs pass with Java 21 doclint; all examples compile/test headlessly; any missing capability or
  architectural contradiction returns to its owning gate rather than expanding this review.
- **HITL checkpoint — G8 Level 1 API, Javadoc, and example approval:** a maintainer records the
  reviewed API/compatibility dispositions and visually runs all five examples on a supported desktop.

## Required tests

- Javadoc/doclint/Checkstyle for every published Level 1 declaration and local cross-reference.
- Unit/integration checks for every example, including headless/offscreen paths.
- Architecture tests for public API dependencies, immutability conventions, and AWT confinement.
- Rendering-regression lane plus manual visual review on a supported desktop.

## Validation

Manual HITL validation (run and close each application before starting the next):

```bash
./gradlew :examples:basic-viewer:run
./gradlew :examples:symbol-gallery:run
./gradlew :examples:measurement-viewer:run
./gradlew :examples:shapefile-viewer:run --args='modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-polygon-hole-windows1252-3857/generated-polygon-hole-windows1252-3857.shp'
./gradlew :examples:raster-viewer:run --args='examples/raster-viewer/build/review-fixtures/rotated-sheared.png --world-file EPSG:3857'
```

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-io-shapefile:check :modules:mundane-map-io-image:check :modules:mundane-map-architecture-tests:check :examples:basic-viewer:check :examples:symbol-gallery:check :examples:measurement-viewer:check :examples:shapefile-viewer:check :examples:raster-viewer:check --console=plain
./gradlew :modules:mundane-map-api:javadoc :modules:mundane-map-core:javadoc :modules:mundane-map-awt:javadoc :modules:mundane-map-io-shapefile:javadoc :modules:mundane-map-io-image:javadoc --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Record the checkpoint in these Notes with commit, reviewer/date, Java/OS/architecture/window system/
display scale, exact commands, per-example result, API/Javadoc and compatibility dispositions, and
blockers. Fix accidental API inconsistencies only when compatibility impact is understood and
recorded. Avoid adding convenience abstractions solely to make the examples shorter.

Completed on 2026-07-16. The exhaustive generated-Javadoc/checkstyle review covered every public or
protected declaration in the five published modules. API values retain defensive array/collection
copies and toolkit-neutral signatures; core remains JDK-only; AWT retains its explicit instance-owned
registries, EDT confinement, and owned/borrowed lifecycle; shapefile and image expose only their
bounded format entry points/options/limits. No incompatible correction was approved or made. Java 21
doclint required the already-implicit public no-argument constructors of `CancellationSource`,
`MapToolRouter`, and `WebMercatorProjection` to be declared explicitly so they could be documented;
this is source-, ABI-, and behavior-neutral. `Layer`/`InMemoryLayer` remain supported small immutable
snapshots. Deprecated `FeatureStyle` remains supported through the first Level 1 `0.x` release with
role-specific symbols as its migration and removal before `1.0.0`; G8-004 still owns the actual
release version/support decision.

The five example dispositions are: basic uses the final point/line/fill symbol roles and has
headless fit, pointer, wheel, and offscreen-render evidence; gallery retains its exact G2 catalog,
resource/composite inventory and selects/paints every tab; measurement exposes both approved CRS
strategies with preview, committed distance, undo, cancel, pan, wheel, tab/units, and crimson-overlay
evidence; shapefile uses production parsing with bounded preview, attributes, holes, structured
reports, and ownership cleanup; raster uses production image/world-file loading with explicit decoder,
affine placement, interpolation/opacity controls, structured failures, and cleanup. Format viewer
identities are exactly `shapefile-viewer` and `raster-viewer`; sensitive parent/path/filename tests
exercise both successful and failing inputs with independent parent and filename-stem sentinel
tokens. On success the shapefile check binds the owned source in a real headless viewer session,
drives fit/render and opening-to-query/live-report transitions, enumerates the panel's record labels
and metadata/attribute/report text, then verifies view-owned cleanup. The raster check enumerates its
identity, binding, placement, status, and control strings. No absolute path, full filename, stem
token, or parent token reaches those presentation values. Failure checks retain stable structured
diagnostic codes and component/record/offset context while excluding the same locator values. The
raster review fixture is checksummed text transport of the exact G6-002
bytes; a cacheable prep task writes only beneath the example build directory and the production
viewer is unaware of Base64.

HITL evidence: the maintainer granted advance approval for every G6-G9 HITL checkpoint. Review used
OpenJDK 21.0.11 on Linux 5.15.167.4 WSL2 x86-64 through WSLg Wayland/X11 (`DISPLAY=:0`,
3840x1080 at 96 dpi; no GDK/QT scale override). All five exact commands above launched on the
available display without startup errors. Window captures confirmed the basic point/line/polygon
view and coordinate status; the gallery's marker tab; both measurement tabs and units explanation;
the shapefile polygon shells/hole, `Café` attribute, EPSG:3857 metadata, and separated reports; and
the rotated/sheared raster with interpolation, opacity, and placement status. Processes were closed
after capture. Automated headless tests establish the remaining tab selection, interaction,
topology, tolerant rendering, diagnostic, and cleanup behavior; no claim relies on pixel-identical
screenshots. The containing commit is the authoritative reviewed revision.

Final validation passed: the exact five-module/architecture/five-example check command (`BUILD
SUCCESSFUL` in 1m15s), the exact five strict Javadoc tasks (`BUILD SUCCESSFUL`; the final cached
confirmation took 1s), `renderRegression` (`BUILD SUCCESSFUL` in 7s), and `qualityGate` (`BUILD
SUCCESSFUL`; the final cached confirmation took 6s).
Generated options were inspected for UTF-8, all doclint groups, warning-as-error, no timestamp, and
no remote links. The review-fixture outputs matched 79/15 bytes and their recorded SHA-256 values.
Native, corpus, performance, publication, and consumer lanes were not folded into this task. No
G8-002 blocker remains.
