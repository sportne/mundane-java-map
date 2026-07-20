# Tasks

Tasks are reviewable vertical capabilities, verification gates, or decisions that unblock a
working slice. The [roadmap](../ROADMAP.md) summarizes capability gates; this index is the
authoritative task set.

Incomplete task cards remain directly in `tasks/`. A task moves to `tasks/closed/` only when its
status becomes `Complete`; proposed and blocked cards must never be archived there.

## Status vocabulary

- `Proposed`: scoped and ready once its dependencies are complete.
- `Blocked`: cannot make meaningful progress because a named external decision, tool, credential,
  or state is unavailable. An incomplete dependency alone does not make a task blocked.
- `Complete`: every acceptance criterion is implemented, required validation has passed, and the
  result is supported by current source and test evidence.

G0 through G7 are complete from current source and test evidence. Later tasks remain governed by
their own dependencies and evidence; a task is never archived merely because implementation exists.

## Type vocabulary

- `AFK`: an agent can complete the task from repository context and available automated tooling.
- `HITL`: completion requires a maintainer decision, visual review, licensed corpus approval,
  external native tool, manual platform observation, or release approval. Every HITL task names
  its checkpoint in `Notes`.

## Task format

Each task uses this lean structure:

```markdown
# <TASK-ID> — <Title>

Status: Proposed | Blocked | Complete
Depends on: <task IDs or None>
Gate: <G0, G1, etc.>
Type: AFK | HITL

## Goal

## Context

## Scope

## Out of scope

## Acceptance criteria

## Required tests

## Validation

## Notes
```

Acceptance criteria describe observable completion rather than intended internal classes. Scope
names the modules and paths expected to change. Public API changes require Javadocs. Format tasks
must name supported behavior, limits, malformed-input handling, and stable diagnostics.

## Dependency and validation rules

- Dependencies form an acyclic graph. A proposed task may depend on another proposed task.
- Complete dependencies are required before implementation begins unless explicitly coordinated as
  parallel work below.
- Every implementation task ends with its narrowest relevant command, then
  `./gradlew qualityGate --console=plain`, then `git diff --check`.
- Native Image, corpus, rendering-regression, performance, and publication/consumer evidence stay
  separate from the normal quality gate.
- A specialized command may appear only in the task that creates it or a descendant:
  - G2-006 creates `./gradlew renderRegression --console=plain`.
  - G5-009 creates `./gradlew shapefileCorpus --console=plain`.
  - G7-001 creates `./gradlew performanceEvidence --console=plain`.
  - G9-006 creates `./gradlew dtedCorpus --console=plain`.
  - G15-005 creates `./gradlew liveTrackSmoke --console=plain`.
  - G15-007 creates
    `./gradlew liveTrackEvidence -PliveTrackProfile=<10k|100k|1m> --console=plain`.
    The evidence lane is opt-in and remains outside `qualityGate` and ordinary CI.
  - G0-001 creates `offlineRepositoryVerification` and `publicationDryRun`; the expensive offline
    proof remains separate from `qualityGate`. G8-003 hardens publication staging and creates
    `consumerSmoke`, yielding `./gradlew publicationDryRun consumerSmoke --console=plain`.
- New `mundane-map-io-*` modules are added only in the task that delivers working behavior and
  tests. No task creates an empty future-format module.

## Parallel work

Parallel work is safe only after dependencies are satisfied and scopes remain disjoint:

- G4-003 additionally waits for G2-005/G3-003/G3-004 interaction rendering and owns the common source/AWT
  foundation; G4-004 follows it rather than duplicating shared contracts in a parallel branch.
- After G5-002, G5-003, G5-004, G5-006, and G5-007 may proceed in reserved behavior-specific
  package-private source files; one owner must integrate the shared shapefile facade, source, cursor,
  diagnostics, and authoritative project inventory.
- G5 and G6 may proceed together after G4 once their separate module-registration edits have landed;
  their production branches remain independent through G6-004. G6-005 is the deliberate native-smoke
  convergence and waits for G2-007 and G5-010 before integrating the exact shared resource inventory.
- G9-002 and G9-003 may proceed together after G9-001.
- Level 2 profile decisions may proceed together after G8-004.
- G9-008 and G10-001 are dependency-parallel but both append to the one native executable and
  authoritative inventory. One owner serializes those shared files; whichever lands second retains
  the first task's required subset and reconciles the complete manifest rather than resetting a
  historical count.
- G9-003, G10-001, and later tasks that publish Level 2 modules follow the same append-only rule for
  the staged artifact manifest and consumer scenarios. Baseline artifact counts are task-required
  subsets; shared publication files have one owner and are not path-safe.
- The GeoTIFF slices G10-030 through G10-038 are deliberately serial because each extends the same
  bounded reader, diagnostics, fixtures, and publication/native inventory.
- G10-039 is a dependency-neutral image helper. After it lands, the GPX and KML branches may proceed
  together after G10-005, and the HTTP XYZ branch may proceed independently; shared module inventory,
  examples, rendering, publication, and native files still require one integration owner.
- G10-040 through G10-044 may not begin until their Proposed G10-004/G11-004 decisions complete.
  Once approved, the GeoPackage and MBTiles branches are logically parallel after their distinct
  roots, but Xerial dependency locking, publication, fixture, and support-evidence files are shared.
- After their approved profile roots, G11 editing/history and snapping branches converge at G11-013;
  graduated portrayal and label metrics may proceed together before collision placement; workspace
  persistence stays serial; and SVG export stays serial after the required label slices.
- Standards symbology is intentionally serial: G12 MIL-STD-2525 closes before G13 OGC SE begins,
  and G13 establishes the shared rule bridge before G14 MapLibre starts. Within G12, SIDC/parser and
  first-render work are serial and portrayal integration also waits for G11-024. Within G13 and G14,
  parser, rule/expression, rendering, hardening, and closeout tasks extend the same modules and are not
  path-safe in parallel.
- G15 is independent of the G12–G14 standards sequence after its existing G5/G7 foundations.
  After G15-001, G15-002/G15-003 estimator/simulator work and G15-004 Natural Earth chart work are
  dependency-parallel. They share example registration and later converge at G15-005, so one owner
  serializes root settings/Gradle and example inventory changes. G15-005 through G15-008 extend one
  viewer/coordinator/evidence path and are serial.

Tasks that share public API files, `MapView`, root Gradle files, this index, or the roadmap are not
path-safe without explicit ownership, even when their dependency graph permits concurrency.

## Level 1

Level 1 is complete only when G8-004 is complete.

### G0 — Verified baseline

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G0-001 — Current baseline verification](closed/G0-001-current-baseline-verification.md) | Complete | AFK | None | Restore the Java 21 Gradle baseline and prove normal/publication staging. |
| [G0-002 — Architecture boundary hardening](closed/G0-002-architecture-boundary-hardening.md) | Complete | AFK | G0-001 | Mechanically enforce production, AWT, I/O, dependency, and native boundaries. |
| [G0-003 — Persistent offline repository simplification](closed/G0-003-persistent-offline-repository-simplification.md) | Complete | AFK | G0-002 | Produce and verify one reusable exact offline Maven repository without a duplicate quality build. |
| [G0-004 — Declarative Gradle verification wiring](closed/G0-004-declarative-gradle-verification-wiring.md) | Complete | AFK | G0-003, G7-005, G8-005 | Replace task-graph machinery with explicit lane and coverage wiring. |

### G1 — First map slice

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G1-001 — First map slice verification](closed/G1-001-first-map-slice-verification.md) | Complete | HITL | G0-002 | Verify and harden the implemented end-to-end Swing slice and native smoke. |

### G2 — Symbols and vector graphics

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G2-001 — Symbol contract and placement profile](closed/G2-001-symbol-contract-and-placement-profile.md) | Complete | HITL | G1-001 | Lock symbol contracts, units, placement, rotation, and style migration. |
| [G2-002 — Toolkit-neutral vector paths and markers](closed/G2-002-toolkit-neutral-vector-path-and-markers.md) | Complete | AFK | G2-001 | Render immutable vector paths and the built-in marker set. |
| [G2-003 — Symbol placement and composition](closed/G2-003-symbol-placement-and-composition.md) | Complete | AFK | G2-002 | Render anchored, offset, rotated, scaled, opaque, and composite symbols. |
| [G2-004 — Line endpoints and hatch fills](closed/G2-004-line-endpoints-and-hatch-fills.md) | Complete | AFK | G2-003 | Render endpoint markers, arrowheads, and bounded hatch fills. |
| [G2-005 — Raster icons, catalogs, and renderer registration](closed/G2-005-raster-icons-catalogs-and-renderer-registration.md) | Complete | AFK | G2-004 | Add bounded icons, immutable catalogs, and explicit render registration. |
| [G2-006 — Symbol gallery and rendering regression](closed/G2-006-symbol-gallery-and-render-regression.md) | Complete | HITL | G2-004, G2-005 | Deliver the gallery and create the tolerant rendering-regression lane. |
| [G2-007 — Native symbol resource smoke](closed/G2-007-native-symbol-resource-smoke.md) | Complete | HITL | G2-006 | Exercise vector, catalog, registry, and explicit icon-resource paths natively. |

### G3 — Interaction and measurement

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G3-001 — Tool lifecycle and navigation routing](closed/G3-001-tool-lifecycle-and-navigation-routing.md) | Complete | AFK | G4-002 | Add deterministic active-tool and pointer routing without breaking navigation. |
| [G3-002 — Symbol-aware hit testing and selection](closed/G3-002-symbol-aware-hit-testing-and-selection.md) | Complete | AFK | G3-001, G2-005 | Select the deterministic topmost feature with pixel-tolerant geometry tests. |
| [G3-003 — Hover and selection rendering](closed/G3-003-hover-and-selection-rendering.md) | Complete | AFK | G3-002 | Add stable hover/selection state and non-destructive visual feedback. |
| [G3-004 — Distance strategies and measurement tool](closed/G3-004-distance-strategies-and-measurement-tool.md) | Complete | AFK | G3-003, G4-002 | Measure planar or recognized-geographic paths through an interactive tool. |

### G4 — Source contracts and CRS boundaries

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G4-001 — Source contract and diagnostic profile](closed/G4-001-source-contract-and-diagnostic-profile.md) | Complete | HITL | G1-001 | Lock vector/raster query, lifecycle, limits, attributes, and diagnostics contracts. |
| [G4-002 — CRS boundary and projection hardening](closed/G4-002-crs-boundary-and-projection-hardening.md) | Complete | AFK | G4-001 | Add explicit CRS metadata/registration and harden Web Mercator boundaries. |
| [G4-003 — Feature source query rendering slice](closed/G4-003-feature-source-query-rendering-slice.md) | Complete | AFK | G2-005, G3-003, G3-004, G4-001, G4-002 | Render bounded viewport queries, including multipoint and multipart geometry. |
| [G4-004 — Raster source window rendering slice](closed/G4-004-raster-source-window-rendering-slice.md) | Complete | AFK | G4-003 | Render synthetic toolkit-neutral raster windows with lifecycle and cancellation. |

### G5 — Read-only shapefile support

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G5-001 — Shapefile supported-profile decision](closed/G5-001-shapefile-supported-profile-decision.md) | Complete | HITL | G4-002, G4-003 | Lock the supported 2D shape, sidecar, encoding, mismatch, and limits profile. |
| [G5-002 — SHP point/multipoint sequential slice](closed/G5-002-shp-point-multipoint-sequential-slice.md) | Complete | AFK | G5-001 | Read and render null, point, and multipoint records from a new working adapter. |
| [G5-003 — SHX indexed access](closed/G5-003-shx-indexed-access.md) | Complete | AFK | G5-002 | Add validated indexed access and deterministic fallback behavior. |
| [G5-004 — Polyline multipart slice](closed/G5-004-polyline-multipart-slice.md) | Complete | AFK | G5-002 | Read and render bounded single- and multipart polylines. |
| [G5-005 — Polygon holes and multipart slice](closed/G5-005-polygon-holes-multipart-slice.md) | Complete | AFK | G5-004 | Read and render multipart polygons and holes predictably. |
| [G5-006 — DBF/CPG attributes and encoding](closed/G5-006-dbf-cpg-attributes-and-encoding.md) | Complete | AFK | G5-002 | Expose bounded DBF attributes with explicit CPG/fallback behavior. |
| [G5-007 — PRJ retention and recognized CRS](closed/G5-007-prj-retention-and-recognized-crs.md) | Complete | AFK | G5-002, G4-002 | Retain PRJ text and recognize only explicitly registered CRS definitions. |
| [G5-008 — Shapefile bounds, diagnostics, and fuzzing](closed/G5-008-shapefile-bounds-diagnostics-and-fuzzing.md) | Complete | AFK | G5-003, G5-005, G5-006, G5-007 | Bound hostile input and prove stable failures with deterministic fuzzing. |
| [G5-009 — Shapefile corpus and viewer completion](closed/G5-009-shapefile-corpus-and-viewer-completion.md) | Complete | HITL | G5-008 | Create the corpus lane, approve fixture provenance, and finish the viewer. |
| [G5-010 — Native shapefile smoke](closed/G5-010-native-shapefile-smoke.md) | Complete | HITL | G5-009 | Read/query/render fixed valid and malformed resources under the exact native inventory policy. |

### G6 — Bounded PNG/JPEG raster support

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G6-001 — Bounded PNG/JPEG raster source](closed/G6-001-bounded-png-jpeg-raster-source.md) | Complete | AFK | G4-002, G4-004 | Add an AWT-free image adapter plus explicitly registered AWT decode path. |
| [G6-002 — World-file affine georeferencing](closed/G6-002-world-file-affine-georeferencing.md) | Complete | AFK | G6-001 | Apply bounded world-file affine georeferencing and CRS metadata. |
| [G6-003 — Raster requests and rendering controls](closed/G6-003-raster-requests-and-rendering-controls.md) | Complete | AFK | G6-002, G2-006 | Add windowing, resampling, opacity, interpolation, and affine rendering. |
| [G6-004 — Raster cache, lifecycle, and hardening](closed/G6-004-raster-cache-lifecycle-and-hardening.md) | Complete | AFK | G6-003 | Bound caches and malformed inputs while honoring close and cancellation. |
| [G6-005 — Native Image raster smoke](closed/G6-005-native-image-raster-smoke.md) | Complete | HITL | G2-007, G5-010, G6-004 | Decode and render PNG/JPEG with affine metadata under Native Image. |

### G7 — Performance evidence and optimization

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G7-001 — Performance evidence baseline](closed/G7-001-performance-evidence-baseline.md) | Complete | AFK | G2-006, G3-003, G5-009, G6-004 | Create repeatable large-data/JFR evidence before optimization. |
| [G7-002 — Packed spatial index and viewport query](closed/G7-002-packed-spatial-index-and-viewport-query.md) | Complete | AFK | G7-001, G4-003 | Replace linear viewport scans with a correctness-proven packed index. |
| [G7-003 — Clipping and simplification](closed/G7-003-clipping-and-simplification.md) | Complete | AFK | G7-002 | Clip and simplify at view scale without corrupting geometry. |
| [G7-004 — Render cache and performance acceptance](closed/G7-004-render-cache-and-performance-acceptance.md) | Complete | AFK | G7-003, G6-004 | Retain the qualified vector-template cache, remove the rejected screen cache, and record the Level 1 envelope. |
| [G7-005 — Lean performance build harness](closed/G7-005-lean-performance-build-harness.md) | Complete | AFK | G7-004, G9-007 | Preserve Java 21 `/tmp` evidence while deleting unnecessary build security and graph machinery. |

### G8 — Native and Level 1 release readiness

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G8-001 — Level 1 Native Image and CI hardening](closed/G8-001-level1-native-image-and-ci-hardening.md) | Complete | HITL | G2-007, G3-004, G5-010, G6-005, G7-004 | Verify the aggregate Level 1 native path on the pinned Ubuntu x86_64 release lane. |
| [G8-002 — Public API, Javadocs, and examples review](closed/G8-002-public-api-javadocs-and-examples-review.md) | Complete | HITL | G2-006, G3-004, G5-009, G6-004, G7-004 | Approve five public modules, strict Javadocs, and five Level 1 examples. |
| [G8-003 — Publication and consumer smoke](closed/G8-003-publication-and-consumer-smoke.md) | Complete | AFK | G8-002 | Validate five staged artifacts through one clean offline Java 21 consumer. |
| [G8-004 — Level 1 release readiness](closed/G8-004-level1-release-readiness.md) | Complete | HITL | G8-001, G8-003 | Approve candidate `a5d1079` for the bounded Level 1 `0.1.0` support statement. |
| [G8-005 — Lean publication and consumer verification](closed/G8-005-lean-publication-and-consumer-verification.md) | Complete | AFK | G8-003 | Preserve the staged Maven repository and positive consumer with targeted verification. |

## Level 2 backlog

Level 2 starts after G8-004. Approved profiles with implementation outcomes are decomposed below into
reviewable vertical slices; an approved `DEFER` outcome creates no implementation card or module.
The G10-040 through G10-044 SQLite-format cards are planning records only until both Proposed
G10-004 and G11-004 complete, so their existence does not approve Xerial, GeoPackage, or MBTiles.
Every card remains subject to its dependencies, and broader follow-up still requires a new card.

### G9 — Elevation and DTED

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G9-001 — Format-neutral elevation model](closed/G9-001-format-neutral-elevation-model.md) | Complete | AFK | G8-004 | Model bounded regularly sampled elevation independently of file format. |
| [G9-002 — Elevation raster layer](closed/G9-002-elevation-raster-layer.md) | Complete | AFK | G9-001 | Render synthetic elevation with color ramps and optional hillshading. |
| [G9-003 — DTED levels reader slice](closed/G9-003-dted-levels-reader-slice.md) | Complete | AFK | G9-001 | Read DTED Levels 0, 1, and 2 into the shared elevation model. |
| [G9-004 — DTED validation and diagnostics](closed/G9-004-dted-validation-and-diagnostics.md) | Complete | AFK | G9-003 | Validate headers, dimensions, checksums, samples, voids, and truncation. |
| [G9-005 — Elevation position-query policy](closed/G9-005-elevation-position-query-policy.md) | Complete | AFK | G9-001, G9-003 | Provide explicit nearest and bilinear query behavior. |
| [G9-006 — Legally redistributable DTED corpus](closed/G9-006-legally-redistributable-dted-corpus.md) | Complete | HITL | G9-004 | Approve and verify an isolated Level 0/1/2 independent-writer corpus. |
| [G9-007 — DTED memory and read performance](closed/G9-007-dted-memory-and-read-performance.md) | Complete | AFK | G9-004, G9-005, G9-006 | Retain eager DTED access from maximum-cell memory/read evidence. |
| [G9-008 — Native Image DTED smoke](closed/G9-008-native-image-dted-smoke.md) | Complete | HITL | G9-002, G9-005, G9-007 | Verify one representative Level 0 path in the existing native executable. |

### G10 — Additional formats, tiles, and projections

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G10-001 — Secure SVG import profile and first slice](closed/G10-001-secure-svg-import-profile-and-first-slice.md) | Complete | HITL | G8-004 | Securely import static marker SVG into ordinary symbols and prove render/native/consumer paths. |
| [G10-002 — GeoJSON feature-source profile decision](closed/G10-002-geojson-feature-source-profile-decision.md) | Complete | HITL | G8-004 | Approve a strict 2D RFC 7946 reader/writer and one isolated Jackson Core adapter. |
| [G10-003 — GeoTIFF raster/elevation profile decision](closed/G10-003-geotiff-raster-and-elevation-profile-decision.md) | Complete | HITL | G8-004, G9-001 | Approve a strict JDK-only Classic TIFF profile with explicit raster/elevation routing. |
| [G10-004 — SQLite container adapter profiles](G10-004-sqlite-container-adapter-profiles.md) | Proposed | HITL | G8-004 | Decide whether to approve strict GeoPackage/MBTiles profiles and a pinned Linux JVM-only Xerial boundary. |
| [G10-005 — GPX and KML source profiles](closed/G10-005-gpx-and-kml-source-profiles.md) | Complete | HITL | G8-004 | Approve separate bounded GPX 1.1 and static KML 2.2 feature sources. |
| [G10-006 — Remote tile source first slice](closed/G10-006-remote-tile-source-first-slice.md) | Complete | AFK | G8-004 | Design explicit bounded HTTP XYZ acquisition into detached raster snapshots. |
| [G10-007 — Additional projection selection](closed/G10-007-additional-projection-selection.md) | Complete | HITL | G8-004 | Approve the three-outcome evidence gate and record the current projection decision as DEFER. |
| [G10-020 — GeoJSON adapter and first read slice](closed/G10-020-geojson-adapter-first-read-slice.md) | Complete | AFK | G10-002 | Publish a working bounded Point/MultiPoint Jackson adapter. |
| [G10-021 — GeoJSON geometry completion](closed/G10-021-geojson-geometry-completion.md) | Complete | AFK | G10-020 | Read and render all six approved geometry families. |
| [G10-022 — GeoJSON deterministic writer](closed/G10-022-geojson-deterministic-writer.md) | Complete | AFK | G10-021 | Write bounded deterministic FeatureCollections with atomic replacement. |
| [G10-023 — GeoJSON hostile-input and writer hardening](closed/G10-023-geojson-hardening.md) | Complete | AFK | G10-022 | Close reader/writer limits, diagnostics, cancellation, and cleanup. |
| [G10-024 — GeoJSON fixtures, viewer, and consumer evidence](closed/G10-024-geojson-fixtures-viewer-consumer.md) | Complete | HITL | G10-023 | Prove fixture, viewer, rendering, publication, and consumer interoperability. |
| [G10-025 — Native Image GeoJSON closeout](closed/G10-025-native-image-geojson-closeout.md) | Complete | HITL | G10-024 | Prove the bounded Linux native read/write/query/render path without service discovery. |
| [G10-030 — GeoTIFF first raster slice](closed/G10-030-geotiff-first-raster-slice.md) | Complete | AFK | G10-003 | Read, query, render, publish, and consume one bounded little-endian area raster. |
| [G10-031 — GeoTIFF raster layout and color completion](closed/G10-031-geotiff-raster-layout-and-color-completion.md) | Complete | AFK | G10-030 | Decode both byte orders, exact strips/tiles, approved color/alpha profiles, and EPSG:3857. |
| [G10-032 — GeoTIFF PackBits and Deflate](closed/G10-032-geotiff-packbits-and-deflate.md) | Complete | AFK | G10-031 | Decode and render bounded PackBits and Adobe Deflate with exact stream diagnostics. |
| [G10-033 — GeoTIFF affine raster placement](closed/G10-033-geotiff-affine-raster-placement.md) | Complete | AFK | G10-032 | Place and regress finite invertible rotated/sheared ModelTransformation rasters. |
| [G10-034 — GeoTIFF integer elevation slice](closed/G10-034-geotiff-integer-elevation-slice.md) | Complete | AFK | G10-033, G9-002, G9-005 | Read, query, colorize, and render signed integer PixelIsPoint elevation. |
| [G10-035 — GeoTIFF floating elevation and no-data](closed/G10-035-geotiff-floating-elevation-and-no-data.md) | Complete | AFK | G10-034 | Add floating samples, no-data, hillshade, and compressed/tiled parity. |
| [G10-036 — GeoTIFF hardening](closed/G10-036-geotiff-hardening.md) | Complete | AFK | G10-035 | Close limits, diagnostics, cancellation, cleanup, aliasing, and hostile mutation. |
| [G10-037 — GeoTIFF corpus, viewers, and performance](closed/G10-037-geotiff-corpus-viewers-and-performance.md) | Complete | HITL | G10-036 | Approve an independent corpus and complete viewer/performance evidence. |
| [G10-038 — Native Image GeoTIFF closeout](closed/G10-038-native-image-geotiff-closeout.md) | Complete | HITL | G10-037 | Prove the bounded raster/elevation/codec Linux Native Image paths. |
| [G10-039 — Encoded raster byte decoder](closed/G10-039-encoded-raster-byte-decoder.md) | Complete | AFK | G6-004, G10-006 | Decode bounded detached PNG/JPEG bytes through the explicit image registry. |
| [G10-040 — GeoPackage catalog and point features](G10-040-geopackage-catalog-and-point-features.md) | Proposed | AFK | G10-004, G11-004 | Qualify Xerial and deliver bounded GeoPackage catalog/point feature behavior. |
| [G10-041 — GeoPackage feature completion](G10-041-geopackage-feature-completion.md) | Proposed | AFK | G10-040 | Complete geometry, attributes, CRS, query, viewer, and feature hardening. |
| [G10-042 — GeoPackage tiles and hardening](G10-042-geopackage-tiles-and-hardening.md) | Proposed | HITL | G10-041, G10-039 | Render bounded sparse tile matrices and approve independent container evidence. |
| [G10-043 — MBTiles raster slice](G10-043-mbtiles-raster-slice.md) | Proposed | AFK | G10-039, G11-004 | Read and render bounded TMS PNG/JPEG MBTiles through a staged adapter. |
| [G10-044 — SQLite adapter hardening and Linux evidence](G10-044-sqlite-adapter-hardening-and-linux-evidence.md) | Proposed | HITL | G10-042, G10-043 | Close MBTiles hardening and approve exact Linux JVM support for both adapters. |
| [G10-050 — GPX waypoint first slice](G10-050-gpx-waypoint-first-slice.md) | Proposed | AFK | G10-005 | Read, query, render, publish, and consume bounded GPX waypoints. |
| [G10-051 — GPX track rendering slice](G10-051-gpx-track-rendering-slice.md) | Proposed | AFK | G10-050 | Render bounded track segments with fixed attributes and warned omissions. |
| [G10-052 — GPX hardening and fixtures](G10-052-gpx-hardening-and-fixtures.md) | Proposed | HITL | G10-051 | Close grammar and hostile-input behavior and approve fixture provenance. |
| [G10-053 — Native Image GPX smoke](G10-053-native-image-gpx-smoke.md) | Proposed | HITL | G10-052 | Prove bounded GPX success, warning, and malformed Linux native paths. |
| [G10-054 — KML point and line first slice](G10-054-kml-point-line-first-slice.md) | Proposed | AFK | G10-005 | Read, query, render, publish, and consume bounded KML points and lines. |
| [G10-055 — KML polygon and MultiGeometry slice](G10-055-kml-polygon-multigeometry-slice.md) | Proposed | AFK | G10-054 | Render bounded polygons and homogeneous MultiGeometry values. |
| [G10-056 — KML hardening and fixtures](G10-056-kml-hardening-and-fixtures.md) | Proposed | HITL | G10-055 | Close KML warning/rejection/security behavior and approve fixture provenance. |
| [G10-057 — Native Image KML closeout](G10-057-native-image-kml-closeout.md) | Proposed | HITL | G10-053, G10-056 | Prove bounded KML native paths and close shared XML security evidence. |
| [G10-060 — HTTP tile one-tile slice](G10-060-http-tile-one-tile-slice.md) | Proposed | AFK | G10-039 | Acquire one bounded PNG/JPEG tile into a detached source through loopback HTTP. |
| [G10-061 — HTTP tile regions, cache, and rendering](G10-061-http-tile-region-cache-rendering.md) | Proposed | AFK | G10-060 | Add exact region math, deterministic batches, missing tiles, LRU, and rendering. |
| [G10-062 — HTTP tile hardening and JVM closeout](G10-062-http-tile-hardening-jvm-closeout.md) | Proposed | AFK | G10-061 | Close HTTP limits/lifecycle failures and document the JVM-only boundary. |

### G11 — Editing, styling, persistence, adapters, and export

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G11-001 — Editing, undo, and snapping model](closed/G11-001-editing-undo-and-snapping-model.md) | Complete | HITL | G8-004 | Approve a point-first immutable edit session, bounded history, and same-CRS snapping. |
| [G11-002 — Thematic styling and label placement](closed/G11-002-thematic-styling-and-label-placement.md) | Complete | HITL | G8-004 | Approve closed thematic selectors and one bounded deterministic point-label pass. |
| [G11-003 — Workspace persistence profile](closed/G11-003-workspace-persistence-profile.md) | Complete | HITL | G8-004 | Approve strict local XML v1 persistence with explicit application openers and atomic replacement. |
| [G11-004 — Optional adapter boundaries](G11-004-optional-adapter-boundaries.md) | Proposed | HITL | G10-003, G10-004, G10-007, G11-001 | Decide whether to accept two Xerial format adapters and defer JTS, PROJ, and GDAL. |
| [G11-005 — Vector map export profile](closed/G11-005-vector-map-export-profile.md) | Complete | HITL | G10-001, G11-002 | Approve detached AWT capture and canonical static SVG export in the existing SVG module. |
| [G11-010 — Immutable point-edit session slice](closed/G11-010-immutable-point-edit-session-slice.md) | Complete | AFK | G11-001 | Create, replace, and delete immutable point features through a real editable map binding. |
| [G11-011 — Bounded undo/redo slice](closed/G11-011-bounded-undo-redo-slice.md) | Complete | AFK | G11-010 | Add bounded delta history, eviction, rollback evidence, and viewer undo/redo. |
| [G11-012 — Same-CRS snap resolver slice](G11-012-same-crs-snap-resolver-slice.md) | Proposed | AFK | G11-010 | Resolve bounded deterministic vertex/segment snaps with visible preview. |
| [G11-013 — Point editing tool completion](G11-013-point-editing-tool-completion.md) | Proposed | HITL | G11-011, G11-012 | Complete interactive point editing and approve viewer/render/native evidence. |
| [G11-020 — Portrayal and categorical marker slice](closed/G11-020-portrayal-and-categorical-marker-slice.md) | Complete | AFK | G11-002, G11-010 | Resolve fixed/categorical marker portrayals across source and editable bindings. |
| [G11-021 — Graduated and complete-role portrayal](closed/G11-021-graduated-and-complete-role-portrayal.md) | Complete | AFK | G11-020 | Complete graduated marker/line/fill selection with query/interaction agreement. |
| [G11-022 — Point-label values and global paint pass](closed/G11-022-point-label-values-and-global-paint-pass.md) | Complete | AFK | G11-020 | Extract, measure, place, and paint bounded singular-point labels globally. |
| [G11-023 — Bounded label placement and example](closed/G11-023-bounded-label-placement-and-example.md) | Complete | HITL | G11-021, G11-022 | Add deterministic collision placement and approve the styling/label example. |
| [G11-024 — Styling and label closeout](closed/G11-024-styling-label-closeout.md) | Complete | HITL | G11-023 | Close styling/label API, performance, consumer, publication, and native evidence. |
| [G11-030 — Workspace model and secure reader](G11-030-workspace-model-and-secure-reader.md) | Proposed | AFK | G11-003 | Read a bounded secure local XML workspace into immutable values. |
| [G11-031 — Canonical workspace writer](G11-031-canonical-workspace-writer.md) | Proposed | AFK | G11-030 | Serialize canonical workspace XML with atomic replacement and failure evidence. |
| [G11-032 — Workspace registries and session opening](G11-032-workspace-registries-and-session-opening.md) | Proposed | AFK | G11-031 | Open all workspace sources through explicit registries with all-or-nothing ownership. |
| [G11-033 — Workspace viewer and local restore](G11-033-workspace-viewer-and-local-restore.md) | Proposed | AFK | G11-032 | Restore a useful local shapefile/raster workspace in a runnable viewer. |
| [G11-034 — Workspace hardening and closeout](G11-034-workspace-hardening-and-closeout.md) | Proposed | HITL | G11-033 | Close hostile input, docs, publication/consumer, and Linux native evidence. |
| [G11-040 — Programmatic SVG map-export slice](G11-040-programmatic-svg-map-export-slice.md) | Proposed | AFK | G11-005, G11-022 | Encode and atomically write a bounded programmatic vector-map snapshot. |
| [G11-041 — AWT capture and complete vector profile](G11-041-awt-capture-and-complete-vector-profile.md) | Proposed | AFK | G11-023, G11-040 | Capture a real map and export the complete approved vector/symbol/label profile. |
| [G11-042 — SVG export hardening](G11-042-svg-export-hardening.md) | Proposed | AFK | G11-041 | Close export limits, accounting, cancellation, diagnostics, cleanup, and atomicity. |
| [G11-043 — SVG export closeout](G11-043-svg-export-closeout.md) | Proposed | HITL | G11-024, G11-042 | Approve browser rendering and close native/publication/consumer evidence. |

### G12 — MIL-STD-2525 symbology

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G12-001 — MIL-STD-2525 profile and legal decision](G12-001-milstd2525-profile-and-legal-decision.md) | Proposed | HITL | G2-007 | Approve the exact 2525E Change 1 point profile, tables, fixture rights, and support wording. |
| [G12-002 — Canonical SIDC model and parser](G12-002-canonical-sidc-model-and-parser.md) | Proposed | AFK | G12-001 | Create the working JDK-only module with canonical 30-position SIDC parsing and classification. |
| [G12-003 — First frame and entity rendering slice](G12-003-first-frame-and-entity-rendering-slice.md) | Proposed | AFK | G12-002 | Render and hit-test standard-identity frames and a first entity through ordinary symbols. |
| [G12-004 — Land/activity catalog and portrayal](G12-004-land-activity-catalog-and-portrayal.md) | Proposed | AFK | G12-003, G11-024 | Complete the finite inventory and select symbols from an explicit feature SIDC attribute. |
| [G12-005 — Reference matrix, gallery, and hardening](G12-005-reference-matrix-gallery-and-hardening.md) | Proposed | HITL | G12-004 | Harden tables/SIDCs and approve the provenance matrix and runnable gallery. |
| [G12-006 — MIL-STD-2525 native and publication closeout](G12-006-milstd2525-native-publication-closeout.md) | Proposed | HITL | G12-005 | Close staged consumer, Linux Native Image, documentation, and support evidence. |

### G13 — OGC Symbology Encoding

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G13-001 — SE profile and portrayal-bridge decision](G13-001-se-profile-and-portrayal-bridge-decision.md) | Proposed | HITL | G12-006, G11-024 | Approve the SE 1.1 subset and one shared closed rule/filter/scale bridge. |
| [G13-002 — Secure SE point-symbolizer slice](G13-002-secure-se-point-symbolizer-slice.md) | Proposed | AFK | G13-001 | Create the secure JDK-only adapter and render a literal well-known point marker. |
| [G13-003 — SE rules, filters, and scale](G13-003-se-rules-filters-and-scale.md) | Proposed | AFK | G13-002 | Apply ordered bounded rules by attributes and explicit scale context. |
| [G13-004 — SE line, polygon, and catalog graphics](G13-004-se-line-polygon-and-catalog-graphics.md) | Proposed | AFK | G13-003 | Complete vector symbolizers and explicit catalog-only external graphics. |
| [G13-005 — SE fixtures, gallery, and hardening](G13-005-se-fixtures-gallery-and-hardening.md) | Proposed | HITL | G13-004 | Close hostile/interoperability evidence and approve the SE gallery. |
| [G13-006 — SE native and publication closeout](G13-006-se-native-publication-closeout.md) | Proposed | HITL | G13-005 | Close staged consumer, Linux Native Image, subset wording, and bridge evidence. |

### G14 — MapLibre Style

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G14-001 — MapLibre Style profile decision](G14-001-maplibre-style-profile-decision.md) | Proposed | HITL | G13-006, G10-025 | Approve the v8 root/source/layer/property/expression matrix and Jackson boundary. |
| [G14-002 — Literal vector-layer slice](G14-002-literal-vector-layer-slice.md) | Proposed | AFK | G14-001 | Create the optional adapter and render literal circle, line, and fill layers. |
| [G14-003 — Explicit source, filter, and zoom binding](G14-003-explicit-source-filter-and-zoom-binding.md) | Proposed | AFK | G14-002 | Bind caller sources transactionally and apply filters, zoom ranges, and layer order. |
| [G14-004 — Bounded MapLibre expressions](G14-004-bounded-maplibre-expressions.md) | Proposed | AFK | G14-003 | Evaluate the approved closed typed expression subset with exact limits and types. |
| [G14-005 — Symbol icons and point labels](G14-005-symbol-icons-and-point-labels.md) | Proposed | AFK | G14-004 | Resolve caller-catalog icons and place bounded G11-compatible point labels. |
| [G14-006 — MapLibre fixtures, gallery, and hardening](G14-006-maplibre-fixtures-gallery-and-hardening.md) | Proposed | HITL | G14-005 | Close hostile/interoperability evidence and approve the MapLibre gallery. |
| [G14-007 — MapLibre native and publication closeout](G14-007-maplibre-native-publication-closeout.md) | Proposed | HITL | G14-006 | Close dependency, staged consumer, Linux Native Image, and G12–G14 evidence. |

### G15 — Live-track stress and IOU tracking

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G15-001 — Live-track stress and IOU-Kalman Filter profile decision](G15-001-live-track-stress-and-iou-kalman-profile-decision.md) | Proposed | HITL | G5-010, G7-005 | Approve the bounded IOU-Kalman Filter state estimator, stochastic workload, Natural Earth, packed execution, and evidence profile. |
| [G15-002 — Optimized IOU-Kalman Filter kernel](G15-002-optimized-iou-kalman-kernel.md) | Proposed | AFK | G15-001 | Prove an allocation-free packed estimator against an independent dense oracle. |
| [G15-003 — Packed stochastic track simulator](G15-003-packed-stochastic-track-simulator.md) | Proposed | AFK | G15-002 | Simulate and filter individually scheduled tracks with a timing wheel and stable shards. |
| [G15-004 — Natural Earth global chart](G15-004-natural-earth-global-chart.md) | Proposed | HITL | G15-001, G5-010 | Bundle a provenance-backed 1:110m land chart and render it through the shapefile stack. |
| [G15-005 — First 10k live-picture slice](G15-005-first-10k-live-picture-slice.md) | Proposed | HITL | G15-003, G15-004 | Display 10k filtered tracks with frame pacing, telemetry, and a fast smoke lane. |
| [G15-006 — 100k sharded tracking and rendering](G15-006-100k-sharded-tracking-and-rendering.md) | Proposed | AFK | G15-005 | Scale the same deterministic behavior to 100k using measured packed sharding. |
| [G15-007 — Million-track stress and evidence lane](G15-007-million-track-stress-and-evidence-lane.md) | Proposed | HITL | G15-006 | Run the 1m tier and produce JSON/Markdown evidence for all three populations. |
| [G15-008 — Live-track hardening and closeout](G15-008-live-track-hardening-and-closeout.md) | Proposed | HITL | G15-007 | Close lifecycle, overload, replay, documentation, visual, and simplicity evidence. |
