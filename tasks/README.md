# Tasks

Tasks are reviewable vertical capabilities, verification gates, or decisions that unblock a
working slice. The [roadmap](../ROADMAP.md) summarizes capability gates; this index is the
authoritative task set.

## Status vocabulary

- `Proposed`: scoped and ready once its dependencies are complete.
- `Blocked`: cannot make meaningful progress because a named external decision, tool, credential,
  or state is unavailable. An incomplete dependency alone does not make a task blocked.
- `Complete`: every acceptance criterion is implemented, required validation has passed, and the
  result is supported by current source and test evidence.

The initial source implements much of G1, but it remains proposed until its required evidence is
rerun. G0-001 has repaired and verified the build baseline; later tasks remain governed by their own
dependencies and evidence.

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

Tasks that share public API files, `MapView`, root Gradle files, this index, or the roadmap are not
path-safe without explicit ownership, even when their dependency graph permits concurrency.

## Level 1

Level 1 is complete only when G8-004 is complete.

### G0 — Verified baseline

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G0-001 — Current baseline verification](G0-001-current-baseline-verification.md) | Complete | AFK | None | Restore the Java 21 Gradle baseline and prove normal/publication staging. |
| [G0-002 — Architecture boundary hardening](G0-002-architecture-boundary-hardening.md) | Complete | AFK | G0-001 | Mechanically enforce production, AWT, I/O, dependency, and native boundaries. |

### G1 — First map slice

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G1-001 — First map slice verification](G1-001-first-map-slice-verification.md) | Complete | HITL | G0-002 | Verify and harden the implemented end-to-end Swing slice and native smoke. |

### G2 — Symbols and vector graphics

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G2-001 — Symbol contract and placement profile](G2-001-symbol-contract-and-placement-profile.md) | Complete | HITL | G1-001 | Lock symbol contracts, units, placement, rotation, and style migration. |
| [G2-002 — Toolkit-neutral vector paths and markers](G2-002-toolkit-neutral-vector-path-and-markers.md) | Complete | AFK | G2-001 | Render immutable vector paths and the built-in marker set. |
| [G2-003 — Symbol placement and composition](G2-003-symbol-placement-and-composition.md) | Complete | AFK | G2-002 | Render anchored, offset, rotated, scaled, opaque, and composite symbols. |
| [G2-004 — Line endpoints and hatch fills](G2-004-line-endpoints-and-hatch-fills.md) | Complete | AFK | G2-003 | Render endpoint markers, arrowheads, and bounded hatch fills. |
| [G2-005 — Raster icons, catalogs, and renderer registration](G2-005-raster-icons-catalogs-and-renderer-registration.md) | Complete | AFK | G2-004 | Add bounded icons, immutable catalogs, and explicit render registration. |
| [G2-006 — Symbol gallery and rendering regression](G2-006-symbol-gallery-and-render-regression.md) | Complete | HITL | G2-004, G2-005 | Deliver the gallery and create the tolerant rendering-regression lane. |
| [G2-007 — Native symbol resource smoke](G2-007-native-symbol-resource-smoke.md) | Complete | HITL | G2-006 | Exercise vector, catalog, registry, and explicit icon-resource paths natively. |

### G3 — Interaction and measurement

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G3-001 — Tool lifecycle and navigation routing](G3-001-tool-lifecycle-and-navigation-routing.md) | Complete | AFK | G4-002 | Add deterministic active-tool and pointer routing without breaking navigation. |
| [G3-002 — Symbol-aware hit testing and selection](G3-002-symbol-aware-hit-testing-and-selection.md) | Complete | AFK | G3-001, G2-005 | Select the deterministic topmost feature with pixel-tolerant geometry tests. |
| [G3-003 — Hover and selection rendering](G3-003-hover-and-selection-rendering.md) | Complete | AFK | G3-002 | Add stable hover/selection state and non-destructive visual feedback. |
| [G3-004 — Distance strategies and measurement tool](G3-004-distance-strategies-and-measurement-tool.md) | Complete | AFK | G3-003, G4-002 | Measure planar or recognized-geographic paths through an interactive tool. |

### G4 — Source contracts and CRS boundaries

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G4-001 — Source contract and diagnostic profile](G4-001-source-contract-and-diagnostic-profile.md) | Complete | HITL | G1-001 | Lock vector/raster query, lifecycle, limits, attributes, and diagnostics contracts. |
| [G4-002 — CRS boundary and projection hardening](G4-002-crs-boundary-and-projection-hardening.md) | Complete | AFK | G4-001 | Add explicit CRS metadata/registration and harden Web Mercator boundaries. |
| [G4-003 — Feature source query rendering slice](G4-003-feature-source-query-rendering-slice.md) | Complete | AFK | G2-005, G3-003, G3-004, G4-001, G4-002 | Render bounded viewport queries, including multipoint and multipart geometry. |
| [G4-004 — Raster source window rendering slice](G4-004-raster-source-window-rendering-slice.md) | Complete | AFK | G4-003 | Render synthetic toolkit-neutral raster windows with lifecycle and cancellation. |

### G5 — Read-only shapefile support

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G5-001 — Shapefile supported-profile decision](G5-001-shapefile-supported-profile-decision.md) | Proposed | HITL | G4-002, G4-003 | Lock the supported 2D shape, sidecar, encoding, mismatch, and limits profile. |
| [G5-002 — SHP point/multipoint sequential slice](G5-002-shp-point-multipoint-sequential-slice.md) | Proposed | AFK | G5-001 | Read and render null, point, and multipoint records from a new working adapter. |
| [G5-003 — SHX indexed access](G5-003-shx-indexed-access.md) | Proposed | AFK | G5-002 | Add validated indexed access and deterministic fallback behavior. |
| [G5-004 — Polyline multipart slice](G5-004-polyline-multipart-slice.md) | Proposed | AFK | G5-002 | Read and render bounded single- and multipart polylines. |
| [G5-005 — Polygon holes and multipart slice](G5-005-polygon-holes-multipart-slice.md) | Proposed | AFK | G5-004 | Read and render multipart polygons and holes predictably. |
| [G5-006 — DBF/CPG attributes and encoding](G5-006-dbf-cpg-attributes-and-encoding.md) | Proposed | AFK | G5-002 | Expose bounded DBF attributes with explicit CPG/fallback behavior. |
| [G5-007 — PRJ retention and recognized CRS](G5-007-prj-retention-and-recognized-crs.md) | Proposed | AFK | G5-002, G4-002 | Retain PRJ text and recognize only explicitly registered CRS definitions. |
| [G5-008 — Shapefile bounds, diagnostics, and fuzzing](G5-008-shapefile-bounds-diagnostics-and-fuzzing.md) | Proposed | AFK | G5-003, G5-005, G5-006, G5-007 | Bound hostile input and prove stable failures with deterministic fuzzing. |
| [G5-009 — Shapefile corpus and viewer completion](G5-009-shapefile-corpus-and-viewer-completion.md) | Proposed | HITL | G5-008 | Create the corpus lane, approve fixture provenance, and finish the viewer. |
| [G5-010 — Native shapefile smoke](G5-010-native-shapefile-smoke.md) | Proposed | HITL | G5-009 | Read/query/render a valid fixture and a stable malformed case natively. |

### G6 — Bounded PNG/JPEG raster support

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G6-001 — Bounded PNG/JPEG raster source](G6-001-bounded-png-jpeg-raster-source.md) | Proposed | AFK | G4-002, G4-004 | Add an AWT-free image adapter plus explicitly registered AWT decode path. |
| [G6-002 — World-file affine georeferencing](G6-002-world-file-affine-georeferencing.md) | Proposed | AFK | G6-001 | Apply bounded world-file affine georeferencing and CRS metadata. |
| [G6-003 — Raster requests and rendering controls](G6-003-raster-requests-and-rendering-controls.md) | Proposed | AFK | G6-002, G2-006 | Add windowing, resampling, opacity, interpolation, and affine rendering. |
| [G6-004 — Raster cache, lifecycle, and hardening](G6-004-raster-cache-lifecycle-and-hardening.md) | Proposed | AFK | G6-003 | Bound caches and malformed inputs while honoring close and cancellation. |
| [G6-005 — Native Image raster smoke](G6-005-native-image-raster-smoke.md) | Proposed | HITL | G2-007, G5-010, G6-004 | Decode and render PNG/JPEG with affine metadata under Native Image. |

### G7 — Performance evidence and optimization

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G7-001 — Performance evidence baseline](G7-001-performance-evidence-baseline.md) | Proposed | AFK | G2-006, G3-003, G5-009, G6-004 | Create repeatable large-data/JFR evidence before optimization. |
| [G7-002 — Packed spatial index and viewport query](G7-002-packed-spatial-index-and-viewport-query.md) | Proposed | AFK | G7-001, G4-003 | Replace linear viewport scans with a correctness-proven packed index. |
| [G7-003 — Clipping and simplification](G7-003-clipping-and-simplification.md) | Proposed | AFK | G7-002 | Clip and simplify at view scale without corrupting geometry. |
| [G7-004 — Render cache and performance acceptance](G7-004-render-cache-and-performance-acceptance.md) | Proposed | AFK | G7-003, G6-004 | Retain only evidence-qualified private AWT caches and record the Level 1 envelope. |

### G8 — Native and Level 1 release readiness

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G8-001 — Level 1 Native Image and CI hardening](G8-001-level1-native-image-and-ci-hardening.md) | Proposed | HITL | G2-007, G3-004, G5-010, G6-005, G7-004 | Pin one aggregate Ubuntu x86_64 native lane and its release evidence. |
| [G8-002 — Public API, Javadocs, and examples review](G8-002-public-api-javadocs-and-examples-review.md) | Proposed | HITL | G2-006, G3-004, G5-009, G6-004, G7-004 | Approve five public modules, strict Javadocs, and five Level 1 examples. |
| [G8-003 — Publication and consumer smoke](G8-003-publication-and-consumer-smoke.md) | Proposed | AFK | G8-001, G8-002 | Validate five staged artifacts through one clean offline Java 21 consumer. |
| [G8-004 — Level 1 release readiness](G8-004-level1-release-readiness.md) | Proposed | HITL | G8-003 | Reconcile one candidate SHA and record the Level 1 go/no-go decision. |

## Level 2 backlog

Level 2 starts after G8-004. DTED is decomposed because its model and binary profile are known;
other speculative capabilities stop at a supported-profile decision or first vertical slice.
A decision-only card is decomposed after approval only when it selects implementation; an approved
`DEFER` outcome creates no implementation card or module. G10-001 remains a working implementation
task; G10-006 records the defined acquisition design and delegates working code to G10-039 and
G10-060 through G10-062. Any broader follow-up still requires a new card.

### G9 — Elevation and DTED

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G9-001 — Format-neutral elevation model](G9-001-format-neutral-elevation-model.md) | Proposed | AFK | G8-004 | Model bounded regularly sampled elevation independently of file format. |
| [G9-002 — Elevation raster layer](G9-002-elevation-raster-layer.md) | Proposed | AFK | G9-001 | Render synthetic elevation with color ramps and optional hillshading. |
| [G9-003 — DTED levels reader slice](G9-003-dted-levels-reader-slice.md) | Proposed | AFK | G9-001 | Read DTED Levels 0, 1, and 2 into the shared elevation model. |
| [G9-004 — DTED validation and diagnostics](G9-004-dted-validation-and-diagnostics.md) | Proposed | AFK | G9-003 | Validate headers, dimensions, checksums, samples, voids, and truncation. |
| [G9-005 — Elevation position-query policy](G9-005-elevation-position-query-policy.md) | Proposed | AFK | G9-001, G9-003 | Provide explicit nearest and bilinear query behavior. |
| [G9-006 — Legally redistributable DTED corpus](G9-006-legally-redistributable-dted-corpus.md) | Proposed | HITL | G9-004 | Approve and verify an isolated Level 0/1/2 independent-writer corpus. |
| [G9-007 — DTED memory and read performance](G9-007-dted-memory-and-read-performance.md) | Proposed | AFK | G9-004, G9-005, G9-006 | Decide from largest-cell evidence whether DTED needs fallible windowed access. |
| [G9-008 — Native Image DTED smoke](G9-008-native-image-dted-smoke.md) | Proposed | HITL | G9-002, G9-005, G9-007 | Prove one representative Level 0 read/query/render path in the existing native executable. |

### G10 — Additional formats, tiles, and projections

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G10-001 — Secure SVG import profile and first slice](G10-001-secure-svg-import-profile-and-first-slice.md) | Proposed | HITL | G8-004 | Securely import static marker SVG into ordinary symbols and prove render/native/consumer paths. |
| [G10-002 — GeoJSON feature-source profile decision](G10-002-geojson-feature-source-profile-decision.md) | Proposed | HITL | G8-004 | Approve a strict 2D RFC 7946 source and one isolated Jackson Core adapter. |
| [G10-003 — GeoTIFF raster/elevation profile decision](G10-003-geotiff-raster-and-elevation-profile-decision.md) | Proposed | HITL | G8-004, G9-001 | Approve a strict JDK-only Classic TIFF profile with explicit raster/elevation routing. |
| [G10-004 — SQLite container adapter profiles](G10-004-sqlite-container-adapter-profiles.md) | Proposed | HITL | G8-004 | Approve strict GeoPackage/MBTiles profiles and a pinned Linux JVM-only Xerial boundary. |
| [G10-005 — GPX and KML source profiles](G10-005-gpx-and-kml-source-profiles.md) | Proposed | HITL | G8-004 | Approve separate bounded GPX 1.1 and static KML 2.2 feature sources. |
| [G10-006 — Remote tile source first slice](G10-006-remote-tile-source-first-slice.md) | Proposed | AFK | G8-004 | Design explicit bounded HTTP XYZ acquisition into detached raster snapshots. |
| [G10-007 — Additional projection selection](G10-007-additional-projection-selection.md) | Proposed | HITL | G8-004 | Approve the three-outcome evidence gate and record the current projection decision as DEFER. |

### G11 — Editing, styling, persistence, adapters, and export

| Task | Status | Type | Depends on | Outcome |
| --- | --- | --- | --- | --- |
| [G11-001 — Editing, undo, and snapping model](G11-001-editing-undo-and-snapping-model.md) | Proposed | HITL | G8-004 | Approve a point-first immutable edit session, bounded history, and same-CRS snapping. |
| [G11-002 — Thematic styling and label placement](G11-002-thematic-styling-and-label-placement.md) | Proposed | HITL | G8-004 | Approve closed thematic selectors and one bounded deterministic point-label pass. |
| [G11-003 — Workspace persistence profile](G11-003-workspace-persistence-profile.md) | Proposed | HITL | G8-004 | Approve strict local XML v1 persistence with explicit application openers and atomic replacement. |
| [G11-004 — Optional adapter boundaries](G11-004-optional-adapter-boundaries.md) | Proposed | HITL | G10-003, G10-004, G10-007, G11-001 | Accept two Xerial format adapters; defer JTS, PROJ, and GDAL. |
| [G11-005 — Vector map export profile](G11-005-vector-map-export-profile.md) | Proposed | HITL | G10-001, G11-002 | Approve detached AWT capture and canonical static SVG export in the existing SVG module. |
