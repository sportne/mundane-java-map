# Roadmap

The roadmap is organized as capability gates. Every gate must leave observable, tested behavior;
the detailed dependency graph and status source of truth is the [task index](tasks/README.md).

## Current evidence

The repository contains source for a real initial map slice: immutable point, line, and polygon
features; packed coordinates; Web Mercator and viewport math; Swing rendering; pan, zoom,
fit-to-data, pointer-coordinate events; a basic viewer; architecture tests; and an offscreen native
smoke entrypoint.

G0 and G1 are complete. G0-001 verifies the fixed Java 21 artifact baseline, strict root/included-build
repository modes, normal JVM gate, and exact three-module publication staging. G0-002 now enforces
the authoritative runtime graph, module/toolkit/I/O direction, public API isolation, resource
discovery restrictions, and prohibited native-targeted mechanisms with positive and negative
evidence. G1 now directly verifies geometry invariants, isolated rendering and holes, repeated-paint
clearing, installed navigation and pointer routing, listener mutation semantics, the example, and the
EDT-safe smoke. The Linux x86-64 GraalVM CE Java 21.0.2 lane builds and runs the real offscreen image.
G2-001 is also complete: its approved design fixes the toolkit-neutral symbol roles, renderer
identity, placement units and transforms, composition rules, diagnostics, and pre-1.0
`FeatureStyle` migration. Production symbol work begins with G2-002.
G2-002 now delivers that first production slice: packed toolkit-neutral paths, explicit marker
dispatch and diagnostics, eight normalized core markers, Java2D conversion, real-stack rendering,
and the temporary legacy-style compatibility path.
G2-003 adds immutable placement, sizing, rotation, stroke, and homogeneous composition contracts;
toolkit-neutral transform math; and ordered, opacity-aware AWT marker rendering with isolated
graphics state. Line, fill, raster-icon, and registration behavior remains in later G2 slices.
G2-004 completes working marker, line, and fill roles with solid lines, outward-oriented endpoint
markers, even-odd solid fills, optional outlines, and allocation-bounded diagonal hatch layouts.
The basic viewer now uses these symbols directly; the deprecated style branch remains separately
verified during the Level 1 migration window.
G2-005 adds bounded toolkit-neutral raster icons, immutable exact-name catalogs, and one explicit,
instance-owned AWT renderer registry. Built-in and consumer renderers now share a validated
paint-scoped context, same-role recursion, stable diagnostics, and derived endpoint/outline behavior
without discovery or global mutable registration.
G2-006 adds the runnable four-section symbol gallery and an independent, headless
`renderRegression` lane. Its fixed tolerant invariants cover every marker and placement mode,
composition, raster interpolation, directional endpoints, polygon holes, and bounded hatches without
platform-specific golden images. The maintainer approved the gallery after desktop pan/zoom review;
its arrow examples use head-only endpoint vectors to avoid double-antialiasing seams.
G2-007 completes the gate with one explicitly declared raw icon resource and a shared JVM/native
scenario that renders a curved vector path, ordered composite, and raster icon through `MapView`.
Focused architecture checks include the native support classes and resources without broadening the
production-module inventory. Linux x86-64 GraalVM CE Java 21.0.2 built and ran the executable; this
evidence makes no Windows or macOS Native Image claim.
G4-001 is complete: the maintainer-approved format-neutral source profile fixes synchronous bounded
feature/raster pull contracts, explicit ownership and close behavior, canonical immutable attributes,
typed limits, cancellation checkpoints, strict raster windows, and stable structured diagnostics.
The approved signatures began as test-only sketches. G4-003 supplies the common and feature-side
production contracts, and G4-004 supplies the raster-specific contracts and working rendering slice.
G4-002 is complete: CRS metadata now distinguishes recognized, unknown, and missing states; an
instance-owned registry explicitly resolves the approved EPSG:4326/EPSG:3857 keys and direct
operations; and strict projection, envelope, viewport, view, and optional pointer boundaries replace
silent Web Mercator clamping.
G3-001 is complete: immutable toolkit-neutral tool events feed one core lifecycle and capture router;
the AWT adapter explicitly converts local input, preserves passed pan/zoom and compatibility
observers, owns predefined cursor mapping, and cancels or quarantines stale gestures at view
lifecycle boundaries.
G3-002 is complete: immutable toolkit-neutral hit/selection identities and robust core screen
predicates feed explicit AWT renderer hit callbacks; hit traversal mirrors reverse paint order and
single primary clicks update stable-ID selection before compatibility observers run.
G3-003 is complete: stable hover/selection events use one mutation-safe FIFO; built-in renderers
report logical source-paint presence and MapView paints configurable, non-destructive hover then
selection overlays after source content while lifecycle changes conservatively invalidate hover.
G3-004 is complete: explicit CRS-bound metre strategies drive a packed immutable measurement state,
bounded command routing, one AWT-owned click measurement tool and clipped final overlay, plus a
runnable planar/geographic measurement example.
G4-003 is complete: canonical immutable feature records, typed limits, cancellation and structured
diagnostics feed a one-cursor in-memory source; packed multipoint/multipart geometry renders and hits
through explicit owned or borrowed AWT bindings with bounded viewport queries, CRS preflight,
source reports, stable interaction identity, and deterministic teardown.
G4-004 completes the gate with strict immutable raster windows and packed RGBA buffers, checked
request accounting, exact grid-edge visibility planning, and an allocation-free-at-open synthetic
source. Matching recognized-CRS rasters render through explicit owned or borrowed AWT bindings with
bounded direct conversion, atomic cancellation/report publication, fixed nearest scaling, and no
decoder, worker, cache, or warp abstraction. The gate-level design review found no additional
abstraction necessary: feature cursors and all-or-nothing raster reads remain separate synchronous
contracts sharing only the approved diagnostics, cancellation, limits, and ownership foundations.
G5-001 is complete: the maintainer-approved read-only profile accepts only null, point, multipoint,
polyline, and polygon records; rejects implicit Z/M reduction; fixes finite SHP/SHX/DBF/CPG/PRJ,
encoding, limit, diagnostic, lifecycle, and fixture policies; and leaves the first production module
to the working G5-002 vertical slice.
G5-002 is complete: the new JDK-only format module positionally reads bounded null, Point, and
MultiPoint SHP records through the format-neutral source/cursor contracts, stages all not-yet-owned
sidecars, and renders a supplied file in the explicit-CRS shapefile viewer. Parser internals remain
package-private and AWT-free; later G5 slices retain ownership of DBF/CPG, PRJ, multipart lines and
polygons, corpus/fuzz hardening, and Native Image evidence.
G5-003 is complete: a validated SHX sidecar becomes one immutable packed physical-address table;
missing or rejected indexes emit a stable opening warning and fall back wholly to the sequential
oracle, while indexed cursors revalidate SHP framing and never expose random or spatial access.
G5-004 is complete: bounded type-3 PolyLine records map to immutable singular or packed multipart
line geometry after exact prefix, count, size, allocation, part-table, coordinate, and bounds
validation. Sequential and SHX-backed paths share the decoder, filtered records remain fully
validated, and the viewer renders every part without introducing a bridge.
G5-005 is complete: bounded type-5 Polygon records use fixed packed exact-binary predicates to retain
source-stable shells, assign holes to the smallest strict containing shell, and reject ambiguous
topology without repair. Singular and packed multipart output preserves component-local even-odd
rendering for holes, disjoint shells, and nested islands under cumulative allocation and topology
limits.
G5-006 is complete: bounded positional DBF reads pair each physical attribute row with its SHP
ordinal, suppress deleted partners, and expose immutable typed attributes in requested order. The
memo-free dBASE III/IV/5 profile uses explicit caller/CPG/LDID/fallback encoding resolution, committed
single-byte tables, stable row/field diagnostics, prospective limits, cancellation, and reverse-order
cleanup; the viewer proves a non-ASCII attribute and deleted-row suppression without introducing AWT
or provider discovery in the format module.
G5-007 is complete: a strict bounded UTF-8 PRJ reader retains exact input after an optional BOM, one
fixed-capacity WKT1 tokenizer validates the approved grammar, and two direct structural matchers alone
recognize EPSG:4326/EPSG:3857. Missing, blank, unknown, invalid, equal/conflicting override, limit,
cancellation, and cleanup outcomes remain distinct and structured; the PRJ-aware viewer exercises
recognized geographic/projected paths without guessing, discovery, or a general WKT abstraction.
G5-008 is complete: every parser limit now has inclusive boundary evidence with component-accurate
locations, repeated zero-progress I/O and caller-raised sidecar ceilings terminate predictably, and
sidecar CPU work remains cancellable. Five committed seeds drive 256 bounded, replayable public-path
cases twice with exact outcome allowlists and clean-state sentinels; architecture tests also reject
memory mapping and any production mutation helper. This remains normal-gate evidence, not the
real-world corpus lane owned by G5-009.

## Level 1

Level 1 keeps production runtime modules JDK-only, uses Java 21, confines Swing and Java2D to
`mundane-map-awt`, adds format modules only with working behavior, and treats Native Image as an
architectural requirement. Level 1 is complete only when
[G8-004](tasks/G8-004-level1-release-readiness.md) is complete.

### G0 — Verified baseline

- Restore and verify the Java 21 Gradle baseline and publication staging in
  [G0-001](tasks/G0-001-current-baseline-verification.md).
- Mechanically enforce dependency, toolkit, I/O, and native-target boundaries in
  [G0-002](tasks/G0-002-architecture-boundary-hardening.md).

### G1 — First map slice

- Verify and harden the existing geometry, viewport, Swing interaction/rendering, viewer, and
  native-smoke slice in [G1-001](tasks/G1-001-first-map-slice-verification.md).

### G2 — Symbols and vector graphics

- Add immutable marker, line, fill, and composite symbol contracts.
- Add toolkit-neutral move/line/quadratic/cubic/close paths and Java2D rendering.
- Cover built-in vector markers, placement, rotation, size units, line endpoints, arrowheads,
  hatches, raster icons, immutable catalogs, and explicit renderer registration.
- Finish with the symbol gallery, tolerant rendering-regression lane, and explicit native resource
  smoke in the [G2 task set](tasks/README.md#g2--symbols-and-vector-graphics).

### G3 — Interaction and measurement

- Add explicit tool lifecycle and navigation routing.
- Add symbol-aware hit testing, deterministic selection, hover, and visual feedback.
- Add planar and recognized-geographic distance strategies plus an interactive measurement tool in
  the [G3 task set](tasks/README.md#g3--interaction-and-measurement).

### G4 — Source contracts and CRS boundaries

- Define bounded, closeable feature/raster source contracts, metadata, queries, cancellation,
  limits, immutable attributes, and structured diagnostics.
- Add explicit CRS metadata/registration and harden projection boundaries.
- Prove feature and raster contracts through real in-memory/synthetic rendering slices in the
  [G4 task set](tasks/README.md#g4--source-contracts-and-crs-boundaries).

### G5 — Read-only shapefile support

- Use the approved [bounded 2D shapefile profile](design/G5-shapefile-support.md): null, point,
  multipoint, polyline, and polygon only, with no implicit Z/M reduction.
- Add `mundane-map-io-shapefile` only with a working SHP point/multipoint slice and viewer.
- Grow through SHX, multipart polylines, polygons/holes, DBF, CPG, and retained/recognized PRJ.
- Bound records, parts, points, fields, and allocations; cover malformed/hostile inputs,
  deterministic fuzzing, a legally redistributable corpus, stable diagnostics, and Native Image.
- G5-001 through G5-010 are implemented and verified: the source supports the bounded profile and
  the separate offline corpus lane covers nine licensed curated/generated datasets while the viewer
  exposes bounded generic previews and stable reports. The fixed valid/malformed resource inventory
  passes the shared JVM and Linux Java 21 Native Image semantic/render path. This is Linux evidence;
  no Windows/macOS or broader cross-platform Native Image claim follows from it.
- The supported profile and all vertical slices are in the
  [G5 task set](tasks/README.md#g5--read-only-shapefile-support).

### G6 — Bounded PNG/JPEG raster support

- Add an AWT-free `mundane-map-io-image` source with an explicitly registered `ImageIO` decoder
  confined to `mundane-map-awt`.
- G6-001 is implemented and verified: fixed-profile PNG/JPEG probing, explicit axis-aligned or
  unplaced metadata, bounded owned-channel reads, immutable decoder registration, strict-window
  nearest conversion, fixture evidence, the normalized non-georeferenced viewer, architecture
  enforcement, and publication staging all use the real G4 raster path.
- G6-002 is implemented and verified: strict bounded world-file snapshots, immutable pixel-center
  affine transforms, placement-derived half-pixel envelopes, conservative inverse viewport windows,
  caller-declared CRS retention, and true-parallelogram Java2D painting extend that same raster path.
  Sidecars are explicit and finite; no directory scanning, priority fallback, CRS guessing, or raster
  reprojection is introduced.
- G6-003 is implemented and verified: source-compatible nearest/bilinear requests, exact integer
  resampling, strict ImageIO region/subsampling hints, screen-density output planning, immutable
  view-owned interpolation/opacity, affine presentation, viewer controls, and tolerant rendering
  evidence use one bounded request/draw path.
- G6-004 is implemented and verified: complete AWT-free PNG/JPEG physical validation, exact
  SHA-256 source versions and operation snapshots, serialized read/close lifecycle, and one private
  source-owned successful-LRU RGBA cache enforce deterministic limits, invalidation, cancellation,
  fresh ownership, and hostile-input diagnostics.
- G6-005 is implemented and verified: the existing no-fallback Linux Java 21 Native Image
  executable uses one explicit PNG/JPEG decoder registry, five literal checked raster resources,
  separate fixed workspaces, exact affine/cache/cancellation/diagnostic assertions, and tolerant
  offscreen rendering while preserving the G2/G5 scenarios. Narrow JDK AWT/ImageIO JNI metadata is
  pinned by architecture tests; application and production code use no JNI or discovery. G6 is
  complete, with no Windows/macOS claim and no performance claim before G7 evidence.
- Support explicit bounds, world files, affine georeferencing, window requests, opacity,
  interpolation, bounded caches, lifecycle/cancellation, hostile inputs, a runnable viewer, and a
  real Native Image decode/render path.
- See the [G6 task set](tasks/README.md#g6--bounded-pngjpeg-raster-support).

### G7 — Performance evidence and optimization

- G7-001 is implemented and verified: one support-only module owns six deterministic fixture
  families, twelve real-stack scenarios, independently derived SMOKE and BASELINE semantic oracles,
  canonical typed digests, structured JSON/Markdown evidence, and an optional same-toolchain JFR
  workflow. Ordinary tests execute SMOKE only; the full BASELINE derivation and timing run remain in
  the separate `performanceEvidence` lane. The checked interpretation identifies in-memory query,
  render, hit-test, and shapefile stages for the next evidence-driven tasks without defining portable
  timing thresholds.
- G7-002 is implemented and verified: callers explicitly select a bounded packed STR-16 index on the
  immutable in-memory source, while the original linear factory remains unchanged. Cursor-owned
  candidate plans preserve source order, exact attributes/diagnostics/accounting, cancellation, and
  one-live-cursor lifecycle. Independent fixed-fixture candidate totals are checked against untimed
  production inference before evidence is reported; 22 appended build/query/real-stack rows retain
  the G7-001 semantic oracles. The canonical BASELINE evidence completed under the fixed 512 MiB heap
  with observed crossover 32; this remains descriptive and creates no automatic selection policy.
- G7-003 is implemented and verified: one JDK-only core optimizer performs deterministic screen-space
  line clipping/simplification and conservative polygon optimization with exact whole-result
  fallback. AWT retains one packed operation-local plan for eligible built-in symbol trees while
  endpoints, hit/hover/selection, source publication, and custom renderers remain authoritative. The
  canonical 39-scenario BASELINE run reduced exact rendered-coordinate work in every optimized pair,
  but median/p95 timings were slower on this host; those results are descriptive evidence for G7-004,
  not an automatic cache, tuning switch, or performance claim.
- G7-004 moves both evidence Java processes, their ordered staged runtime classpaths, and generated
  fixture workspaces into invocation-unique native `/tmp` trees before timing, then safely returns only
  completed reports to the project build tree. The separate `performanceQuick` lane runs the full
  reduced-cardinality SMOKE scenario set with fixed one/two iterations for an under-five-minute local
  loop; it is always investigative and `NOT_EVALUATED`, remains outside every other lane, and cannot
  replace the full BASELINE evidence used for one-time cache decisions.
- G7-004 is implemented and verified. The canonical `/tmp`-native BASELINE run completed in 2m15s.
  Warm screen-plan pan produced zero hits and 6,104 evictions, so that candidate was removed in full.
  The vector-template candidate reduced the unchanged symbol median from 21.68 ms to 18.69 ms cold
  and 18.39 ms warm, with nine retained entries/4,869 logical bytes and zero eviction/bypass, so the
  private AWT partition is retained at 512 entries, 4 MiB total, and 256 KiB per entry. No cache API,
  tuning switch, raster duplication, or native acceleration was added. G7 is complete.
- Do not add a custom native performance library without separate benchmark evidence and a new
  decision. See the [G7 task set](tasks/README.md#g7--performance-evidence-and-optimization).

### G8 — Native and release readiness

- Aggregate representative success and diagnostic paths in Linux Native Image CI.
- G8-001's direct aggregate JVM scenario and pinned single-job workflow are implemented locally, but
  the task remains Proposed pending the required reviewed-commit Ubuntu 24.04 CI evidence. A
  supplemental cached GraalVM CE 21.0.2 run compiled the no-fallback image and then failed in the
  newly reached measurement-label font path; no resource or reachability metadata was widened to
  treat that older local toolchain as release evidence.
- G8-002 is complete. The five published modules now have strict offline Java 21 Javadocs and a
  published-only public-API documentation check; their immutable/toolkit/dependency boundaries were
  reviewed without incompatible API changes. README and all five examples describe and exercise the
  observed Level 1 surface. Fixed format-viewer identities, sensitive-path presentation tests, and
  captured WSLg launches cover the release-review checkpoint; final Native Image wording remains
  provisional while G8-001 awaits its required CI evidence.
- G8-003 is complete. One exact five-coordinate contract now serializes the real local-repository
  writes after cleanup, validates snapshot/release POMs, module metadata, reproducible licensed
  archives, strong checksums, and an immutable artifact manifest, then compiles and runs a fresh
  offline Java 21 consumer from only the staged artifacts. This independently executable release
  branch does not satisfy G8-001's pending Linux CI checkpoint.
- G8-001 and G8-003 are independent evidence branches after G8-002. G8-004 explicitly depends on
  both and is the only point that may join publication evidence with the pending native checkpoint.
- G8-004's proposed `0.1.0` support statement, changelog, limitation profile, and local license/
  provenance audit are prepared. The task is Blocked—not complete—because the one authorized final
  push has not yet produced exact-candidate Native Image and normal CI URLs or a six-lane evidence
  matrix on one immutable SHA.
- Run normal, native, shapefile-corpus, rendering-regression, performance, and
  publication/consumer lanes separately before the release decision.
- Reconcile every lane against one clean candidate SHA; G8-004 records `GO` or leaves Level 1
  unclaimed. A later evidence-record commit never changes which artifact revision was tested.
- A successful Ubuntu 24.04 Linux x86_64 GraalVM Java 21 run is required; missing or failing evidence
  blocks Level 1 release. Windows, macOS, Linux AArch64, and other-distribution evidence is required
  before making broader Native Image claims, but does not block the narrowly supported release.

## Level 2 backlog

Level 2 begins after G8-004. A decision-only profile that selects implementation must be followed by
newly decomposed vertical slices; an approved `DEFER` outcome creates none. G10-001 defines its working
first slice; G10-006 is a design-only acquisition profile whose implementation is decomposed into
G10-039 and G10-060 through G10-062. No task creates an empty module, and broader follow-up remains
separately decomposed.

### G9 — Elevation and DTED

- G9-001 is complete under the maintainer's explicit sequencing exception while G8-004 awaits the
  one final push. The toolkit-neutral source, post-center metadata, typed limits/units, packed finite
  samples, structural no-data mask, diagnostics, and lifecycle are implemented without a format
  module or raster inheritance; this does not make the Level 1 release gate complete.
- G9-002 is complete: immutable caller-supplied color ramps, explicit no-data styling, bounded
  deterministic projected/geographic hillshading, post-support planning, sample-domain clipping,
  and nearest/bilinear rendered-color resampling run through direct borrowed/owned AWT elevation
  bindings and the synthetic no-DTED viewer. Elevation remains distinct from `RasterSource`, and no
  derived cache or numeric position-query policy is introduced.
- G9-003 is complete: the published dependency-free `mundane-map-io-dted` module strictly opens the
  approved eager WGS84 one-degree Level 0/1/2 profile, reconciles bounded fixed headers and exact
  latitude-zone dimensions, decodes signed-magnitude samples and declared Level 2 voids into the
  shared elevation source, closes file I/O before publication, and is covered by independent
  generated fixtures, architecture rules, staged artifacts, and a clean Java 21 consumer. This is a
  JVM reader slice only; licensed corpus, performance/lazy-access evidence, and Native Image
  verification remain in G9-006 through G9-008.
- G9-004 is complete: immutable DTED-specific limits precede shared elevation limits and
  prospectively charge all project-owned primitive storage; every reached fixed-header byte is
  classified and cross-checked; exact framing, mandatory checksums, signed-magnitude ranges, void
  policy, final-size races, cleanup, and the closed diagnostic vocabulary are enforced. Boundary and
  truncation tables plus a deterministic bounded 64-case public-facade mutation sequence provide
  hostile-input evidence without adding corpus, query, rendering, performance, or Native Image
  scope.
- G9-005 is complete: the two explicit numeric query modes and finite unit-bearing results remain in
  the API while one stateless core utility asserts the source CRS, validates domain precedence,
  binary-searches exact post coordinates, applies lower-index nearest ties, and performs ordered
  positive-weight bilinear interpolation with deterministic overflow-safe `Math.fma` evaluation.
  Level 0/1/2 DTED tests use the format-neutral utility; no reprojection, format-specific query API,
  cache, cancellation, diagnostics, limits, or ownership transfer was introduced.
- G9-006 is complete: exactly three project-owned synthetic zone-V cells emitted by pinned GDAL
  3.13.0 cover complete Level 0/1 and partial Level 2 through a hash-pinned, licensed, size-capped
  inventory and finite public-reader oracle. The separate offline Java 21 corpus lane has no network
  or process API, normal/publication task graphs reject all six corpus tasks, and staged artifacts
  remain corpus-free. The evidence also narrowed optional fixed-field compatibility to ASCII text
  with GDAL NUL termination/padding; numeric discriminators and the one structural profile did not
  expand.
- DTED is elevation data, not a generic image format. It remains separate from GeoTIFF while both
  may produce the same elevation model. See the
  [G9 task set](tasks/README.md#g9--elevation-and-dted).

### G10 — Additional formats, tiles, and projections

- A secure static SVG import subset; general SVG is not promised.
- GeoJSON through one bounded optional Jackson Core adapter; a strict JDK-only Classic GeoTIFF reader
  with explicit raster/elevation entry points; separate strict GeoPackage 1.4.0 and raster MBTiles 1.3
  adapters; separate JDK-only GPX 1.1 waypoint/track and static KML 2.2 geometry sources; remote tile
  sources; and an evidence gate for any additional projection.
- GeoTIFF remains Level 2, keeps cell-area imagery distinct from sample-post terrain, and routes only
  its approved elevation profile through the G9 model. BigTIFF and GDAL remain deferred.
- SQLite-backed formats use separate `mundane-map-io-geopackage-xerial` and
  `mundane-map-io-mbtiles-xerial` Optional adapters with pinned code-only/Linux-native classifiers,
  direct read-only construction, and no JDBC type leakage. Their first claim is Java 21 on Linux
  x86-64/glibc JVM only; Native Image and other platforms require new evidence. See the
  [G10 task set](tasks/README.md#g10--additional-formats-tiles-and-projections).
- GPX and KML remain independent modules even though both use directly constructed JDK StAX. Their
  first profiles are bounded UTF-8 local-file snapshots yielding unstyled EPSG:4326 features; GPX
  routes/extensions and KML network, temporal, region, altitude, style, and presentation semantics
  remain explicit rejects or warned omissions rather than implicit behavior.
- Remote XYZ uses an explicit blocking acquisition client that callers run off UI/render threads; a
  successful bounded HTTP batch returns a detached Web Mercator raster source. The first profile has
  no credentials, redirects, proxy, cookies, retries, disk cache, live-network `RasterSource`, Native
  Image claim, or default public service URL.
- Additional projection work is currently deferred: no third CRS, formula, PROJ adapter, or raster
  warp is selected. A later proposal must supply one complete workflow/domain/accuracy/format/platform/
  conformance evidence packet, then choose `CORE_DIRECT` or `PROJ_REQUIRED` explicitly before creating
  implementation tasks or modules.

### G11 — Editing, styling, persistence, adapters, and export

- Editing uses an application-owned, point-first immutable-record session: atomic revision-checked
  transactions, bounded delta undo/redo, and explicit same-CRS vertex/segment snapping precede any
  mutable workflow. Read-only sources are never written or disguised as edit state. The approved
  design decomposes implementation into session, history, snapping, and point-tool vertical slices.
- Use one immutable binding-owned portrayal with closed fixed/categorical/graduated role selectors,
  exact canonical-scalar matching, and projected source attributes. Place bounded name/text-attribute
  labels for singular points in one deterministic global pass: AWT owns logical `SansSerif` metrics
  and drawing through one fixed logical metric profile shared by paint and export capture, while
  toolkit-neutral placed-label values preserve the handoff. Later
  G11-020 through G11-024 slices deliver selectors, complete roles, layout, regression/evidence, and
  native/consumer closeout without an expression language or label cache.
- Persist a strict local `.mmap.xml` version 1: canonical CRS/view state, ordered local opener/identity/
  path references, fixed external catalog symbols, and raster presentation only. One AWT-free module
  depends only on API/core and uses guarded relative paths, explicit application source/catalog
  registries, bounded secure StAX parsing, all-or-nothing owned sessions, and mandatory atomic
  replacement; edit/history, thematic labels,
  data, limits, caches, diagnostics, credentials, remote sources, and later formats remain excluded.
  G11-030 through G11-034 deliver reader, writer, session, viewer, and hardening/native/consumer slices.
- Approve only the two bounded Xerial-backed GeoPackage/MBTiles Optional adapters, with exact
  classifiers and a Java 21 Linux x86-64/glibc 2.35+ JVM-only claim. JTS, PROJ, and GDAL remain
  explicitly deferred until their recorded capability/evidence gates are met; no generic adapter API
  or empty module is reserved.
- Export one detached logical-screen viewport as canonical static SVG 1.1. API-owned immutable
  snapshot values cross from synchronous AWT capture into the existing AWT-free SVG module, which
  reuses only the approved core symbol algorithms. The profile supports the six vector geometry
  families, exact built-in vector symbol trees, hatches, and already measured/placed point labels;
  raster/elevation layers, raster icons, custom/legacy symbols, overlays, metadata, image fallback,
  and arbitrary SVG reject the whole operation. G11-040 through G11-043 deliver encoding/atomic
  write, real capture and complete built-ins, hardening, then manual browser/native/publication/
  consumer closeout without a new module or export framework.
- See the [G11 detailed design](design/G11-editing-styling-persistence-adapters-export.md) and the
  [G11 task set](tasks/README.md#g11--editing-styling-persistence-adapters-and-export).
