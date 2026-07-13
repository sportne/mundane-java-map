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
- The supported profile and all vertical slices are in the
  [G5 task set](tasks/README.md#g5--read-only-shapefile-support).

### G6 — Bounded PNG/JPEG raster support

- Add an AWT-free `mundane-map-io-image` source with an explicitly registered `ImageIO` decoder
  confined to `mundane-map-awt`.
- Support explicit bounds, world files, affine georeferencing, window requests, opacity,
  interpolation, bounded caches, lifecycle/cancellation, hostile inputs, a runnable viewer, and a
  real Native Image decode/render path.
- See the [G6 task set](tasks/README.md#g6--bounded-pngjpeg-raster-support).

### G7 — Performance evidence and optimization

- Establish repeatable large-data and JFR evidence before optimization.
- Add a packed spatial index, viewport queries, clipping, and scale-aware simplification, then retain
  only private bounded render-cache candidates that pass predeclared evidence rules.
- Do not add a custom native performance library without separate benchmark evidence and a new
  decision. See the [G7 task set](tasks/README.md#g7--performance-evidence-and-optimization).

### G8 — Native and release readiness

- Aggregate representative success and diagnostic paths in Linux Native Image CI.
- Review public APIs, Javadocs, examples, publication metadata, and staged downstream consumption.
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

- Define the format-neutral regularly sampled elevation model first, then render it through color
  ramps and optional hillshading.
- Add dependency-free `mundane-map-io-dted` support for Levels 0, 1, and 2 with bounded headers,
  dimensions, checksums, signed-magnitude samples, voids, position queries, licensed fixtures,
  memory/read evidence, and Native Image verification.
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
