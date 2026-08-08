# G18 Vaadin browser frontend

## Status and objective

This document is the approved implementation profile for a reusable browser map component and a runnable
Vaadin example. It records the user's decision that a commercial map component is unacceptable.
G18 therefore uses Vaadin Flow's open component-integration surface but does not use Vaadin Map,
Vaadin TestBench, or another commercial Vaadin artifact.

The intended result is one optional `mundane-map-vaadin` adapter backed by a project-authored HTML
Canvas web component. The adapter reuses the existing geometry, CRS, source, portrayal, symbol,
interaction, editing, raster, elevation, workspace, diagnostics, cancellation, and limit contracts.
It does not turn Vaadin, JavaScript, browser, or JSON types into general `mundane-map-api` contracts.

G18 is a Level 2 JVM/browser capability. It does not change the JDK-only Level 1 runtime or the
existing Linux Native Image support statement.

## Selected direction

The approved runtime is:

```text
browser
  project-authored <mundane-map-canvas> custom element
  Canvas 2D drawing, local pan/zoom, resize, pointer capture
                         ^
                         | bounded versioned scene updates and events
                         v
mundane-map-vaadin
  Vaadin Flow component, session/binding ownership, query coordination,
  projection, portrayal, diagnostics, cancellation, and tool host
                         ^
                         |
                         v
mundane-map-api + mundane-map-core
  ordinary format sources + workspace sessions

mundane-map-awt remains an independent desktop presentation adapter
```

Vaadin supplies application routing, layouts, controls, component lifecycle, server communication,
upload/download facilities, and optional low-rate server push. It is not the map engine. The local
web component supplies only the browser presentation behavior that Swing/Java2D cannot provide.

The initial profile deliberately has no browser map-engine dependency. OpenLayers, Leaflet,
MapLibre GL JS, and similar packages are not required to demonstrate the second presentation
consumer and would otherwise introduce a second geometry, projection, style, selection, and source
engine. A later evidence-backed task may reconsider that decision without changing G18's public
contracts.

## Dependency and license boundary

The approved platform BOM is `com.vaadin:vaadin-bom:25.2.3` and the approved application build
plugin is `com.vaadin.flow` version `25.2.4`. With that BOM, the reusable adapter has exactly one direct
external dependency, `com.vaadin:flow-server`, resolved to `25.2.4`; it does not depend on the
`com.vaadin:vaadin` aggregate. The complete resolved production graph, artifact SHA-256 values,
licenses, service-provider entries, and roles are frozen in
`verification/G18-001-open-dependency-profile.md`. The Gradle plugin is application build tooling
and is not applied to or published by the reusable adapter. The Spring Boot/Vaadin application
runtime belongs to the example, not the reusable component's public implementation graph.

The dependency review inventories Maven and frontend packages, licenses, checksums, service
providers, reflection/resource scanning, build-time Node requirements, and offline behavior. It
must mechanically reject `com.vaadin:vaadin-map-flow`, `@vaadin/map`, TestBench, and any other
commercial artifact. The project does not inherit a map-data subscription, remote tile source, API
key, analytics service, telemetry client, or production basemap.

The adapter is an `OPTIONAL_ADAPTER`, is `nativeTarget: false`, and may expose Vaadin types only from
its own package. `mundane-map-api`, `mundane-map-core`, every format adapter, `mundane-map-awt`, and
the workspace module remain unaware of Vaadin and browser types. Architecture checks enforce that
direction.

## Module and artifact shape

The first working slice creates:

- `modules/mundane-map-vaadin`, a published optional adapter depending on API, core, and the approved
  Flow surface;
- `io.github.mundanej.map.vaadin.MundaneMap`, the Java Flow component;
- adapter-owned layer bindings and immutable configuration/diagnostic values only where an
  observable browser workflow requires them;
- a local JavaScript module under `META-INF/frontend` that registers
  `<mundane-map-canvas>`; and
- `examples/vaadin-viewer`, a non-published Spring Boot/Vaadin application added only when it opens
  and displays a real useful map.

The web component is packaged inside the adapter JAR and loaded with Vaadin's local `@JsModule`
mechanism. It is not separately published to npm. Public Java APIs receive and return ordinary
mundane-map or Vaadin values; they do not expose the private scene protocol.

## Server and browser responsibilities

### Server-owned behavior

Java remains authoritative for:

- source opening, limits, ownership, cursor serialization, cancellation, and diagnostics;
- CRS recognition and registered source-to-map/display operations;
- viewport query envelopes, aggregate query accounting, stable logical feature identity, and
  horizontal-wrap planning;
- portrayal selection, required-attribute projection, symbol/profile resolution, and elevation
  rasterization;
- selection state, tool routing, measurement state, feature-edit transactions, snapping, and
  undo/redo; and
- workspace parsing, guarded paths, explicit source/catalog registries, and session cleanup.

### Browser-owned behavior

The custom element owns only presentation-local state:

- Canvas sizing, device-pixel-ratio backing storage, clipping, and repaint scheduling;
- immediate pan, wheel/pinch zoom, focus, cursor, and pointer-capture feedback;
- map-coordinate-to-screen affine conversion for the current projected viewport;
- drawing accepted vector paths, symbols, labels, overlays, and detached raster windows; and
- emitting settled viewport, pointer, command, resize, and lifecycle events.

Continuous browser gestures must not wait for server round trips. A trailing settled-viewport event
starts a new bounded Java query generation. Clicks, semantic tool commands, edit commits, and
explicitly throttled hover events cross the Flow boundary. High-rate live-track frames remain out of
scope; ordinary Flow push is suitable for low-rate state and telemetry, not a 60 Hz frame stream.

## Approved support surface

The Java baseline is 21. Browser support follows Vaadin 25's evergreen Chrome, Edge, and Firefox
policy plus Safari 17 or newer. G18-060 automates the then-current Playwright Chromium and Firefox
binaries on Linux; Edge and Safari remain compatibility claims backed by the closed standards used
by the component, not automated operating-system coverage. Canvas 2D, `ResizeObserver`, Pointer
Events, `devicePixelRatio`, `requestAnimationFrame`, `AbortController`, `fetch`, typed arrays, and
ES modules are required. A missing required API produces `BROWSER_CAPABILITY_UNSUPPORTED` before a
scene is accepted.

The approved geometry surface is point, multipoint, line string, multiline string, polygon, and
multipolygon with holes. The approved CRS surface is explicit EPSG:4326 and EPSG:3857 operations
already registered by core. The approved vector surface is the existing built-in immutable marker,
line, fill, composite, endpoint, hatch, raster-catalog icon, portrayal, and point-label profiles.
Interaction is single hover and single selection, pointer/touch navigation, wheel/pinch zoom,
keyboard pan/zoom, one toolkit-neutral tool router, measurement, and immutable point-only editing.
Raster/elevation support is detached bounded RGBA windows; horizontal repetition is explicit and
opt-in. Workspace, upload, and SVG export support is limited to the guarded workflows named in the
runnable-example section.

Excluded are remote basemaps, remote sprites/glyphs/fonts, credentials, analytics, telemetry,
JavaScript projection or style engines, executable expressions, arbitrary SVG/HTML/CSS/URLs,
custom renderer code, 3D/WebGL, rotation or pitch of the map viewport, geocoding, routing, lasso or
box selection, multi-selection, line/polygon editing, source write-back, collaboration, offline
service workers, mobile-native packaging, Native Image, pixel-identical fonts, and portable
latency/FPS claims.

## Private scene protocol

The adapter and its bundled web component use one private, versioned protocol. It supports a full
scene replacement for the first slice and stable-ID patches only after measured evidence shows the
need. Every message carries a protocol version, component/session generation, viewport generation,
and bounded layer/feature/primitive counts.

Values are finite numbers, booleans, bounded strings, packed coordinate arrays, exact logical IDs,
and closed enum tokens. JavaScript, HTML, CSS, URLs, callbacks, and executable expressions are never
accepted from source attributes. Server calls pass structured arguments through Vaadin's supported
element-function mechanism rather than interpolating script text.

Protocol version `1` uses structured Flow element-function calls. Each complete scene starts with a
header and is validated into detached Java values before the current scene changes. Client events
carry the component generation, accepted scene generation, viewport generation, and a strictly
increasing unsigned sequence represented within JavaScript's safe-integer range. No source value is
ever used as a JavaScript property name.

The closed limits are:

| Resource | Limit |
| --- | ---: |
| Canonical logical wire size per scene or event | 64 MiB |
| Layers / logical features / browser primitives per scene | 64 / 50,000 / 200,000 |
| Coordinate pairs / path commands per scene | 2,000,000 / 2,000,000 |
| ID / attribute name / attribute string / diagnostic detail | 256 / 128 / 4,096 / 512 UTF-16 code units |
| Attributes per feature / aggregate attribute characters | 64 / 8,000,000 |
| Label candidates / accepted labels | 20,000 / 5,000 |
| Catalog icons / one icon encoded bytes / aggregate icon bytes | 2,048 / 1 MiB / 32 MiB |
| Raster width or height / pixels / decoded bytes per window | 16,384 / 16,777,216 / 64 MiB |
| Aggregate live scene resources | 128 MiB |
| CSS canvas side / device-pixel ratio / backing pixels | 16,384 / 4 / 67,108,864 |
| Visible source bindings / query records / live cursors per source | 64 / 50,000 / 1 |
| Pending generations | one active and one newest queued |
| Settled viewport / hover / tool pointer events | 10 / 20 / 120 per second |
| Query deadline after dispatch | 30 seconds |
| Edit history / snap candidates | 256 revisions / 4,096 candidates |

Counts are checked before multiplication and allocation. The first exceeded aggregate wins; limits
are applied in the table's order and then layer, feature, role, and coordinate order. Malformed,
oversized, stale, duplicate, and out-of-order client events are rejected predictably. No partial
scene becomes current after failure or cancellation.

Canonical logical wire size is counted without materializing a second message: each number or
generation is eight bytes, each boolean or closed enum is one byte, each string is four bytes plus
its strict UTF-8 byte length, each array is four bytes plus its elements, and each fixed-schema
record is the sum of its values in protocol field order (field names and transport framing count as
zero). Null and absent values are forbidden. Both Java and JavaScript use this formula with checked
integer addition before handing a structured value to Flow; actual transport size may be larger but
can never expand an accepted collection or string beyond its separate count limit.

Event rates use one token bucket per event class and component generation, measured by the server's
monotonic clock. Capacity and refill per elapsed second are respectively 10/10 for settled viewport,
20/20 for hover, and 120/120 for tool-pointer events; buckets start full and cap at capacity. The
browser mirrors the buckets for traffic reduction, but the server is authoritative. An empty
settled or hover bucket retains only the newest event and submits it when one token refills; replaced
events are silently coalesced. The first tool-pointer event without a token cancels capture and the
active gesture and reports `EVENT_RATE_EXCEEDED`; subsequent events are ignored until cancellation
is acknowledged. Test clocks advance in nanoseconds, and detach/reattach creates fresh buckets.

Stable diagnostic precedence is `CLOSED`, `DISABLED`, `PROTOCOL_VERSION_UNSUPPORTED`,
`STALE_GENERATION`, `EVENT_SEQUENCE_INVALID`, `EVENT_RATE_EXCEEDED`, `LIMIT_EXCEEDED`, `NON_FINITE_VALUE`,
`DUPLICATE_ID`, `CRS_OPERATION_UNAVAILABLE`, `SOURCE_FAILURE`, `SYMBOL_UNSUPPORTED`,
`BROWSER_CAPABILITY_UNSUPPORTED`, and `CLIENT_FAILURE`. Validation never includes source values,
paths, URLs, stack traces, or message bodies in public detail. A newer successful generation clears
only the transient component diagnostic; per-source reports retain their own latest accepted
outcome.

The protocol remains package-private and frontend-private for G18. A public renderer-neutral scene
model is considered only if a third independent consumer demonstrates the need.

## Viewport, CRS, and querying

The browser viewport uses projected map-CRS coordinates and the same center,
world-units-per-logical-pixel, width, and height convention as `MapViewport`. CSS pixels are logical
screen pixels; device pixels affect only Canvas backing resolution. Resize preserves center and
scale. Fit is computed by Java from authoritative data bounds and applied as one viewport update.

The initial supported CRS pair is the existing explicitly registered EPSG:4326/EPSG:3857 profile.
Unknown and missing source CRS states retain their current failures; the browser does not guess or
load projection definitions.

Each component owns an externally serialized query coordinator. A settled viewport cancels or
supersedes the prior generation, queries every visible binding using only required attributes,
projects records, resolves portrayal, and publishes one complete accepted generation. Cursor and
source ownership follows the existing owned/borrowed distinction. Detach, route removal, session
close, and explicit component close cancel work, invalidate tokens, remove listeners and pointer
capture, revoke adapter resources, clear queued paints and measured labels, and close only owned
resources. Reattach creates a new component generation and never revives a prior token, event
sequence, query, or resource URL.

## Vector, symbol, and label profile

The first vector slice supports point, line string, and polygon snapshots with solid screen-pixel
marker, line, and fill symbols. Feature-source completion adds all six supported geometry families,
holes, deterministic layer/source order, clipping, stable IDs, and source reports.

Later symbol completion covers project-authored vector marker paths, placement, map/screen units,
rotation, opacity, composites, endpoint markers, solid outlines, bounded hatches, explicit-catalog
raster icons, thematic/rule portrayals, and point labels. Unsupported custom renderers fail with a
stable diagnostic; the browser never executes renderer-supplied code.

The accepted settled vector scene can be captured as the existing detached
`VectorExportSnapshot`. The SVG module remains the encoder; the Vaadin adapter does not create a
second SVG writer or depend on AWT capture.

Browser text measurement is inherently browser/font dependent. Label layout therefore uses one
bounded measure/placement handshake: Java selects label values and candidates, the bundled client
measures the configured closed font profile, Java or a parity-tested closed client implementation
applies the existing greedy ordering, and the accepted placements become immutable for that scene
generation. G18 claims structural and tolerant layout agreement, not cross-platform glyph-pixel
identity.

## Interaction, measurement, and editing

The adapter converts closed client events into the existing toolkit-neutral tool events. One
`MapToolRouter` per component owns lifecycle, capture, quarantine, cursor intent, and default-route
suppression. Default browser navigation runs only when the router permits it.

Hit testing mirrors reverse paint order and retains logical identity across multipart and repeated
display copies. Hover is throttled and disposable; selection is authoritative Java state and paints
after ordinary layers. The browser does not get permission to nominate an arbitrary logical ID that
was not present in its accepted scene generation.

Measurement reuses the current distance strategies and measurement state. Point editing reuses
`FeatureEditSession`, immutable commands, same-CRS snapping, bounded history, and canonical
world-wrap behavior. The adapter adds only browser overlays, pointer conversion, and Flow-facing
commands. Source write-back, line/polygon editing, collaborative edits, and server-side conflict
resolution remain out of scope.

## Raster, elevation, and world wrap

Raster and elevation values remain server-produced detached `RgbaPixelBuffer` windows. The adapter
offers a bounded same-origin binary resource for accepted windows; the browser fetches immutable
bytes into `ImageData` and paints the supplied map-grid placement. The final profile must choose and
document an exact binary framing, authorization/token lifetime, cache key, cancellation behavior,
and content/security headers. It must not require AWT, ImageIO, a PNG encoder, a data URL, or a
third-party browser map source.

This boundary begins after a caller has opened a `RasterSource`. Existing encoded PNG/JPEG-backed
sources retain their explicit `EncodedRasterDecoder` requirement; G18 neither moves the AWT
ImageIO decoder into the web adapter nor promises a new codec. The example can demonstrate
JDK-decoded GeoTIFF/elevation or synthetic sources without broadening that policy.

Elevation uses the existing colorization/hillshade algorithms before transport. Raster opacity,
nearest/bilinear request policy, affine placement, missing windows, diagnostics, and cache ownership
retain their current meanings.

Horizontal repetition remains disabled by default and requires the existing view-plus-binding
opt-in. Java owns canonical queries, deduplication, seam splitting, copy bounds, and raster
compatibility. The browser receives checked translated display copies and cannot infer global
layers.

## Runnable example

`examples/vaadin-viewer` demonstrates the released adapter rather than defining its behavior. The
application provides:

- an in-memory introductory map that opens without network access;
- explicit examples of bounded shapefile, GeoTIFF/elevation, and workspace sources from checked
  fixtures or caller-selected server-local paths;
- responsive map, toolbar, fit/zoom controls, layer visibility/order, coordinate readout, selection
  inspector, source diagnostics, measurement, and point editing;
- bounded uploads staged under a fresh application-owned directory with exact suffix/sidecar
  handling, no trust in client paths or names, and deterministic cleanup; and
- an SVG export/download action using the existing vector-export module where the visible content
  is representable.

The example does not ship a remote basemap, credential, production authentication policy, database,
or multi-user collaboration model. Its README distinguishes server-local paths, browser uploads,
per-UI resources, and production deployment responsibilities.

## Verification strategy

Fast Java tests remain in ordinary `check` and `qualityGate`: conversion, limits, protocol
validation, ownership, query generations, portrayal, interaction, editing, raster framing, and
server-side Vaadin component lifecycle. Frontend logic receives deterministic tests for viewport
math, transforms, draw order, stale generations, and malformed messages.

`G18-060` creates a separate `vaadinBrowserTest` lane using open-source Playwright, not TestBench.
It starts the real example on a loopback random port and exercises Chromium and Firefox when their
explicitly installed binaries are available. The lane covers resize, local pan/zoom, settled query,
vector/raster display, selection, measurement, editing, upload cleanup, detach/reattach, and no
network-basemap behavior. Browser installation/download is explicit and never occurs during the
normal gate.

Tolerant rendering comparisons use geometry, ordering, color-region, hole, label-envelope, and
interaction invariants rather than whole-canvas pixel hashes. A bounded browser performance scenario
records scene sizes, transferred bytes, query/paint latency, frame responsiveness, and retained
memory without establishing portable wall-clock thresholds.

Publication closeout stages the adapter, verifies its JAR frontend resources and POM dependency
surface, and runs a standalone Java 21 Vaadin consumer from staged Maven artifacts. Offline
verification must account for both Maven and frontend inputs without weakening the repository's
isolated-resolution policy.

## Task graph and ownership

```text
G18-001
   -> G18-010 -> G18-011 -> G18-020 -> G18-021 -> G18-022 -> G18-030 -> G18-031
                                                           \-> G18-040
G18-031 + G18-040 -> G18-041 -> G18-050 -> G18-051 -> G18-052 -> G18-060 -> G18-061
```

The serial vector path establishes the shared component, protocol, bindings, symbols, portrayal,
resources, labels, export capture, and event host. After `G18-022`, interaction and raster work are logically parallel, but both touch the
component protocol and frontend module; one integration owner must serialize those files.
`G18-050` through `G18-052` converge and complete the example, `G18-060` owns browser evidence, and
`G18-061` owns publication/offline closeout.

The named G18-001 HITL checkpoint—**open-source Vaadin dependency, browser component profile,
private protocol, supported surface, and task graph approval**—was accepted by the maintainer's
2026-08-08 directive to complete G18-001 through G18-031. No dependency, production module, or
example preceded that approval.

## Reference material

- Vaadin local web-component packaging and Java wrapper:
  <https://vaadin.com/docs/latest/building-apps/components/wrap-web-component>
- Vaadin component events and debouncing:
  <https://vaadin.com/docs/latest/flow/component-internals/events>
- Vaadin Playwright testing without TestBench:
  <https://vaadin.com/docs/latest/flow/testing/playwright>
- Vaadin 25 Java/platform comparison:
  <https://vaadin.com/docs/latest/upgrading/version-comparison>
