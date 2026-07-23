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
architectural requirement. Level 1 completed at
[G8-004](tasks/closed/G8-004-level1-release-readiness.md) for candidate
`a5d10791d6cf811b438cb72504ff8b00b2ab8d75` and the bounded `0.1.0` support statement.

### G0 — Verified baseline

- Restore and verify the Java 21 Gradle baseline and publication staging in
  [G0-001](tasks/closed/G0-001-current-baseline-verification.md).
- Mechanically enforce dependency, toolkit, I/O, and native-target boundaries in
  [G0-002](tasks/closed/G0-002-architecture-boundary-hardening.md).
- Materialize one reusable exact offline Maven repository and prove it with one clean offline build
  in [G0-003](tasks/closed/G0-003-persistent-offline-repository-simplification.md).
- Keep coverage and specialized lanes explicit while removing task-graph interpreters and
  post-evaluation dependency rewrites in
  [G0-004](tasks/closed/G0-004-declarative-gradle-verification-wiring.md).

### G1 — First map slice

- Verify and harden the existing geometry, viewport, Swing interaction/rendering, viewer, and
  native-smoke slice in [G1-001](tasks/closed/G1-001-first-map-slice-verification.md).

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
- G7-005 keeps the same Java 21 `/tmp` evidence, quick/full profiles, DTED probe, and optional JFR
  while replacing the nearly thousand-line Gradle harness with one typed runner and explicit task
  dependencies. See [G7-005](tasks/closed/G7-005-lean-performance-build-harness.md).
- Do not add a custom native performance library without separate benchmark evidence and a new
  decision. See the [G7 task set](tasks/README.md#g7--performance-evidence-and-optimization).

### G8 — Native and release readiness

- Aggregate representative success and diagnostic paths in Linux Native Image CI.
- G8-001 is complete. Candidate `a5d10791d6cf811b438cb72504ff8b00b2ab8d75` passed the required
  Ubuntu 24.04 x86-64 lane with Oracle GraalVM Java 21.0.11+9.1, built the no-fallback image, ran the
  aggregate semantic and diagnostic scenarios, and printed `mundane-map native smoke: OK`. The
  approved claim remains limited to the recorded Java 21 Ubuntu 24.04 Linux x86-64 environment.
- G8-002 is complete. The five published modules now have strict offline Java 21 Javadocs and a
  published-only public-API documentation check; their immutable/toolkit/dependency boundaries were
  reviewed without incompatible API changes. README and all five examples describe and exercise the
  observed Level 1 surface. Fixed format-viewer identities, sensitive-path presentation tests, and
  captured WSLg launches cover the release-review checkpoint. G8-001's completed evidence supplies
  the final narrow Native Image wording.
- G8-003 is complete. One exact five-coordinate Level 1 contract serializes the real local-repository
  writes after cleanup, validates snapshot/release POMs, module metadata, reproducible licensed
  archives, strong checksums, and an immutable artifact manifest, then compiles and runs a fresh
  offline Java 21 consumer from only the staged artifacts. This independently executable release
  branch remains independent from G8-001's completed Linux CI checkpoint. The approved G9 sequencing
  exception later added DTED as a sixth current publication coordinate without widening Level 1.
- G8-001 and G8-003 are independent evidence branches after G8-002. G8-004 explicitly depends on
  both and is the only point that may join publication evidence with the completed native checkpoint.
- G8-004 and Level 1 are complete. Candidate `a5d10791d6cf811b438cb72504ff8b00b2ab8d75`
  received `GO` for version `0.1.0` after normal, native, shapefile-corpus, rendering-regression,
  performance, and publication/consumer lanes passed independently. Two fresh publication stagings
  produced byte-identical manifests and the isolated consumer passed. The six-coordinate current
  manifest contains the immutable five-coordinate Level 1 subset plus DTED as a Level 2 addendum.
- G8-005 preserves the staged Maven repository and one clean Java 21 consumer while narrowing the
  verifier to project-specific artifact, checksum, license, package, and dependency invariants. See
  [G8-005](tasks/closed/G8-005-lean-publication-and-consumer-verification.md).
- The evidence-record commit does not change or relabel the tested artifact revision, publish it,
  create a tag, or authorize a broader support statement.
- A successful Ubuntu 24.04 Linux x86_64 GraalVM Java 21 run is required; missing or failing evidence
  blocks Level 1 release. Windows, macOS, Linux AArch64, and other-distribution evidence is required
  before making broader Native Image claims, but does not block the narrowly supported release.

## Level 2 backlog

Level 2 begins after G8-004. Approved profiles that select implementation now have linked vertical
slices in the [task index](tasks/README.md#level-2-backlog); an approved `DEFER` outcome creates none.
The G10-040 through G10-044 SQLite-format cards document the reviewed design but remain gated by the
Proposed G10-004 and G11-004 decisions. Their presence does not approve an external dependency,
adapter, artifact, or platform claim. No task creates an empty module, and broader follow-up remains
separately decomposed.

### G9 — Elevation and DTED

- G9-001 completed under the maintainer's explicit sequencing exception before the final G8-004
  evidence record. The toolkit-neutral source, post-center metadata, typed limits/units, packed finite
  samples, structural no-data mask, diagnostics, and lifecycle are implemented without a format
  module or raster inheritance; it did not widen the later Level 1 release decision.
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
- G9-007 is complete: four append-only DTED scenarios, a fresh-JVM memory probe, and an analytical
  profile-cache model run wholly from `/tmp` and retain only durable evidence reports. A complete
  3,601-by-3,601 zone-I cell publishes in the canonical 512-MiB fork; exact logical storage is
  105,358,512 retained bytes with a 210,726,938-byte open peak. Frozen semantics, the 9.14-second
  quick loop, the 115.99-second canonical lane, and JFR CPU/allocation evidence retain eager access;
  no fallible windowed-source task or production/API/parser change is warranted.
- G9-008 and G9 are complete. Candidate `a5d10791d6cf811b438cb72504ff8b00b2ab8d75`
  passed the required Ubuntu 24.04 Linux x86-64 Oracle GraalVM Java 21.0.11 lane through the final
  sentinel. The single executable verifies its hash-pinned representative Level 0 read/query/
  colorize/hillshade/render/malformed-length path and exact six-dependency/13-resource inventory.
  The claim covers that Level 0 scenario only; Levels 1/2 remain JVM/corpus-verified.
- DTED is elevation data, not a generic image format. It remains separate from GeoTIFF while both
  may produce the same elevation model. See the
  [G9 task set](tasks/README.md#g9--elevation-and-dted).

### G10 — Additional formats, tiles, and projections

- G10-001 is complete: published AWT-free `mundane-map-io-svg` securely imports the approved static
  SVG marker subset into ordinary immutable vector/composite symbols through bounded JVM, render,
  Native Image, publication, and consumer paths. General SVG is not promised.
- G10-003 is complete as the profile decision. G10-030 publishes the first working JDK-only GeoTIFF
  slice, and G10-031 completes its uncompressed raster matrix: both byte orders, exact strips and
  full-shape edge tiles, WhiteIsZero/BlackIsZero/RGB with optional unassociated alpha, EPSG:4326 and
  EPSG:3857 scale/tiepoint placement, segment-selective strict windows, and tolerant rendering.
  G10-032 completes the closed raster codec matrix with bounded PackBits and Adobe Deflate,
  decode-once window staging, exact stream-consumption diagnostics, and ordinary AWT rendering.
  G10-033 adds the mutually exclusive finite 2D ModelTransformation path, exact cell-corner to
  pixel-center conversion, conservative affine bounds, and tolerant rotated/sheared rendering
  evidence. G10-034 adds eager signed Int16/Int32 PixelIsPoint terrain with explicit elevation
  units, exact post bounds, existing query/colorization rendering, and viewer integration. G10-035
  completes the elevation sample matrix with Float32/Float64, bounded finite or lowercase `nan`
  GDAL no-data masking, non-finite diagnostics, and compressed/tiled color and hillshade parity.
  G10-036 closes the parser envelope with exact ancillary-tag and citation validation, numeric
  unsupported-tag/key context, a single-buffer snapshot transaction with explicit cleanup
  arbitration, data-leak canaries, and deterministic raster/elevation mutation and every-cut
  truncation evidence. G10-037 adds a four-file independent-writer corpus with pinned provenance,
  licenses, recipes, and hashes; completes raster/elevation viewer outcomes and tolerant rendering;
  and extends the existing quick/canonical performance lane with bounded raster-window and eager
  elevation evidence. G10-038 closes the approved GeoTIFF sequence in the one shared native
  executable with four literal, checksummed independent fixtures: raster None/Deflate and elevation
  PackBits/Deflate open, query, render, cleanup, and exact malformed-header evidence. The supplemental
  no-fallback result is limited to GraalVM CE Java 21.0.2 on Ubuntu 24.04.1 WSL2 Linux x86-64; the
  pinned Ubuntu 24.04 x86-64 Oracle GraalVM Java 21 CI lane remains the authoritative repository
  evidence, and no Windows/macOS or cross-platform claim is made.
- G10-005 is complete as two profile decisions only: bounded GPX 1.1 and static KML 2.2 sources stay
  separate, use direct hardened JDK StAX, and begin later with G10-050 and G10-054 respectively.
- G10-002 approves one bounded optional Jackson Core GeoJSON read/write adapter. G10-020 and G10-021
  are complete: the published optional adapter reads all six approved 2D geometry families through
  locked and checksum-verified Jackson Core, with packed multipart storage, stable malformed-shape
  outcomes, tolerant source-backed rendering, architecture, offline publication, and consumer
  evidence. G10-022 is also complete: its writer borrows one canonical EPSG:4326 FeatureSource,
  rejects silent data loss, produces bounded deterministic UTF-8, and performs same-directory atomic
  local replacement with stable failure and cleanup behavior. G10-023 completes the hostile-input
  envelope with exact reader/writer ceilings, stable closed diagnostics, bounded deterministic
  mutation, cancellation-transition, lifecycle, cleanup-precedence, and atomic-target evidence.
  G10-024 adds checksummed provenance fixtures, stable viewer diagnostics, independent tolerant
  rendering evidence for all six geometry families and holes, staged publication, and a clean Java
  21 offline read/write/reopen/query/render consumer. G10-025 closes the sequence with one Linux
  x86-64 Native Image scenario that directly constructs Jackson, queries and renders a source,
  writes deterministic output, reopens it, checks one exact malformed diagnostic, and excludes the
  Jackson service descriptor/service from native registration. Generic JSON serialization remains
  outside scope; Windows and macOS Native Image remain unclaimed.
- G10-004 remains Proposed. Its draft evaluates strict GeoPackage and MBTiles profiles and an Xerial
  boundary; G10-040 through G10-044 record the conditional working graph but cannot execute while
  G10-004 or G11-004 is incomplete. No adapter, artifact coordinate/classifier set, or platform claim
  is approved.
- GeoTIFF remains Level 2, keeps cell-area imagery distinct from sample-post terrain, and routes only
  its approved elevation profile through the G9 model. BigTIFF and GDAL remain deferred.
- The approved GPX and KML designs keep their future modules independent even though both use directly
  constructed JDK StAX. Their first profiles are bounded UTF-8 local-file snapshots yielding unstyled
  EPSG:4326 features; GPX
  routes/extensions and KML network, temporal, region, altitude, style, and presentation semantics
  remain explicit rejects or warned omissions rather than implicit behavior.
- G10-050 through G10-057 are the Proposed GPX/KML implementation, hardening, fixture, viewer, and
  Native Image slices. Their separate parser branches converge only for shared final security/native
  evidence.
- Remote XYZ uses an explicit blocking acquisition client that callers run off UI/render threads; a
  successful bounded HTTP batch returns a detached Web Mercator raster source. The first profile has
  no credentials, redirects, proxy, cookies, retries, disk cache, live-network `RasterSource`, Native
  Image claim, or default public service URL. G10-006 completes this design and independently
  authorizes the shared G10-039 image helper before G10-060, without waiting on SQLite/Xerial work.
  G10-039 is complete: bounded detached PNG/JPEG bytes now reuse the full G6 validation and explicit
  decoder boundary with exact ownership/accounting and no file, source, cache, or toolkit coupling.
  G10-060 is complete: the published JDK-only/AWT-free adapter acquires one strict loopback-tested
  PNG/JPEG XYZ tile with bounded response handling and returns a detached EPSG:3857 raster that
  remains readable after client/server close. G10-061 is complete with exact tile-aligned region
  selection, deterministic concurrent batches, transparent missing-tile warnings, transactional
  decoded LRU caching, detached mosaics, and a worker-driven loopback viewer. G10-062 is complete:
  hostile-response, limits, interruption, cancellation, close/drain, and cache-rollback evidence now
  closes the adapter's Java 21 JVM profile. The module makes no Native Image or public-network claim.
- G10-007 is complete with outcome `DEFER`: no third CRS, formula, PROJ adapter, or raster
  warp is selected. A later proposal must supply one complete workflow/domain/accuracy/format/platform/
  conformance evidence packet, then choose `CORE_DIRECT` or `PROJ_REQUIRED` explicitly before creating
  implementation tasks or modules.

### G11 — Editing, styling, persistence, adapters, and export

- G11-001 is complete as a design decision. Editing uses an application-owned, point-first immutable-
  record session: atomic revision-checked transactions, bounded delta undo/redo, and explicit same-CRS
  vertex/segment snapping precede any
  mutable workflow. Read-only sources are never written or disguised as edit state. The approved
  design is delivered by complete G11-010 and G11-011: bounded create/replace/delete transactions now
  publish immutable revisioned snapshots through a borrowed editable map binding, with deterministic
  selection/hover reconciliation and a live point-edit example. Private per-command deltas now provide
  bounded undo/redo with prospective whole-entry eviction, monotonic replay revisions, and explicit
  history descriptions. Complete G11-012 adds the stateless bounded same-CRS vertex/segment resolver,
  exact semantic target indexes, cancellation/limit diagnostics, and visible snapped/unsnapped preview
  evidence. Complete G11-013 closes the interactive point-tool slice with view-bound create, selected-
  point drag/move, delete, undo/redo, deterministic snapping, transient overlays, stable diagnostics,
  a reviewed live viewer, tolerant rendering regression, and Linux Native Image smoke evidence.
- G11-002 is complete as a design decision. Use one immutable binding-owned portrayal with closed
  fixed/categorical/graduated role selectors,
  exact canonical-scalar matching, and projected source attributes. Place bounded name/text-attribute
  labels for singular points in one deterministic global pass: AWT owns logical `SansSerif` metrics
  and drawing through one fixed logical metric profile shared by paint and export capture, while
  toolkit-neutral placed-label values preserve the handoff. Later
  complete G11-020 now delivers fixed and exact categorical marker selection across snapshot, source,
  and editable bindings with exact attribute projection and transactional renderer preflight.
  Complete G11-021 adds normalized graduated selection across marker, line, and fill roles while
  keeping source projection, interaction, omission, and extent semantics aligned. Complete G11-022
  adds bounded name/attribute extraction, toolkit-neutral label values and candidate geometry, fixed
  Java2D metrics, and one global post-geometry label paint pass across every binding kind. Complete
  G11-023 adds fixed-limit deterministic priority/topmost collision admission, declared-position
  fallback, clipping, a reviewed styling/label viewer, and tolerant rendering regression. Complete
  G11-024 closes public documentation, performance, native, staged-consumer, and publication evidence
  without an expression language or label cache. The July 2026 baseline retained linear placement:
  256 sparse labels measured a 2.63 ms median and 1,024 fully colliding labels measured a 6.33 ms
  median on the recorded WSL/Linux environment, so neither a placement index nor cache is justified.
  Linux Native Image covers categorical portrayal rendering plus toolkit-neutral placement and its
  stable limit diagnostic. Java2D font/glyph execution is not part of that native claim because it
  would require internal-JDK JNI/reflection metadata; JVM rendering uses logical `SansSerif`, with no
  Windows/macOS Native Image or cross-platform glyph-identity claim.
- G11-003 is complete as a design decision. Persist a strict local `.mmap.xml` version 1: canonical
  CRS/view state, ordered local opener/identity/
  path references, fixed external catalog symbols, and raster presentation only. One AWT-free module
  depends only on API/core and uses guarded relative paths, explicit application source/catalog
  registries, bounded secure StAX parsing, all-or-nothing owned sessions, and mandatory atomic
  replacement; edit/history, thematic labels,
  data, limits, caches, diagnostics, credentials, remote sources, and later formats remain excluded.
  Complete G11-030 now delivers the immutable workspace model, bounded local file snapshot, strict
  UTF-8/XML 1.0 grammar, hardened JDK StAX reader, stable problems, and an AWT-free published module.
  Complete G11-031 adds deterministic direct UTF-8 encoding, exact output/operation accounting,
  private same-directory temporary files, forced content, target-change detection, and mandatory
  atomic replacement with failure-safe cleanup. Complete G11-032 adds exact explicit source/catalog
  registries, guarded finite path profiles, preflight-before-I/O, cancellation, and all-or-nothing
  owning sessions. Complete G11-033 restores a real local shapefile/world-file raster workspace in a
  runnable AWT viewer with borrowed bindings, persisted view/raster state, and deterministic close
  order. Complete G11-034 closes the capability with public threat/lifecycle guidance, the existing
  exhaustive hostile/mutation/allocation/fault matrices, verified publication metadata, and a clean
  offline Java 21 consumer that writes, reads, atomically rewrites, explicitly opens, and closes a
  staged-artifact-only workspace. The shared native executable performs the same bounded
  read/write/open/close path plus one stable hostile-XML failure; the required lane passed on
  2026-07-20 using GraalVM CE Java 21.0.2 on Ubuntu 24.04 WSL2 Linux x86-64. This evidence makes no
  Windows, macOS, Linux AArch64, non-WSL distribution, or broader cross-platform Native Image claim.
- G11-004 remains Proposed. Its draft evaluates whether to accept bounded Xerial-backed GeoPackage/
  MBTiles adapters and defer JTS, PROJ, and GDAL; no adapter disposition, classifier, platform claim,
  generic adapter API, or module is approved.
- G11-005 is complete as a design decision. Export one detached logical-screen viewport as canonical
  static SVG 1.1. API-owned immutable snapshot values cross from synchronous AWT capture into the
  existing AWT-free SVG module, which
  reuses only the approved core symbol algorithms. The profile supports the six vector geometry
  families, exact built-in vector symbol trees, hatches, and already measured/placed point labels;
  raster/elevation layers, raster icons, custom/legacy symbols, overlays, metadata, image fallback,
  and arbitrary SVG reject the whole operation. Complete G11-040 now supplies the immutable detached
  snapshot boundary plus canonical background, singular point/line/polygon solid portrayal, placed
  labels, bounded encoding, cancellation, and failure-atomic local replacement. Complete G11-041
  now captures the visible vector stack synchronously on the EDT, preserves authoritative screen
  geometry and fixed point-label placement, and encodes all six geometry families, composite
  symbols, endpoint markers, outlines, and bounded hatches. Its vector-export example displays and
  exports the same viewport. Complete G11-042 now enforces the approved semantic snapshot inventory,
  deterministic chunked UTF-8 writer accounting, hatch and transform limits, cancellation
  checkpoints, stable diagnostics, and failure-atomic cleanup under injected filesystem faults.
  Complete G11-043 closes the capability without a new module or export framework: a checked-in
  structural/render fixture captured from the runnable example agrees on the approved broad
  properties in Firefox 149.0 and Chromium-based Google Chrome 150.0.7871.129 on Windows NT build
  26200.8875; the clean staged Java 21 consumer verifies the
  published API/core/SVG dependency boundary; and the shared native executable passed on
  2026-07-21 using GraalVM CE Java 21.0.2 on Ubuntu 24.04.1 WSL2 Linux x86-64. The browser evidence
  is not a pixel/glyph-identity claim, and no Windows/macOS Native Image claim is made.
- See the [G11 detailed design](design/G11-editing-styling-persistence-adapters-export.md) and the
  [G11 task set](tasks/README.md#g11--editing-styling-persistence-adapters-and-export).

### G12 — MIL-STD-2525 symbology

- G12 is the first standards-based symbology track. Its approved profile uses a JDK-only, AWT-free
  `mundane-map-symbology-milstd2525` module for MIL-STD-2525E with Change 1 incorporated, using
  canonical 30-position SIDCs and a finite icon-based Land Unit, Land Equipment, and Activities
  profile.
- Resolved frames, icons, and graphical modifiers become ordinary toolkit-neutral marker/composite
  symbols. There is no military-specific AWT renderer, runtime table discovery, remote update, legacy
  SIDC translation, APP-6 mapping, text-amplifier engine, or multipoint tactical-graphics claim.
- G12-001 approved the exact profile/legal/conformance wording and authoritative-source record on
  2026-07-23. G12-002 and G12-003 deliver parsing and the first real rendering slice; G12-004
  through G12-006 retain the finite catalog and portrayal integration, reference/gallery hardening,
  and publication/consumer/Linux Native Image closeout.
- G12-002 supplies the published JDK-only, AWT-free module with packed canonical SIDC parsing,
  complete field access, stable structured syntax/support problems, and deterministic strict versus
  degradable support classification. G12-003 adds all supported identity and status frames, both
  approved palettes, the Infantry entity, degraded-frame behavior, and ordinary AWT paint/hit
  testing. Native Image targeting remains unclaimed until G12-006 adds executable evidence.
- G13 is deliberately dependent on G12-006 so the requested MIL-STD-2525-first order is explicit.
- See the [G12 detailed design](design/G12-milstd2525-symbology.md) and the
  [G12 task set](tasks/README.md#g12--mil-std-2525-symbology).

### G13 — OGC Symbology Encoding

- After G12, G13 proposes a bounded read-only OGC SE 1.1 `FeatureTypeStyle` adapter in
  `mundane-map-io-se`, using secure directly constructed JDK StAX and no schema/network resolution.
- The supported profile covers ordered rules, bounded attribute filters and explicit scale context,
  point/line/polygon symbolizers, and caller-catalog graphics. Coverage, SLD/WMS, text/raster
  symbolizers, arbitrary functions, and remote resources remain excluded.
- G13-001 approves the profile and the smallest closed standards-neutral rule-portrayal bridge.
  G13-002 through G13-006 deliver the first point slice, rules/scale, complete vector roles,
  hostile/interoperability/gallery evidence, and publication/consumer/Linux Native Image closeout.
- See the [G13 detailed design](design/G13-ogc-symbology-encoding.md) and the
  [G13 task set](tasks/README.md#g13--ogc-symbology-encoding).

### G14 — MapLibre Style

- G14 follows G13 and proposes an optional `mundane-map-io-maplibre-style-jackson` adapter for a
  bounded MapLibre Style Specification v8 subset. It reuses the existing locked Jackson Core
  boundary and G13's standards-neutral portrayal plan.
- The first profile supports explicit caller-bound feature sources, circle/line/fill and bounded
  symbol layers, filters, zoom ranges, a closed typed expression subset, caller-catalog icons, and
  G11-compatible point labels. It never fetches sources, tiles, sprites, glyphs, or fonts and does
  not claim 3D, terrain, heatmap, vector-tile, Mapbox-extension, or complete MapLibre compatibility.
- G14-001 approves the exact matrix. G14-002 through G14-007 deliver literal layers, transactional
  source binding, expressions, icons/labels, hostile/interoperability/gallery evidence, and
  publication/consumer/Linux Native Image closeout.
- See the [G14 detailed design](design/G14-maplibre-style.md) and the
  [G14 task set](tasks/README.md#g14--maplibre-style).

### G15 — Live-track stress and IOU tracking

- Add one JVM-only `live-track-stress` example that individually simulates, receives, and estimates
  10,000, 100,000, or 1,000,000 fixed tracks over a simple global chart.
- Use the G15-001-approved bounded forward IOU-Kalman Filter state estimator derived independently
  from publicly documented Integrated Ornstein-Uhlenbeck position/velocity equations. The approved
  profile fixes equations, numerical behavior, provenance, and support wording; it makes no
  proprietary-equivalence, endorsement, operational-accuracy, or safety claim.
- Complete G15-002 keeps that estimator example-local and proves its allocation-free packed scalar
  update against an independent dense oracle across the approved interval and numerical boundaries.
- Schedule deterministic stochastic position reports independently per track at intervals from one
  second through one minute. Use packed primitive truth/filter state, a due-work timing wheel, and
  stable worker shards; data association, track birth/death, networking, and operational accuracy
  remain excluded.
- Complete G15-003 supplies the packed deterministic truth/report simulator, 64-slot due-work wheel,
  stable worker shards, explicit virtual/real-time lifecycle, and 10k headless filtering slice.
- Complete G15-004 bundles the official Natural Earth `ne_110m_land` shapefile with exact version,
  hashes, retrieval record, public-domain terms, and no runtime download. It verifies and stages the
  unchanged sidecars, explicitly declares EPSG:4326 for the retained ESRI WKT, clips decoded land to
  the Web Mercator source domain, renders through the existing shapefile/CRS/MapView stack, and
  removes staged files with the owned layer.
- Draw dense estimated positions through an example-owned detached AWT overlay rather than widening
  the immutable `FeatureSource` contract or allocating one million feature records per frame. Keep
  map navigation on the EDT and publish at most one generation-matched completed frame.
- Complete G15-005 supplies that first 10k picture with a non-intercepting overlay, three-buffer
  ownership bound, one in-progress/one-pending frame handoff, generation-aware stale rejection,
  exact 1/2/5/10/15/30/60/uncapped EDT pacing, visible telemetry, and asynchronous pause/reset/close
  controls. Its deterministic `liveTrackSmoke` lane advances 120 virtual seconds and stays separate
  from the normal quality gate.
- Complete G15-006 adds the runnable 100k tier, measured stable sharding, report/work skew and
  backlog telemetry, bounded packed display caches, deterministic 1/8-shard equivalence, and a
  reproducible `/tmp` JFR profile. The retained coefficient/display-transition and Gaussian-pair
  optimizations preserve the frozen strict formulas and reduced the named 100k/1200s probe by about
  41% on the recorded machine; this is environment evidence, not a portable threshold.
- Report achieved FPS and accept an explicit maximum-FPS cap while separately reporting update
  throughput, frame latency/skips/stale results, backlog, shard skew, logical/observed memory, and
  deterministic error summaries. The reference target is the approximately 100,000-report/second 1m
  workload with a 10 FPS cap; its measured outcome is evidence, not a portable timing gate.
- G15-005 creates a sub-five-minute 10k `liveTrackSmoke` iteration lane. Complete G15-007 adds the
  runnable 1m tier and an opt-in `/tmp` `liveTrackEvidence` lane with schema-tagged JSON and
  LLM-readable Markdown for 10k, 100k, and 1m. Its named WSL2/Java 21 evidence met the 10 FPS cap at
  all tiers, including approximately 100,000 reports/second and 447 MB peak observed heap at 1m. Full
  stress evidence remains outside `qualityGate` and ordinary CI, and FPS is evidence rather than a
  portable pass/fail threshold. The elevated large-population innovation summaries reflect the
  intentionally simple Cartesian estimator encountering global wrap/reflection discontinuities;
  they are retained evidence and do not support an operational-accuracy claim.
- Complete G15-008 closes deterministic worker-failure, cancellation, pause/reset, resize/stale,
  concurrent shutdown, replay, and one-hour virtual soak evidence. The reviewed viewer/report
  documentation preserves the JVM-only, non-operational support boundary. The holistic closeout
  keeps all estimator, scheduler, packed overlay, and evidence types inside the example because no
  second consumer justifies a public live-source or tracking framework.
- A post-close presentation correction prepares and projects Natural Earth once, renders a bounded
  two-screen background on one owned worker, coalesces stale viewport demand, and detaches duplicate
  covered `MapView` content after publication. On the recorded environment, cached pan composition
  measured below one millisecond while exact replacement work stayed off the EDT. These are
  threshold-free observations, not a portable responsiveness claim.
- G15-002/G15-003 estimator/simulator work may proceed in parallel with G15-004 chart work after the
  G15-001 decision. G15-005 through G15-008 then deliver the 10k picture, evidence-guided 100k scale,
  1m reports, and lifecycle/simplicity closeout.
- See the [G15 detailed design](design/G15-live-track-stress-and-iou-tracking.md) and the
  [G15 task set](tasks/README.md#g15--live-track-stress-and-iou-tracking).

### G16 — Dateline and continuous world wrap

- Add explicit, bounded horizontal repetition so global maps can pan continuously east or west
  across the Web Mercator dateline. Wrapping is a display policy above strict projection and source
  coordinates; EPSG:4326/EPSG:3857 domains do not widen.
- Wrapping remains disabled by default. The view declares one periodic display profile and each
  global layer independently opts into `REPEAT_X`; local layers never repeat from CRS or extent
  inference.
- Keep canonical source geometry and stable logical feature identity. Decompose display X into a
  canonical coordinate plus checked world-copy index, split seam viewports into unique canonical
  queries, query a full world once, deduplicate source records, and paint bounded translated copies
  in deterministic order.
- G16-001 is complete: it approved core/AWT API placement, the public planner boundary,
  transactional view configuration, half-open seam convention, eight default and 64 hard visible
  copies, a 1,048,576 copy-index ceiling plus quarter-pixel precision check, aggregate query
  accounting, westward half-period ties, atomic polygon rejection, strict raster compatibility,
  diagnostic precedence, and optional-by-default support wording.
- G16-002 is complete: the responsive G15 viewer repeats prepared Natural Earth and canonical packed
  tracks through multiple east/west crossings using the shared bounded planner, while overscanned
  replacement rendering remains coalesced off the EDT and presentation timing remains evidence.
- G16-003 is complete: `MapView` has an explicit compatible horizontal profile, each binding keeps
  a pre-attachment `NONE`/`REPEAT_X` choice, and ordinary in-memory and shapefile point sources use
  bounded split/full-world queries, stable-ID deduplication, aggregate accounting, and paint-scoped
  copies without changing canonical records.
- G16-004 is complete: one bounded core seam splitter takes recognized geographic line and polygon
  geometry along the deterministic shortest path, emits canonical packed fragments, preserves real
  endpoint and built-in fill/outline semantics, and feeds the same ordered copies to paint, hit
  geometry, labels, detached vector export, and deterministic SVG. A full-longitude, visible-latitude
  geographic query prevents ordinary literal envelopes from hiding seam-crossing records without
  admitting unrelated latitudes; topology requiring repair is rejected atomically. Projected
  sources repeat literally; default non-wrapped sources remain unchanged.
- G16-005 is complete: reverse-paint hit testing and copy-scoped hover retain logical feature
  identity, persistent selection paints every visible copy, and pointer inversion plus
  vertex/segment snapping produce canonical edit coordinates. Snap repetition is per binding, and
  repeated editable visual output is charged against existing session and label limits before
  allocation. Point create/move/delete and undo/redo remain logical commands, while geographic
  measurement keeps its existing shortest antimeridian distance and private display references keep
  the path beneath the pointer across multi-world views. The measurement and point-edit examples
  expose explicit wrapped modes without changing planar defaults.
- G16-006 is complete: explicitly opted-in global rasters validate matching CRS, full-period extent,
  and axis-aligned affine behavior before attachment. Canonical seam windows are read and accounted
  once, tolerated source edges normalize to exact display seams, detached pixels are reused across
  checked visual copies, cancellation publishes atomically, and local or rotated/sheared rasters
  remain bounded. The raster viewer exposes an explicit global
  mode, and bounded tile-column math is ready for later G10 HTTP/MBTiles consumers without an empty
  format or network module.
- G16-007 is complete: deterministic hostile/boundary sweeps, tolerant rendering, paired
  disabled/wrapped performance rows, Linux Native Image success/failure paths, an explicitly
  configured staged consumer, Javadocs, examples, and approved support wording close the gate.
  Performance remains evidence rather than a wall-clock quality threshold.
- G16 excludes automatic wrap discovery, vertical/polar wrapping, globe rendering, topology repair,
  projected seam guessing, external dependencies, native acceleration, and implementation of open
  tile/container tasks.
- G16-004 and G16-006 may proceed in parallel after G16-003 with one shared `MapView` integration
  owner. G16-005 follows vector completion, and G16-007 is the convergence owner.
- See the [G16 detailed design](design/G16-dateline-and-continuous-world-wrap.md) and the
  [G16 task set](tasks/README.md#g16--dateline-and-continuous-world-wrap).

G16 is complete. The gate-level review retained one core planner plus explicit AWT view/binding
configuration; source identity, strict CRS domains, local layers, and non-wrapped defaults remain
unchanged.
