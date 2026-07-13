# Design

## Goals

- Provide a lightweight embeddable Java map component.
- Keep Level 1 production modules free of third-party runtime dependencies.
- Support explicit, deterministic APIs that work on the JVM and with GraalVM Native Image.
- Grow by tested vertical capabilities rather than speculative abstraction layers.
- Keep data formats behind format-neutral vector and raster source contracts as they are added.

## Non-goals

- A full desktop GIS.
- Runtime plugin discovery, classpath scanning, or reflection-based registration.
- Arbitrary CRS transformation, GeoTIFF, vector editing, or geometry overlay operations in the
  initial slice.
- Custom native libraries for performance without benchmark evidence.

## Design principles

- Prefer the smallest explicit model that supports the next observable vertical slice.
- Introduce an abstraction only for a demonstrated second consumer or a boundary that must remain
  independent, such as toolkit-neutral public contracts versus Java2D rendering.
- Give the common embedding path deterministic defaults and few moving parts; keep limits,
  registries, lifecycle, and advanced policy explicit rather than ambient.
- Assign every value, resource, cache, and registry one clear owner. Immutability is the default at
  public boundaries; mutable state remains local and lifecycle-bound.
- Reuse cross-cutting diagnostics, limits, and cancellation semantics instead of inventing a
  framework inside each format adapter.
- Delete or defer an abstraction when its owner, enforcement, or developer benefit cannot be stated
  plainly. The design should remain as simple as possible, but no simpler than its correctness and
  safety boundaries require.

## Dependency policy

Level 1 production modules use only the JDK and other `mundane-map` modules. Build and test tooling
may use JUnit, ArchUnit, JaCoCo, Checkstyle, SpotBugs, Error Prone, Spotless, and GraalVM Native
Build Tools. Future external integrations must live in optional adapter modules and must not leak
their types through `mundane-map-api`.

## Detailed design index

This file is the project-wide design entry point and owns goals, principles, cross-gate decisions, and
task traceability. Detailed architecture is gate-oriented so a task normally reads this index, its
own gate file, and only the gate files named by its dependencies. Gate closeout rechecks this index
and every directly affected gate boundary. A future gate file is created with its first substantive
design task; there are no empty speculative design files.

| Gate | Detailed design | Current coverage |
| --- | --- | --- |
| G0 | [Build and architecture](design/G0-build-and-architecture.md) | G0-001 through G0-002 approved |
| G1 | [First map slice](design/G1-first-map-slice.md) | G1-001 approved |
| G2 | [Symbols and vector graphics](design/G2-symbols-and-vector-graphics.md) | G2-001 through G2-007 approved |
| G3 | [Interaction and measurement](design/G3-interaction-and-measurement.md) | G3-001 through G3-004 approved |
| G4 | [Sources and CRS](design/G4-sources-and-crs.md) | G4-001 through G4-004 approved |
| G5 | [Read-only shapefile support](design/G5-shapefile-support.md) | G5-001 through G5-010 approved |
| G6 | [PNG/JPEG raster support](design/G6-image-raster-support.md) | G6-001 through G6-005 approved |
| G7 | [Performance and indexing](design/G7-performance-and-indexing.md) | G7-001 through G7-004 approved |
| G8 | [Native and release readiness](design/G8-release-readiness.md) | G8-001 through G8-004 approved |
| G9 | [Elevation and DTED](design/G9-elevation-and-dted.md) | G9-001 through G9-008 and holistic closeout approved |

The linked files are authoritative for their detailed contracts. Moving text between these files is
organizational only unless the same change explicitly records a new decision and task trace update.

## Decisions

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-07-11 | Use `mundane-java-map` and `io.github.mundanej.map`. | Align with the existing MundaneJ family. |
| 2026-07-11 | Use Java 21 and Gradle 9.5.1 Groovy DSL. | Match the existing project baseline. |
| 2026-07-11 | Use BSD 3-Clause. | Match the existing project family. |
| 2026-07-11 | Use Swing/Java2D initially. | Smallest JDK-only desktop path. |
| 2026-07-11 | Keep Level 1 production modules JDK-only. | Preserve portability and native-image friendliness. |
| 2026-07-11 | Add format modules only with working behavior. | Avoid empty or speculative modules. |
| 2026-07-11 | Keep Native Image outside the default gate. | Native tooling is optional for normal development. |
| 2026-07-12 | Keep Java 21 bytecode fixed across CI launcher JDKs. | A newer build JDK should test compatibility, not silently raise the consumer baseline. |
| 2026-07-12 | Make an explicit offline repository the sole resolution source. | Offline evidence must not succeed through hidden public or machine-local fallback. |
| 2026-07-12 | Enforce Level 1 architecture with explicit allowlists in the normal gate. | Fast dependency, bytecode, and narrow source/resource checks prevent boundaries from becoming advisory. |
| 2026-07-12 | Verify the existing first map slice without replacing its contracts. | Deeper event, geometry, viewport, rendering, example, and native evidence should precede new abstractions. |
| 2026-07-12 | Replace geometry-dependent `FeatureStyle` with role-specific symbols during `0.x`. | One explicit toolkit-neutral portrayal model is simpler and more extensible than parallel legacy and symbol state. |
| 2026-07-12 | Use logical screen pixels or projected map units for Level 1 symbol measurements. | The two explicit units cover stable UI marks and zoom-scaled cartography without implying geographic distance. |
| 2026-07-12 | Store vector paths as packed opcodes and ordinates with fixed even-odd fill. | The complete Level 1 command set stays toolkit-neutral, compact, deterministic, and directly convertible to Java2D. |
| 2026-07-12 | Compute marker placement as a toolkit-neutral affine result before AWT painting. | One tested transform order keeps anchors, units, offsets, and rotation identical across current and future renderers. |
| 2026-07-12 | Generate hatch lines only over the clipped screen extent with an explicit per-feature budget. | A simple packed lattice plus Java2D polygon clip preserves holes while bounding allocation and work. |
| 2026-07-12 | Register AWT symbol renderers by explicit role/key in an instance-owned immutable registry. | Consumers can extend rendering without reflection, discovery, toolkit leakage, or global mutable state. |
| 2026-07-12 | Verify rendering through invariant-based headless scenarios in a separate lane. | Tolerant bounds, regions, and ordering catch material regressions without promising cross-platform pixel identity or burdening the normal gate. |
| 2026-07-12 | Prove native symbol resources with one exact test-owned raw RGBA file and Java 21 resource metadata. | A fixed literal lookup and exact inclusion rule exercise the native boundary without inventing a production loader, decoder, or discovery mechanism. |
| 2026-07-12 | Route toolkit-neutral tool events through one session-aware core state machine before AWT defaults. | Explicit capture, cancellation, quarantine, and cursor ownership preserve navigation while preventing stale gestures from crossing tool lifetimes. |
| 2026-07-12 | Use screen-space analytic predicates and renderer-owned hit methods for visible symbol footprints. | Sharing placement and paint traversal keeps hit order deterministic while allowing explicitly registered custom renderers to opt in without toolkit leakage or guessed bounds. |
| 2026-07-12 | Keep hover/selection transactions in MapView and report paint presence from the existing render result. | Full invalidation and in-pass presence avoid a speculative core state machine, duplicate bounds traversal, and presentation-dependent identity state before G7 evidence. |
| 2026-07-12 | Bind Level 1 distance strategies to an exact map CRS and keep measurement state in one uncaptured tool. | Reusing metre/degree CRS declarations, semantic command routing, and a concrete AWT paint pass avoids duplicate unit, input, overlay, and state frameworks. |
| 2026-07-12 | Use synchronous feature cursors and raster reads that return unstyled, independently owned values. | One explicit pull boundary preserves source order, resource ownership, and parser/presentation separation without streams, reactive APIs, or background work. |
| 2026-07-12 | Use bounded warning reports and unchecked structured terminal source failures. | Stable diagnostics, typed limits, and cooperative cancellation stay observable through cursors and Swing rendering without format exceptions or checked-failure adapters leaking across modules. |
| 2026-07-12 | Expose latest per-layer source reports and make MapView explicitly closeable. | Deferred EDT report delivery and permanent explicit close make asynchronous paint failures and transferred source ownership observable without treating reversible Swing detachment as disposal. |
| 2026-07-12 | Model map, source, and display CRSs explicitly and resolve only registered direct operations. | Endpoint definitions and an instance-owned registry preserve current pointer ergonomics while preventing guessed CRS identity, hidden chaining, or discovery. |
| 2026-07-12 | Reject out-of-domain Web Mercator coordinates and make query-envelope clipping an explicit result. | Strict feature transforms expose invalid data, while bounded viewport intersection remains usable without silent latitude repair or unsafe generic corner projection. |
| 2026-07-12 | Require Level 1 raster sources to match the display CRS. | Painting a cross-CRS raster as one transformed rectangle would masquerade as nonlinear warping that G4/G6 do not implement. |
| 2026-07-12 | Compose legacy and lazy source content through one explicit AWT binding. | A source must keep bounded cursor, CRS, cancellation, report, and ownership behavior instead of masquerading as the existing snapshot `Layer`. |
| 2026-07-12 | Atomically arbitrate staged source success, known failure, and cancellation before publication. | Exactly one terminal outcome owns payload, availability, and report publication, so a cross-thread cancel cannot produce success-after-cancel or two terminal diagnostics. |
| 2026-07-12 | Keep a concise project design index and gate-oriented detailed design files. | Scoped reading and review remain fast while one entry point preserves cross-gate principles, decisions, and traceability. |
| 2026-07-12 | Bound Level 1 shapefiles to explicit 2D SHP/SHX/DBF/CPG/PRJ behavior and reject Z/M reduction. | A fixed geometry, sidecar, encoding, limit, diagnostic, and recovery profile is useful for ordinary maps without becoming an implicit general GIS compatibility promise. |
| 2026-07-12 | Open shapefiles through one static format facade backed by a private positional source. | Returning `FeatureSource` keeps parser/channel types private while an explicit opening token, options, and owned channel make the first real file slice cancellable, bounded, and testable. |
| 2026-07-12 | Treat SHX as a fully prevalidated packed address table with sequential fallback. | One private long per physical record improves deterministic addressing without exposing random access, pretending to be a spatial index, or trusting a partial sidecar. |
| 2026-07-12 | Classify shapefile rings with one bounded structural algorithm and packed operation state. | Exact orientation, strict hole association, up-front allocation reservation, and terminal ambiguity preserve useful polygons without implying repair or a general topology engine. |
| 2026-07-12 | Align DBF rows to physical SHP ordinals and resolve text through one finite format-local encoding profile. | Positional pairing, selected-field decoding, and explicit hints preserve source identity and bounded work without locale, provider, or schema-discovery behavior. |
| 2026-07-12 | Retain exact bounded PRJ text while recognizing only two fixed WKT1 trees. | Format-local structural matching can provide useful canonical CRS metadata without a general WKT model, authority lookup, heuristic CRS guess, or registry alias leak. |
| 2026-07-12 | Harden shapefile input with exact hostile fixtures plus bounded deterministic public-opener mutation tests. | Targeted cases remain the diagnostic oracle while reproducible breadth tests expose unchecked parser paths without a production fuzzer, random CI behavior, or second format implementation. |
| 2026-07-12 | Keep a small licensed shapefile interoperability corpus in a separate offline verification lane. | Checksummed curated artifacts and exact public outcomes add real-tool evidence without weakening unit-test precision, slowing the normal gate, or making builds depend on downloads. |
| 2026-07-12 | Extend one explicit native smoke with a checksummed corpus-derived shapefile subset and one malformed record. | A literal resource inventory and the real Path/source/AWT stack prove reachability and parity without embedding the corpus, scanning resources, adding native parser code, or making a performance claim. |
| 2026-07-12 | Decode bounded PNG/JPEG through an explicit toolkit-neutral registry and one AWT ImageIO adapter. | A fixed header profile and source-owned accounting keep the format module AWT-free; application decoder choice remains explicit while G0 narrowly acknowledges the standard JDK ImageIO registry's opaque initialization. |
| 2026-07-12 | Retain world-file placement as an invertible pixel-center affine transform. | A tagged axis/affine placement preserves exact G4 grid behavior while true parallelogram planning and drawing avoid guessed CRS, envelope stretching, or a general warp model. |
| 2026-07-12 | Resample raster requests once with exact project-owned nearest/bilinear math and keep opacity in AWT presentation state. | Window-local integer filtering, screen-density planning, and immutable render options avoid codec-dependent correctness, double filtering, or cache-key pollution. |
| 2026-07-12 | Harden image input with complete bounded container validation and one versioned per-source result cache. | Exact snapshots/digests, serialized lifecycle, and fresh consumer buffers provide deterministic reuse without multiple cache layers, ambient refresh, or pre-evidence performance claims. |
| 2026-07-12 | Extend the one explicit native smoke with fixed PNG/JPEG affine resources and one malformed PNG. | Shared JVM/native assertions and literal reachability metadata prove the real codec, placement, cache, rendering, diagnostic, and cleanup path without discovery, a second executable, or a cross-platform claim. |
| 2026-07-12 | Establish one deterministic real-stack evidence lane before any G7 optimization. | Fixed fixtures, semantic oracles, raw timings, environment metadata, and optional JDK JFR can locate work without adding a benchmark framework or making portable wall-clock claims. |
| 2026-07-12 | Offer one explicit packed STR index for immutable in-memory feature sources. | A fixed-fanout primitive tree and source-order query plan reduce bounded viewport candidates without changing FeatureSource, indexing shapefiles, exposing tree nodes, or selecting a strategy implicitly. |
| 2026-07-12 | Optimize only operation-local screen paths while retaining authoritative geometry. | Fixed line clipping/simplification and conservative polygon validation reduce paint work without changing hit, endpoint, fit, query, topology, or cache semantics. |
| 2026-07-12 | Admit only evidence-qualified private AWT render-cache partitions and keep G6 as the sole raster-pixel cache. | Predeclared same-run rules can justify screen-plan or vector-template retention without a public cache framework, duplicate image storage, runtime timing policy, or complexity that failed to pay for itself. |
| 2026-07-12 | Close native verification with one aggregate executable and one pinned Ubuntu 24.04 x86_64 GraalVM Java 21 lane. | Direct scenarios, an exact resource inventory, retained logs, and narrow platform wording provide release evidence without a second binary, broad reachability metadata, or a cross-platform claim. |
| 2026-07-12 | Review the five published Level 1 modules and five examples without adding permanent API-governance machinery. | Generated Javadoc indexes, strict offline docs, explicit compatibility dispositions, headless tests, and one manual checkpoint are exhaustive enough before a first release. |
| 2026-07-12 | Stage one exact five-artifact Maven contract and consume it from one standalone offline Java 21 build. | Ordering the real publish tasks, verifying artifacts/checksums/licenses, and resolving only staged coordinates proves downstream usability without a BOM, release plugin, Maven CLI, or remote repository. |
| 2026-07-12 | Reconcile Level 1 release evidence against one immutable candidate SHA and keep any later record update distinct. | A named candidate, independent lane provenance, and narrow evidence-only status commit avoid mixing revisions or making an impossible self-referential release record. |
| 2026-07-12 | Model elevations as a distinct synchronous sampled source with post-center bounds and an explicit no-data mask. | Numeric terrain remains reusable by DTED and GeoTIFF without pretending that sample posts are raster pixels or adding a generic source hierarchy. |
| 2026-07-12 | Render elevation through one stateless colorization/hillshade operation and a direct AWT binding. | Reusing raster requests, accounting, resampling, pixels, options, and report/lifecycle behavior provides a real layer without disguising numeric terrain as a `RasterSource`. |
| 2026-07-12 | Decode the first DTED slice eagerly through one format facade into `PackedElevationGrid`. | Completing and closing bounded file I/O before publication preserves failure-free sample access and leaves lazy/windowed access to measured evidence. |
| 2026-07-12 | Harden the strict DTED profile with prospective format limits and terminal exact diagnostics. | Complete field grammar, mandatory checksums, deterministic precedence, and bounded mutation evidence make hostile input predictable without repair, profile expansion, or a parser framework. |
| 2026-07-12 | Query elevation through one explicit source-CRS core operation with nearest or bilinear policy. | Exact post-coordinate search, unit-bearing values, and deliberate ordinary absence provide deterministic reuse without parser branches, implicit transforms, or a query framework. |
| 2026-07-12 | Build DTED corpus evidence from approved GDAL-generated synthetic cells in a separate offline lane. | An independent writer plus exact provenance, hashes, licenses, and public outcomes adds compatibility evidence without ambiguous terrain rights, runtime downloads, or production dependencies. |
| 2026-07-13 | Judge eager DTED access with exact logical storage and real-stack evidence before considering another source shape. | A largest-standard-cell run, environment-labelled JVM observations, and an analytical profile-cache model expose the tradeoff without implementing a speculative second parser or portable timing gate. |
| 2026-07-13 | Extend the one native executable with one approved Level 0 DTED resource and shared JVM/native assertions. | A literal resource, public read/query/render path, and one exact malformed outcome prove representative reachability without a second native framework, full corpus, or platform overclaim. |

## Task design traceability

Design status is independent of implementation task status. `Draft` is ready for review, `Reviewed`
has completed independent review, and `Approved` is the committed top- and mid-level design baseline.
Implementation tasks remain Proposed until their code, tests, and task-specific evidence are complete.

| Task | Design coverage | Status |
| --- | --- | --- |
| G0-001 | Java baseline, repository resolution, normal verification, and publication staging | Approved |
| G0-002 | Module graph, architecture enforcement, prohibited mechanisms, and exception policy | Approved |
| G1-001 | First-slice geometry, viewport, rendering, interaction, example, and native verification | Approved |
| G2-001 | Symbol roles, renderer keys, placement units, transforms, composition, and style migration | Approved |
| G2-002 | Packed vector paths, normalized built-in markers, Java2D conversion, and first render slice | Approved |
| G2-003 | Immutable placement/stroke values, core marker transforms, AWT painting, and composites | Approved |
| G2-004 | Solid line/fill values, endpoint tangents, bounded hatch layout, clipping, and migration | Approved |
| G2-005 | Bounded raster icons, immutable named catalogs, explicit AWT registry, and custom rendering | Approved |
| G2-006 | Runnable symbol gallery, named visual checkpoint, portable render scenarios, and separate lane | Approved |
| G2-007 | Exact icon resource metadata, shared native symbol smoke, stable probes, and G2 closeout | Approved |
| G3-001 | Toolkit-neutral tool events/context, session router, capture/quarantine, and AWT navigation order | Approved |
| G3-002 | Screen-space geometry predicates, renderer-owned symbol footprints, deterministic topmost hits, and single selection | Approved |
| G3-003 | Immutable interaction events, hover probes, overlay symbols, logical paint presence, full invalidation, and ordered rendering | Approved |
| G3-004 | CRS-bound metre results, planar/great-circle strategies, semantic undo/user cancel, tool-owned state, concrete AWT overlay, and G3 closeout | Approved |
| G4-001 | Synchronous feature/raster contracts, immutable records/IDs, canonical attributes, raster grid math, limits, cancellation, reports, and explicit ownership | Approved |
| G4-002 | Immutable CRS metadata, explicit direct-operation registry, strict Web Mercator/envelopes, display integration, and same-CRS raster boundary | Approved |
| G4-003 | Common/feature source contracts, packed multipart geometry, linear in-memory queries, explicit AWT bindings, staged CRS-safe rendering, interaction, reports, and ownership | Approved |
| G4-004 | Raster contracts/accounting, exact visible cell windows, procedural nearest source, matching-CRS binding, direct AWT conversion, atomic publication, and G4 closeout | Approved |
| G5-001 | Exact 2D shapes, sidecar/recovery rules, DBF schema/value profile, encodings, PRJ recognition, limits, diagnostics, lifecycle, and fixtures | Approved |
| G5-002 | Static opener/options/limits, component snapshot, bounded positional SHP null/point/multipoint source, module/publication wiring, viewer, and lifecycle evidence | Approved |
| G5-003 | Packed SHX preflight, exact SHP cross-check, missing/ignored fallback, indexed cursor equivalence, and lifecycle evidence | Approved |
| G5-004 | Shared bounded multipart decoding, exact PolyLine validation, packed singular/multipart mapping, and viewer evidence | Approved |
| G5-005 | Shape-5 activation, bounded ring validation/association, honest worst-case allocation, stable polygon mapping, and viewer evidence | Approved |
| G5-006 | Packed DBF schema plan, finite CPG/LDID encoding resolution, positional row alignment, selected typed decoding, and viewer evidence | Approved |
| G5-007 | Strict bounded PRJ retention, packed WKT1 syntax validation, exact Level 1 CRS recognition, override arbitration, and viewer evidence | Approved |
| G5-008 | Cross-component hostile fixtures, uniform bound/precedence audit, deterministic public-opener mutation harness, stable outcome oracles, and cleanup evidence | Approved |
| G5-009 | Licensed checksummed corpus, exact interoperability oracles, isolated corpus lane, complete viewer observability, and maintainer review | Approved |
| G5-010 | Explicit native shapefile resources, JVM/native semantic parity, valid query/render and malformed diagnostic paths, lifecycle evidence, and G5 closeout | Approved |
| G6-001 | Bounded PNG/JPEG profile, source-owned decode reservation, explicit registry/context, narrow ImageIO qualification, lifecycle, viewer, and publication wiring | Approved |
| G6-002 | Exact sidecar snapshot/grammar, pixel-center affine placement/inverse, conservative window planning, true AWT parallelogram drawing, and viewer evidence | Approved |
| G6-003 | Source-compatible interpolation requests/context, exact core resampling, bounded ImageIO hints, affine output planning, immutable opacity/options, and render evidence | Approved |
| G6-004 | Full PNG/JPEG validation, exact content snapshots/versioning, one bounded source result cache, serialized read/close lifecycle, and hostile-input evidence | Approved |
| G6-005 | Fixed native raster resources, explicit codecs, shared semantic/diagnostic scenario, exact reachability metadata, lifecycle evidence, and G6 closeout | Approved |
| G7-001 | One checked support module, six fixed fixture families, twelve real-stack scenarios, deterministic report/statistics, optional JFR workflow, and separate CI evidence | Approved |
| G7-002 | Explicit indexed in-memory factory, bounded packed STR-16 arrays/build, source-ordered query plan/accounting, linear equivalence, and comparison evidence | Approved |
| G7-003 | Operation-local packed screen plans, deterministic line clipping/simplification, conservative polygon optimization/fallback, authoritative hit/endpoint semantics, and paired evidence | Approved |
| G7-004 | Evidence-gated private screen-plan/vector-template candidates, exact ownership/keys/budgets/LRU, append-only comparisons, performance acceptance, and G7 closeout | Approved |
| G8-001 | One aggregate native scenario, exact diagnostics/resources, final measurement/render path, pinned Linux CI evidence, and release-lane checkpoint | Approved |
| G8-002 | Five-module public-surface inventory, compatibility dispositions, strict Javadocs/package docs, source-accurate examples, and manual release approval | Approved |
| G8-003 | Five-artifact publication contract, real-task staging order, deterministic verifier, standalone offline consumer, and combined release lane | Approved |
| G8-004 | Candidate identity/version, independent evidence matrix, license/artifact/support reconciliation, go/no-go record, and holistic Level 1 closeout | Approved |
| G9-001 | Elevation source/metadata/limits, post-center grid convention, units, packed finite samples, no-data mask, diagnostics, and lifecycle | Approved |
| G9-002 | Immutable color ramps/hillshade style, post-support planning, bounded core rasterization, direct AWT binding, synthetic viewer, and render evidence | Approved |
| G9-003 | DTED facade/options, strict Level 0/1/2 profile, eager fixed-record decoding, signed-magnitude/no-data mapping, publication extension, and fixtures | Approved |
| G9-004 | Typed DTED limits, complete fixed-field grammar, checksum/sample enforcement, exact diagnostic precedence, hostile fixtures, and deterministic mutation evidence | Approved |
| G9-005 | Explicit source-CRS query boundary, exact post search, nearest/bilinear/no-data policy, unit-bearing results, lifecycle, and DTED integration evidence | Approved |
| G9-006 | Approved GDAL-generated Level 0/1/2 corpus, manifest/licenses/recipe, exact public oracle, isolated offline lane, and publication exclusion | Approved |
| G9-007 | Four append-only DTED evidence scenarios, exact logical memory accounting, analytical profile-cache comparison, and a deterministic eager/windowed decision rubric | Approved |
| G9-008 | One explicit Level 0 native fixture, public read/query/colorize/hillshade/render path, exact malformed diagnostic, Linux evidence, and G9 simplicity closeout | Approved |
