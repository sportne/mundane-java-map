# G19 — Module feature-completeness review

## Purpose

G19 records a source-level review of every Gradle project under `modules/` and turns confirmed
limitations into an actionable backlog. Earlier gates remain truthful: they completed deliberately
bounded profiles. G19 asks a different question—what would an external specialist still regard as
missing from the declared domain, format, or protocol?

“Feature complete” here means complete against a named standards baseline and an explicit supported
profile. It does not mean implementing every adjacent GIS product category, accepting malformed input,
or weakening the project’s bounded-work, stable-diagnostic, explicit-registration, JDK-only, and
ownership rules.

## Review method

For each module the review inspected its public surface, package documentation, implementation,
ordinary and specialized tests, completed design decisions, and explicit unsupported diagnostics.
Format and protocol findings were compared with primary specifications:

- ESRI Shapefile Technical Description (July 1998):
  <https://downloads.esri.com/support/whitepapers/ao_/shapefile.pdf>
- PNG Third Edition: <https://www.w3.org/TR/png-3/>
- WebP container and bitstream specifications: <https://developers.google.com/speed/webp/docs/riff_container>,
  <https://datatracker.ietf.org/doc/html/rfc6386>, and
  <https://developers.google.com/speed/webp/docs/webp_lossless_bitstream_specification>
- HTTP Semantics and Caching: <https://www.rfc-editor.org/rfc/rfc9110> and
  <https://www.rfc-editor.org/rfc/rfc9111>
- MIL-PRF-89020B DTED: <https://quicksearch.dla.mil/qsDocDetails.aspx?ident_number=110830>
- OGC GeoTIFF 1.1 and COG 1.0: <https://docs.ogc.org/is/19-008r4/19-008r4.html> and
  <https://docs.ogc.org/is/21-026/21-026.html>
- SVG 2 secure-static processing: <https://www.w3.org/TR/SVG2/conform.html>
- OGC Symbology Encoding 1.1, Styled Layer Descriptor 1.1, Filter Encoding 1.1,
  GPX 1.1, and OGC KML 2.3:
  <https://www.ogc.org/standards/se/>, <https://www.ogc.org/standards/sld/>,
  <https://schemas.opengis.net/filter/1.1.0/>, <https://www.topografix.com/gpx/1/1/>, and
  <https://docs.ogc.org/is/12-007r2/12-007r2.html>
- RFC 7946 GeoJSON and RFC 8142 GeoJSON Text Sequences:
  <https://www.rfc-editor.org/rfc/rfc7946> and <https://www.rfc-editor.org/rfc/rfc8142>
- current MapLibre Style Specification: <https://maplibre.org/maplibre-style-spec/>
- OGC GeoPackage 1.4.0: <https://docs.ogc.org/is/12-128r19/12-128r19.html>
- MBTiles 1.3: <https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md>
- Mapbox Vector Tile 2.1 and UTFGrid 1.3:
  <https://github.com/mapbox/vector-tile-spec/blob/master/2.1/README.md> and
  <https://github.com/mapbox/utfgrid-spec/blob/master/1.3/utfgrid.md>
- WCAG 2.2: <https://www.w3.org/TR/WCAG22/>

Every proposed expansion must freeze the exact edition, conformance classes, limits, diagnostics,
and interoperability corpus before implementation. Specification breadth is not permission for
network discovery, credential lookup, reflection, classpath scanning, JNI, or unbounded recovery.

## Module findings

### `mundane-map-api`

The API is coherent for immutable 2D maps, but its sealed geometry model has only the six homogeneous
2D families. It cannot faithfully carry Z/M ordinates, heterogeneous geometry collections, or empty
standard geometries. Its attributes are intentionally flat scalar values, and its portrayal model
lacks common cap/join/dash, graphic paint, advanced text, and band/raster semantics. These limits are
the root blockers for complete Shapefile, GeoJSON, KML, GeoPackage, SE, and MapLibre support. The
authoritative current, target, invariant, and exclusion matrix is recorded in
`modules/mundane-map-api/CAPABILITIES.md`.

Cards: G19-001 and G19-002.

### `mundane-map-core`

Core recognizes only EPSG:4326 and EPSG:3857 operations, has no WKT2/axis-aware common-CRS catalog,
does not provide general geometry validity/topology or dimensional preservation, and cannot reproject
raster grids. Label placement is point-only and tile calculations are Web-Mercator XYZ-specific rather
than an OGC tile-matrix-set model. The authoritative algorithm profile and its explicit pure-Java,
bounded, no-network boundaries are recorded in `modules/mundane-map-core/CAPABILITIES.md`.

Cards: G19-010 through G19-014.

### `mundane-map-awt`

The Swing renderer is complete for today’s built-in profile, not for the advanced portrayal and
dimensional geometry needed by G19. `MapView` also lacks a reviewed Swing accessibility contract and
a deterministic pageable/print rendering surface. Keyboard routing exists, but that is not equivalent
to an accessible component model. The authoritative rendering, approximation, accessibility, print,
provider, and lifecycle matrix is recorded in `modules/mundane-map-awt/CAPABILITIES.md`.

Cards: G19-020 and G19-021.

### `mundane-map-io-shapefile`

The reader explicitly rejects every Z/M shape code and MultiPatch, omits memo-backed and broader
dBASE/encoding profiles, recognizes only two exact PRJ trees, and is read-only. These are visible
gaps against the ESRI shape inventory and common interoperable datasets, even though the current 2D
profile is complete. The approved target adds complete declared-profile reading and a strict,
transactional create-new-dataset exporter; it does not add in-place updates or silent lossy
conversion. The detailed matrix is recorded in
`modules/mundane-map-io-shapefile/CAPABILITIES.md`.

Cards: G19-030 through G19-036.

### `mundane-map-io-image`

PNG probing and validation cover static images up to eight bits per sample but reject the standard
16-bit combinations and do not apply PNG color-management chunks. JPEG is limited to an eight-bit
grayscale/RGB baseline/progressive subset and deliberately ignores EXIF orientation, ICC/color
semantics, and CMYK/YCCK. The approved module intent is decode-only: complete the PNG Third Edition
static/default image and common 8-bit Huffman DCT JPEG interchange profiles without adding image
encoding, editing, APNG playback, or the less common JPEG coding families. The detailed current,
target, and excluded profiles are recorded in `modules/mundane-map-io-image/CAPABILITIES.md`.

Cards: G19-040 through G19-044.

### planned `mundane-map-awt-image-webp-twelvemonkeys`

WebP is useful in tile databases and web style ecosystems, but a complete custom RIFF/VP8/VP8L/alpha
codec would be a large security-sensitive image project and would duplicate maintained pure-Java work.
The approved path is a separately published optional AWT adapter pinned to TwelveMonkeys ImageIO 3.14.0.
It explicitly constructs the WebP reader, converts static lossy/lossless/alpha images into the project's
neutral immutable raster model, and never exposes ImageIO, Java2D, or dependency types through project
contracts. It does not use service discovery or global provider registration. Animation, writing,
transcoding, custom codecs, and Native Image support are explicit exclusions. Until the module exists,
its approved boundary is recorded in `modules/mundane-map-io-image/CAPABILITIES.md` and its cards; the
first working slice creates the module-local matrix.

Cards: G19-227 through G19-229.

### `mundane-map-io-http-tiles`

The client implements one fixed-host, fixed-size Web-Mercator XYZ profile with one attempt, no
redirects, conditional requests, freshness, stale policy, credentials, proxy configuration, TMS,
retina/variable sizes, or service metadata. The approved TileJSON target is read-only consumption in
a new explicit `mundane-map-io-tilejson-jackson` adapter so the transport remains JDK-only; authoring
and serving are excluded. The approved WMTS target is a separate JDK-only read client for 1.0.0
capabilities, explicit selection, KVP/REST tiles, and bounded media-typed FeatureInfo; SOAP and server
behavior are excluded. The approved OGC API Tiles target is a separate Jackson adapter with guarded
landing-page discovery, explicit conformance/resource/matrix selection, generic bounded raw tile
results, and raster construction through registered decoders; server behavior is excluded. The live
matrix is recorded in `modules/mundane-map-io-http-tiles/CAPABILITIES.md`.

Cards: G19-050 through G19-058 and G19-220 through G19-224 across the HTTP and planned
TileJSON/WMTS/OGC API Tiles adapter owners.

### `mundane-map-io-dted`

Single-cell Level 0/1/2 parsing is strict and well evidenced, but it eagerly retains the whole cell,
discards most standard metadata, rejects accuracy subregions, and has neither regional access nor a
writer. The approved target is complete declared metadata, bounded windowed cells, an explicit
catalog and seam-aware mosaics, plus builder-driven transactional cell creation. Most builder
metadata has deterministic conservative defaults; cell/level/grid facts and vertical datum remain
required or exactly derived. The writer never performs implicit reprojection/resampling and makes no
official product-certification claim. The live standards/default matrix is recorded in
`modules/mundane-map-io-dted/CAPABILITIES.md`.

Cards: G19-060 through G19-066.

### `mundane-map-io-geotiff`

The current profile rejects BigTIFF, multiple IFDs/SubIFDs, overviews, masks, planar samples,
orientation, LZW/JPEG and predictors, most photometric/sample organizations, general GeoKeys,
user-defined/vertical/compound CRS, and remote range access. The approved target is common
geospatial TIFF reading, lossless raw bands, complete applicable GeoTIFF 1.1 behavior, vertical/3D
interoperability stated at its actual non-normative boundary, guarded local/HTTP windows, and a
builder-driven writer for conventional tiled GeoTIFF and COG. The writer uses lossless None/LZW/
Deflate rather than reopening the image module's rejected JPEG-encoding scope. The live matrix is
recorded in `modules/mundane-map-io-geotiff/CAPABILITIES.md`.

Cards: G19-070 through G19-079.

### `mundane-map-io-svg`

Import is a small secure marker grammar rather than a named SVG 2 restricted static profile: CSS,
paint servers, reusable definitions, clipping/masking, text, images, filters, and much viewport/unit
behavior are absent. The approved target stays non-scripted and non-animated, resolves resources only
from embedded data or a closed caller catalog, and implements a bounded common filter graph while
excluding turbulence, displacement, and lighting. Export is canonical for the project snapshot but
is not yet evidenced as an accessible deterministic generator for the completed static profile. The
live standards/feature/security matrix is recorded in `modules/mundane-map-io-svg/CAPABILITIES.md`.

Cards: G19-080 through G19-089.

### `mundane-map-io-se`

The released SE reader accepts only a `FeatureTypeStyle` subset. It lacks an SLD 1.1 document wrapper,
Filter Encoding 1.1 arithmetic/functions/identity/spatial predicates, complete rule/geometry behavior,
standard units beyond pixels, advanced vector graphics, TextSymbolizer, RasterSymbolizer, and
CoverageStyle. It also has no writer or declared OGC conformance evidence. The approved target adds
strict lossless canonical SE 1.1 and SLD 1.1 writing but not WMS operations or vendor extensions.
Filter Encoding 2.0 temporal predicates are deliberately outside the SE 1.1 profile because the
standard references Filter Encoding 1.1. The live standards, feature, resource, writer, and exclusion
matrix is recorded in `modules/mundane-map-io-se/CAPABILITIES.md`.

Cards: G19-090 through G19-099.

### `mundane-map-io-gpx`

GPX 1.1 routes are terminally unsupported. Most standard metadata, links, waypoint quality/time fields,
and track-point data are ignored with warnings; extensions are discarded; no canonical writer exists.
The approved target is a complete immutable GPX 1.1 reader and deterministic writer. Unknown vendor
extensions are retained as a bounded namespace-aware infoset and may use explicitly registered typed
codecs, but the core module will not embed Garmin or other vendor-specific semantics. The live standard,
domain, extension, feature-projection, writer, and exclusion matrix is recorded in
`modules/mundane-map-io-gpx/CAPABILITIES.md`.

Cards: G19-100 through G19-106.

### `mundane-map-io-kml`

The parser is a static KML 2.2 geometry subset. It lacks the KML 2.3 object/data/extension model,
altitude/time/views, heterogeneous geometry and tracks, styles, regions, overlays, models, KMZ
resources, `NetworkLink`, `Update`, `Tour`, and a writer. The approved target provides complete KML
2.3/KMZ interchange, deterministic writing, explicitly authorized bounded network links,
transactional in-memory updates, and host-neutral tour playback. The 2D toolkit fully renders ground
and screen overlays but represents PhotoOverlay panoramas and COLLADA models as documented 2D
footprints/thumbnails without building general panorama/3D engines. Unknown extensions round-trip as
a bounded infoset. A separate optional Jsoup adapter sanitizes and renders a named static HTML subset
for descriptions/balloons while core remains JDK-only and safely escapes markup. The live standard,
feature, portrayal, resource, dynamic, writer, and exclusion matrix is recorded in
`modules/mundane-map-io-kml/CAPABILITIES.md`.

Cards: G19-110 through G19-119, plus G19-225 and G19-226 in the planned
`mundane-map-io-kml-html-jsoup` adapter.

### `mundane-map-symbology-milstd2525`

The module honestly implements only fifteen project-authored Land/Activities entity paths and seven
sector modifiers. It is not the full current inventory and has no tactical graphics, full modifiers,
legacy/NATO translation, or independent conformance data. The approved authoritative targets are
MIL-STD-2525E Change 1 (2 March 2025) and APP-06 Edition E Version 1. The module also reads and
loss-audits translation from 2525D Change 1, 2525C, and APP-06D, with legacy output only when
losslessly representable; A/B remain excluded. Completion includes all current point symbols and the
full multipoint tactical-graphics/control-measures catalog, editing, AWT/Vaadin/SVG parity, and
traceable lawful generated-data/artwork provenance. The live edition, SIDC, catalog, point, tactical,
translation, rendering, and exclusion matrix is recorded in
`modules/mundane-map-symbology-milstd2525/CAPABILITIES.md`.

Cards: G19-120 through G19-129.

### `mundane-map-io-geojson-jackson`

The released strict RFC 7946 adapter handles a bounded six-family, non-empty XY feature-source slice
and deterministic FeatureCollection writing. Completion covers all seven geometry types, empty/null
forms, Z and bounded further position ordinates, scoped N-dimensional bboxes, winding and antimeridian
semantics, complete immutable JSON properties and foreign members, and every GeoJSON object root.
RFC 8142 is a distinct incrementally framed reader/writer surface. An explicit legacy-input mode may
accept only caller-registered obsolete `crs` identifiers and reprojects them to strict WGS 84; it never
fetches linked definitions or emits legacy CRS output. Output is deterministic but does not claim RFC
8785 or source-lexical preservation. The live standards, geometry, value, CRS, sequence, writer, limits,
and exclusion matrix is recorded in `modules/mundane-map-io-geojson-jackson/CAPABILITIES.md`.

Cards: G19-130 through G19-136.

### `mundane-map-io-maplibre-style-jackson`

The released adapter is a detached reader for a small MapLibre version-8 vector-style subset: descriptive
GeoJSON source locators, circle/line/fill/point-symbol portrayal, and a small expression algebra. Completion
is pinned to `@maplibre/maplibre-gl-style-spec` v26.2.1 (whose documents still declare version 8) and adds
deterministic writing, all 19 root members, six source types, ten layer types, 87 expressions, and the exact
generated property inventories. Deprecated filters/functions read through a lossless migration path but are
never written. Resources resolve only through an explicit caller-authorized online policy or offline catalog;
video uses a caller-supplied decoded-frame provider rather than a built-in codec. Every applicable 2D behavior
is rendered. Terrain, extrusion, model, sky/fog, and non-2D projection state remain complete validated
interchange data but produce explicit non-renderable diagnostics from the 2D binder. The live document,
expression, source, layer, resource, writer, limit, and exclusion matrix is recorded in
`modules/mundane-map-io-maplibre-style-jackson/CAPABILITIES.md`.

Cards: G19-140 through G19-149.

### `mundane-map-io-geopackage-xerial`

The released Xerial adapter is an extension-free read-only subset for simple features and PNG/JPEG tiles.
Completion targets full direct use of OGC GeoPackage 1.4.0: core/features/tiles/attributes, every registered
extension, all core and non-linear GeoPackageBinary geometries, RTree, raster pyramids, Tiled Gridded Coverage
Data 1.1, Related Tables, builder-driven creation, and transactional editing/recovery. Unknown vendor
extensions are preserved when unrelated and block governed mutations unless an explicit codec owns them.
The OGC Vector Tiles Pilot/MVT/GeoJSON/RBT-compatible surface and Styling and Symbology storage are separate
opt-in community profiles with separate evidence and no GeoPackage conformance claim. High-level APIs remain
typed and do not expose arbitrary SQL. The live core, extension, geometry, tile, coverage, community,
transaction, platform, limit, and exclusion matrix is recorded in
`modules/mundane-map-io-geopackage-xerial/CAPABILITIES.md`.

Cards: G19-150 through G19-159.

### `mundane-map-io-mbtiles-xerial`

The reader rejects spec-compatible views and supports only PNG/JPEG raster tiles. Completion targets
the full MBTiles 1.3 logical container, raw declared media, registered raster decoders, MVT 2.1
read/write, and bounded UTFGrid 1.3 read/write as a legacy interoperability profile rather than a new
interaction architecture. Builder creation emits caller-selected canonical flat or normalized layouts.
Transactional CRUD is allowed only for exact recognized writable schemas; arbitrary valid view-backed
schemas stay read-only and have an explicit safe canonical rewrite path. Unknown metadata and unrelated
SQLite objects are preserved without reverse-engineering update SQL or granting execution authority.
Pinned Jackson Core handles bounded vector/UTFGrid JSON inside the already optional Xerial adapter.
The live container, metadata, media, raster, vector, UTFGrid, builder, transaction, limit, and evidence
matrix is recorded in `modules/mundane-map-io-mbtiles-xerial/CAPABILITIES.md`.

Cards: G19-160 through G19-169.

### `mundane-map-workspace`

The custom `.mmap.xml` v1 grammar has no version migration and persists only a small local map
composition. Visibility, wrap, labels/rules, elevation layers, editable bindings, selection/tool
state, resource integrity, portable packaging, locking, recovery, and backup policy are deliberately
absent. The approved product boundary remains a project-native complete save/restore format, not an
OGC interchange format. OGC Web Map Context and OWS Context import/export are explicit exclusions;
they must not be implied by XML, GeoJSON, service-reference, or packaging support. Version 2 retains
canonical hardened XML so the production module stays JDK-only and v1 migration remains direct. Plain
`.mmap.xml` remains the readable/external-reference form; a separate custom `.mmapz` ZIP package embeds
the same XML manifest plus authorized portable resources. Neither form is described as a standard.
Portable entries require SHA-256 integrity digests. Built-in signing, encryption, key/certificate
management, trust stores, revocation, and timestamping are excluded; applications may install an
explicit bounded verifier without changing the canonical package or granting resource authority.
The native format persists committed editable content/references, layer and portrayal state,
viewport/wrap, selection identities, the active tool kind, and stable tool preferences. Undo/redo
history, unfinished edits or measurements, hover, pointer capture, previews, pending work, caches,
workers, and other live session state are deliberately transient; a reopened tool is idle. Remote
HTTPS endpoints, service selections, request policy, and opaque host-resolved credential aliases may
be stored as inert configuration. Secrets and credential-store locations never are. Opening performs
no network access until the host authorizes the endpoint and supplies any credential explicitly.
Each package resource is explicitly `EMBED` or `REFERENCE`. Embedded local datasets carry the complete
adapter-declared file/sidecar resource set as opaque media-typed, hashed bytes; the package never
auto-downloads, transcodes, or silently takes a partial dataset. Per-entry and aggregate limits apply.
Version 2 has a closed core plus bounded namespace-aware optional extension values. Unknown required
core content fails closed; unknown optional extensions round-trip semantically and typed handling
requires explicit codecs. Extensions grant no external authority. Downgrades report every loss or fail.

The live state, version, reference/authority, package, integrity, transaction, lifecycle, and evidence
matrix is recorded in `modules/mundane-map-workspace/CAPABILITIES.md`.

Cards: G19-170 through G19-179.

### `mundane-map-vaadin`

G18-061 already owns publication and isolated offline frontend completion and must not be duplicated.
Beyond it, the component needs a WCAG 2.2/assistive-technology contract and a broader mobile/touch/pen
browser matrix. It must also track every advanced API/core portrayal, geometry, and raster capability
introduced by G19 without leaking the private protocol.

The approved browser target is the complete pinned Vaadin 25 compatibility matrix: evergreen desktop
Chrome and Chromium Edge, evergreen Firefox plus ESR, Safari 17+, evergreen Android Chrome, and mobile
Safari 17+. Playwright Chromium/Firefox/WebKit automation is required but does not substitute for
version-stamped real Safari/macOS, Edge/Windows, Android Chrome, or iOS/iPadOS Safari evidence. Products
outside that upstream matrix are not claimed. The module-local intent and evidence boundary are recorded
in `modules/mundane-map-vaadin/CAPABILITIES.md`. The focused assistive-technology matrix covers NVDA with
Chrome and Firefox ESR, JAWS with Chrome, Narrator with Edge, VoiceOver with Safari on macOS and iOS/
iPadOS, and TalkBack with Chrome on Android. JAWS and all real-platform/product combinations are explicit
version-stamped human evidence rather than claims inferred from automated browser engines.

Canvas 2D remains the required renderer and compatibility fallback. The approved long-term direction
introduces a renderer-neutral prepared scene and an optional production WebGPU backend with bounded GPU
resources, project-owned validated WGSL, device-loss recovery, hostile-input treatment and Canvas parity.
WebGPU is never required for the Vaadin 25 baseline. WebGL2 is added only after a separate measured
compatibility decision; it is not silently committed as a third renderer.

The reusable adapter does not register a service worker or claim standalone runtime-offline operation.
PWA scope, manifests, cache/update policy and offline resources remain host-owned. The adapter documents
safe immutable assets, excludes session/authorization-bound resources from generic caching, and provides
generation-safe disconnect/reconnect behavior. This is distinct from G18-061's build-time offline
reproducibility.

The supported public surface remains the Java Flow component. The bundled custom element, private closed
scene/interaction protocol, worker messages, GPU prepared data and resource handshake are version-matched
implementation details, not an independently published JavaScript/Web Component API. Internal schemas
remain explicit and hostile-input tested without granting direct callers a compatibility contract.

Remote protocols, redirects, credentials, query/cache policy and decoder authority remain on Java/server
lanes. The browser contacts only bounded same-origin session-authorized component resources; large payloads
may use those resource responses rather than Flow RPC, but scene data never grants direct remote network
authority or duplicates service adapters in JavaScript.

G19 transport becomes a private hybrid: small Flow JSON control manifests refer to exact-length packed
same-origin binary resources, and an acknowledged generation may receive a bounded atomic semantic patch.
Any generation gap, incompatibility, failed validation or uneconomic patch falls back to a complete
snapshot. Candidate manifests/resources preflight before visible or interaction state changes, and evidence
separates logical, control, binary, full, patch, transfer and retained-memory costs.

Authoritative map text uses the shared neutral shaped/placed glyph result from explicitly registered fonts.
Vaadin Canvas/WebGPU paint bounded glyph outlines or atlases and retain the original Unicode semantics for
accessibility; browser-local font substitution and `fillText` do not determine placement, collision or hit
geometry. Ordinary HTML controls may continue using browser-native text.

Canvas and WebGPU remain complete 2D renderers. Elevation-derived 2D products are in scope; globe,
perspective terrain meshes, extrusion, models, sky/lighting, occlusion and depth picking are excluded.
Introducing 3D requires a future whole-project neutral API/core/query/edit/persistence/renderer/export
architecture gate rather than a private Vaadin extension.

Mouse and touch are supported; pen/stylus input is deliberately excluded rather than partially forwarded.
Pen pointer streams are ignored or safely cancel an ambiguous active gesture, and the module makes no
claims for tip/barrel/eraser, pressure, tilt, twist, palm rejection or digital ink. Any future expressive
pen support requires a separate cross-project neutral input/stroke decision.

Touch navigation is limited to translation and scale: one-finger pan/tool taps, two-finger centroid pan plus
pinch zoom, and bounded double-tap zoom. Rotational contact movement is ignored and no private browser map
bearing exists. A rotated viewport would require a future whole-project neutral viewport decision.

Ordinary mouse/touch pans may continue with strictly bounded kinetic motion. It has fixed velocity/duration/
distance/work ceilings, no inertial zoom or bounce, no tool/edit use, immediate lifecycle/input cancellation,
one final authoritative settle, and is disabled under `prefers-reduced-motion`.

The client may retain a bounded session-memory cache of neighboring server-authored prepared spatial
chunks. Complete semantic keys include source/style/font/CRS/wrap/scale/revision state; overlap preserves
logical/visual identity, and invalidation/release is atomic. It is optional, non-persistent and does not
grant browser source/query authority.

The public Java component exposes a small stable host-theming contract for focus, loading, error/empty and
accessibility/interaction chrome through closed theme variants and CSS tokens/parts. It cannot restyle map
portrayal, labels, z-order, hits, private canvases or protocol/GPU resources.

All built-in human-facing component, status and accessibility text uses an explicit caller Java message
provider with a complete default English catalog, stable typed keys/parameters and explicit session locale.
Machine diagnostics remain locale-independent, and neither JVM nor browser ambient locale is authority.

The Java API also offers a bounded browser-native PNG capture of the currently accepted view. It is a
same-session convenience result with explicit pixel/byte/time/memory limits, not a deterministic Java image
writer, archival export or cross-browser pixel claim; JPEG and WebP capture remain excluded.

Cards: existing G18-061 plus G19-180 through G19-189.

### `mundane-map-architecture-tests`

The module strongly enforces the current dependency and forbidden-API graph, but it does not govern
semantic-version compatibility, JPMS descriptors, or accidental public binary/source surface drift
against the last released baseline.

The approved JPMS policy is hybrid and evidence-based. Published modules with a module-path-clean complete
runtime graph receive explicit minimal descriptors; adapters with pinned dependencies that still require
automatic/classpath behavior receive stable `Automatic-Module-Name` identities and real automatic-module
consumer tests. Broad `opens` and false strong-encapsulation claims are prohibited. Support/examples do not
gain a published module contract. The governance matrix is recorded in
`modules/mundane-map-architecture-tests/CAPABILITIES.md`.

Compatibility uses an explicit two-phase SemVer policy. Before 1.0, only a reviewed `0.MINOR.0` may break;
patches remain binary/source compatible and every break still carries migration/release documentation. At
and after 1.0, breaking/compatible-addition/fix changes require major/minor/patch respectively, with normal
deprecation retention and only narrow documented security/correctness emergency exceptions.

Pinned Apache-2.0 Revapi Java analysis is the primary build-time released-JAR comparison engine, supplemented
by project checks for stricter language, JPMS, service/resource, diagnostic and behavioral rules. It never
enters production graphs, its default pre-1.0 policy is overridden, and its classifications never create an
automatic compatibility exception.

Each artifact's checked-in manifest names one immutable compatible released coordinate plus digest and
POM/module provenance; connected and offline builds resolve/stage those exact bytes, never dynamic latest or
the current build. A reviewed deterministic signature snapshot exists only before a first publication, and
the release workflow advances baselines after publication/consumer verification. The strict source policy
treats enum/sealed exhaustiveness, record shape, overload ambiguity, generics, checked exceptions, nullness,
annotations/constants, services and module exports as governed rather than relying on linkage alone.

The architecture module deliberately stops short of a general behavioral-versioning framework. Diagnostics,
defaults, ordering, limits, ownership, threading, lifecycle and domain semantics stay with their owning
module's documentation and regression tests. Architecture governance is decomposed into API/SemVer, JPMS,
and release-integration closeout rather than duplicating every project contract.

Cards: G19-190 through G19-192.

### `mundane-map-native-tests`

The closed-world smoke is Linux x86-64-focused and excludes non-targeted adapters by design. The
approved support profile covers standard dynamically linked executables built and run on native
Linux x86-64/AArch64, macOS x86-64/AArch64, and Windows x86-64 hosts. It does not claim Windows ARM,
cross-compilation, static/musl or mostly-static distribution, native desktop windows, shared-library
artifacts, or incidental compatibility of excluded adapters.

Feature-complete native verification therefore requires more than a wider CI matrix. It must pin
each host toolchain and compatibility CPU baseline, mechanically close reachability/resources and
the native-target registry, execute equivalent semantic and hostile corpora, isolate platform-sensitive
filesystem/charset/XML/TLS/headless-rendering behavior, and archive reproducible release evidence.
The authoritative matrix and exclusions are recorded in
`modules/mundane-map-native-tests/CAPABILITIES.md`.

Cards: G19-200 through G19-204.

### `mundane-map-performance-tests`

The deterministic evidence lane remains valuable for integration-scale semantic/work observations,
but its custom warmup loop is not the microbenchmark standard. The approved profile adopts pinned
OpenJDK JMH for isolated algorithms and bounded operations while retaining the existing runner only
for end-to-end integration evidence. Browser and Native Image measurements stay in their owning real
environment lanes; JFR remains diagnostic rather than the primary score source.

Performance scores are explicitly non-normative. Pull requests verify harness configuration,
coverage, semantic correctness, bounded work, result validity, and cleanup, but timing, allocation,
GC, and memory changes never fail release gates. The project does not maintain dedicated comparison
hardware, performance baselines, statistical regression gates, rebasing, waivers, or service-level
guarantees; all scores remain environment-specific informational evidence.
The authoritative methodology and interpretation boundary are recorded in
`modules/mundane-map-performance-tests/CAPABILITIES.md`.

Cards: G19-210 through G19-214.

## Completion rule

G19-999 is the external-expert closeout. It may complete only after every module card and G18-061 are
complete, each standards claim names exact conformance classes and exclusions, interoperability and
hostile-input evidence is current, and no module claims completeness merely because unsupported input
is rejected cleanly.
