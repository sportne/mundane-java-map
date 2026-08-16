# Vaadin browser-adapter capability intent

`mundane-map-vaadin` is the optional Vaadin Flow adapter for MundaneJ's toolkit-neutral map model. It
owns a project-authored browser custom element, a private closed scene/interaction protocol, guarded
per-session resources, and Flow lifecycle integration. It is not a general JavaScript map engine,
public web protocol, web-component compatibility layer, or replacement for the neutral API/core
contracts.

The released G18 surface is a bounded Canvas 2D implementation for vectors, labels, raster/elevation,
feature sources, wrap, hit testing, selection, measurement and point editing. G19 completion must carry
the completed neutral G19 surface through the browser where applicable, close accessibility and input
gaps, and retain fail-closed protocol, ownership and production-scale behavior.

The root README describes released behavior. Target rows become release claims only after their cards
and required platform evidence close.

## Browser and platform support

The approved browser baseline is the complete browser matrix supported by the pinned Vaadin 25 line,
not a Chromium-only profile. The upstream reference is the [Vaadin compatibility guide][vaadin-compat].

| Platform | Approved target | Required evidence boundary |
| --- | --- | --- |
| Desktop Chrome | Evergreen release on Vaadin-supported desktop operating systems | Automated Chromium conformance plus a current real-Chrome smoke |
| Desktop Firefox | Evergreen release and current ESR | Automated Firefox conformance plus an ESR smoke |
| Desktop Safari | Safari 17 and later on supported macOS | Automated WebKit is useful preflight; a real Safari/macOS run is required for the support claim |
| Desktop Edge | Evergreen Chromium-based Edge on Windows | Chromium evidence is shared, but a real Edge/Windows smoke is required for Edge-specific integration |
| Mobile Chrome | Evergreen Chrome on supported Android | Emulation is preflight only; essential touch, viewport and lifecycle workflows require a real Android run |
| Mobile Safari | Safari 17 and later on supported iOS/iPadOS | Emulation is preflight only; essential touch, viewport and lifecycle workflows require a real iOS/iPadOS run |

Internet Explorer, legacy EdgeHTML, mobile Firefox, embedded webviews, Electron and browsers outside
Vaadin's supported matrix are not claimed. A host may run them, but failures there are not compatibility
defects unless a future profile explicitly adds them.

Playwright's Chromium, Firefox and WebKit engines provide the mandatory repeatable automation lane.
They do not by themselves prove Chrome, Firefox ESR, Safari, Edge, Android Chrome or iOS Safari product
compatibility. Platform-product smoke evidence is version-stamped, and the documentation distinguishes
automated conformance from manual/device evidence.

## Standards and capability matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Browser compatibility | Chromium and Firefox production automation with a project support profile | Full pinned Vaadin 25 browser matrix above, with cross-engine automation and real-product evidence | G19-180, G19-189 |
| Accessibility | Basic name/status/keyboard workflow evidence; no WCAG conformance claim | Applicable component-level WCAG 2.2 A/AA matrix, essential-workflow alternatives and the focused assistive-technology matrix below | G19-181, G19-182 |
| Pointer input | Mouse, bounded pointer routing and existing touch-compatible primitives | Complete mouse and touch behavior, multi-pointer loss/cancellation, coarse targets and mobile viewport handling; pen explicitly unsupported | G19-180, G19-183 |
| Responsive user interface | Responsive example shell and local Canvas viewport | Browser zoom/reflow, orientation, high-DPI, forced colors, contrast and reduced-motion behavior without functional loss | G19-181, G19-183 |
| Scene capability | Closed G18 vector/label/raster/elevation/source/edit/wrap protocol | Explicit encode/render/hit/edit/export treatment for every completed applicable G19 neutral construct | G19-184 |
| Performance | Frozen G18 scene/resource/event ceilings and browser evidence | Named logical/visual/query/transfer/paint/memory ceilings, backpressure and soak on the supported browser profile | G19-185 through G19-189 |
| GPU rendering | Unsupported; Canvas 2D only | Renderer-neutral prepared scene plus an optional production WebGPU backend with exact Canvas fallback and cross-renderer semantic/visual evidence | G19-188 |
| Dimensionality | Two-dimensional map presentation | Complete 2D Canvas/WebGPU presentation; elevation-derived 2D effects allowed, globe/terrain mesh/extrusion/model/perspective 3D excluded | G19-184, G19-188 |
| Runtime offline/PWA | No component service worker or standalone-offline claim | Host-owned PWA integration boundary, safe static-asset caching rules, session-resource exclusions and authoritative reconnect behavior | G19-183 |
| Public surface | Public Java Flow component; private browser protocol | Keep Java/Flow as the only supported adapter API; version-lock the element, workers, resources and renderer messages as private implementation | G19-184, G19-185 |
| Network authority | Server-side source/query/auth policy; same-origin session resources | Retain server-only remote protocols and credentials; browser fetches only bounded same-origin session-authorized component resources | G19-185, G19-186 |
| Scene transport | Full closed JSON scene replacement | Hybrid JSON control plus packed binary resources, acknowledged atomic patches and full-snapshot recovery | G19-185 |
| Navigation reuse | Current accepted scene only | Bounded session-memory prefetch/cache of server-authored semantic spatial chunks; complete no-cache fallback | G19-186 |
| Map text | Browser-measured point labels with browser font rendering | Consume neutral deterministic shaping/placement from registered fonts and paint bounded glyph outlines/atlases with retained semantic text | G19-184 |
| Host theming | Internal custom-element styling and example CSS | Small stable CSS-part/token plus Java theme-variant contract for component chrome and accessibility states; no portrayal override | G19-181 |
| Localization | Fixed implementation/example English text | Caller-supplied Java message provider, default English catalog, explicit locale and locale-neutral diagnostic codes | G19-181 |
| Current-view raster capture | Unsupported | Bounded browser-native PNG convenience capture; explicitly non-authoritative and non-reproducible | G19-187 |
| Publication/offline | Staged binary/source/Javadocs/resource inventories, exact Flow graph, Java 21 consumer, and isolated offline production frontend/normal gate | Reproducible published adapter and isolated offline Maven/frontend consumption; no ambient CDN or package installation | G18-061 |

## Browser contract

- Browser feature detection fails closed with a stable diagnostic when a required Canvas, resource,
  pointer or lifecycle primitive is unavailable. User-agent sniffing does not silently broaden support.
- The client validates the complete private payload before changing the accepted scene. Generation,
  viewport, resource and interaction state transitions remain atomic and bounded under malformed,
  stale, repeated and adversarial messages.
- Rendering and hit/edit behavior consume server-authored, toolkit-neutral values. The browser does not
  evaluate caller JavaScript, arbitrary style code, unregistered URLs, shaders or executable metadata.
- Browser resources remain same-session, same-origin, expiring, exact-length and explicitly owned.
  Resource locators and scene data never grant network, filesystem, credential or decoder authority.
- Navigation and tool feedback may be local for responsiveness, but authoritative coordinates,
  selection, edits and cancellation remain reconciled with the serialized server state.
- Support applies to the component under its documented embedding contract. Host applications remain
  responsible for page language/title, surrounding landmarks, authentication, deployment transport,
  global focus order and any inaccessible controls they add or substitute.

## Public API and private browser protocol

The supported adapter API is the public Java `MundaneMap` Flow component and its documented immutable
bindings, policies, events and lifecycle. The generated custom element is not a standalone supported Web
Component. Its JavaScript methods/properties/events, scene and interaction envelopes, worker messages,
resource URLs and Canvas/WebGPU prepared data are private implementation details delivered and versioned
as one adapter artifact.

Every private channel still has an explicit closed version/profile, exact schema, bounded validation,
generation/lifecycle rules and stable server-side public diagnostic translation. A mismatched, forged,
future or partially upgraded client fails closed. This internal rigor does not create a compatibility
promise for callers that invoke the element or `$server` surface directly.

There is no separately published JavaScript package, framework-neutral lifecycle contract, TypeScript
declaration, browser protocol specification, client extension/plugin interface or semantic-version promise.
Providing any of those requires a separately approved adapter and cannot be inferred from the bundled
frontend source or tests.

## Accessibility and assistive-technology profile

The component targets the applicable WCAG 2.2 Level A and AA requirements, while recognizing that WCAG
conformance applies to complete pages rather than allowing this reusable component to certify an
embedding application. The module publishes a requirement-by-requirement applicability, implementation,
automation, human-evidence and host-responsibility table. It does not make a blanket conformance claim.

The approved assistive-technology combinations are deliberately focused rather than a Cartesian product
of every supported browser and screen reader:

| Platform | Assistive technology | Browser | Evidence |
| --- | --- | --- | --- |
| Windows | Current NVDA | Current Chrome and Firefox ESR | Essential workflow review on both combinations |
| Windows | Current JAWS | Current Chrome | Licensed, version-stamped human review |
| Windows | Current Narrator | Current Edge | Version-stamped essential-workflow smoke |
| macOS | Current VoiceOver | Current Safari | Version-stamped essential-workflow review |
| iOS/iPadOS | Current VoiceOver | Current mobile Safari | Physical-device essential-workflow review |
| Android | Current TalkBack | Current Chrome | Physical-device essential-workflow review |

An assistive-technology support claim requires keyboard discovery, focus and browse/forms-mode behavior,
announced name/role/value/status/error changes, alternatives for spatial inspection and essential actions,
and stable behavior through navigation, measurement, selection, editing, resize and lifecycle changes.
Automated accessibility checks cannot replace these product tests. Combinations outside this table are
not claimed, although standards-based semantics must not deliberately prevent their use.

Keyboard-only operation, browser zoom/reflow, visible focus, non-color cues, contrast/forced-colors,
reduced motion, coarse-target behavior and equivalent non-pointer workflows remain required independently
of screen-reader behavior. The host embedding contract identifies page-level responsibilities such as
language, title, landmark structure, global focus order and surrounding controls.

## Input-device boundary

Mouse and touch are supported input classes. Pen/stylus input is explicitly unsupported: the adapter does
not claim pen hover/contact, tip/barrel/eraser buttons, pressure, tilt, twist, altitude/azimuth, tangential
pressure, contact geometry, palm rejection or digital ink. A browser pointer whose `pointerType` is `pen`
does not enter ordinary map navigation or tool routing. It is ignored before authoritative routing, or
safely cancels an already active mixed/ambiguous gesture without mutating map/edit state.

The client still treats every pointer field as hostile input and bounds tracked identifiers/events even
when the device class is unsupported. There is no implicit mouse emulation support claim for a stylus.
Adding pen later requires a separate decision and, for pressure-sensitive drawing, a cross-project neutral
API/core stroke model rather than browser-only event forwarding.

Touch navigation is translation-and-scale only. One-finger direct manipulation pans or participates in a
tool's documented tap workflow; two-finger gestures combine centroid translation with pinch scale; and a
double tap performs bounded anchor-relative zoom. Rotational movement between two contacts does not create
a map bearing and is ignored while the valid centroid/scale portion continues. Orientation changes resize
the viewport and preserve its neutral center/scale semantics.

The browser never maintains a private rotated camera. `MapViewport` has no bearing, so adding touch rotation
would first require a separate whole-project viewport/query/hit/label/raster/edit/AWT/SVG/workspace decision.
Gesture recognition fixes contact-count transitions, slop, tap/double-tap timing, capture, default-page
scroll/zoom suppression, tool priority, cancellation and final settled synchronization under explicit limits.

Ordinary mouse/touch panning includes bounded kinetic continuation after a qualifying release. Velocity
uses a fixed recent-sample window and explicit maximum velocity, duration, distance and frame/work budgets.
Inertia never follows measurement/edit/tool capture, never applies zoom or elastic boundary bounce, and
publishes one authoritative settled viewport when it finishes.

New input, tool/session/scene changes, detach, hidden/lost focus, connection loss, rejected navigation,
resource failure or close cancels the animation and reconciles the viewport exactly once. The feature is
disabled when `prefers-reduced-motion` requests reduced motion; disabling animation does not remove any
navigation function.

## Compatibility evidence policy

- Pin the Vaadin support source and dependency versions used to derive the matrix. A Vaadin upgrade must
  review browser changes before dependency acceptance; a passing build cannot silently change claims.
- Run deterministic frontend unit/protocol tests and Playwright Chromium/Firefox/WebKit workflows on CI.
  Record actual browser/engine versions and distinguish simulated touch/pen from physical-device tests.
- Run version-stamped real Chrome, Firefox ESR, Safari/macOS, Edge/Windows, Chrome/Android and
  Safari/iOS or iPadOS smoke workflows before closing the browser-support card and before releases that
  materially change input, rendering, resource or lifecycle behavior.
- Essential product-specific workflows cover load/paint, keyboard focus, pointer/touch navigation,
  selection, measurement/editing, resize/orientation, detach/reattach, resource failure and cleanup.
- Unsupported products and untested combinations are stated honestly. Engine substitution is never
  reported as evidence for a branded browser or operating-system integration.

## Rendering-backend direction

Canvas 2D remains the required compatibility renderer, semantic reference and fallback across the full
supported browser matrix. G19 will separate validated immutable scene preparation from backend execution
so that rendering, hit testing, interaction identity, diagnostics and resource ownership do not become
accidentally coupled to Canvas operations.

A separate long-term task adds a production WebGPU backend. It is not a prototype-only or benchmark-only
card: it covers renderer selection, bounded vertex/index/uniform/texture/atlas/storage resources, pipeline
and shader inventories, validated project-owned WGSL, numeric precision, clipping, blending, antialiasing,
color/alpha behavior, raster/elevation processing, labels/symbols, device/context loss, cancellation,
replacement, teardown, diagnostics and hostile resource inputs. It must prove semantic equivalence and
declared visual tolerances against Canvas fixtures and never expose caller shaders or arbitrary GPU code.

WebGPU availability is feature- and device-detected. Absence, denial, software fallback, adapter failure,
limit shortfall or device loss selects or restores the complete Canvas path without losing authoritative
state. WebGPU is therefore not required to satisfy the Vaadin 25 browser baseline.

WebGL2 is not a committed second backend. The WebGPU task measures whether WebGL2 would cover an important
supported-platform performance gap that Canvas cannot. Adding it requires a separate approved decision,
because maintaining Canvas, WebGL2 and WebGPU shader/rendering parity would materially enlarge the public
support and security burden.

Both backends remain two-dimensional map renderers. They may render elevation-derived hillshade, color
relief, contours and other neutral 2D products, but do not introduce a globe, perspective camera, terrain
mesh, building/fill extrusion, 3D model, sky, lighting, occlusion or depth-based picking. Preserved 3D or
non-planar adapter values remain explicitly non-renderable as defined by their owning neutral/format
contracts. A future 3D effort requires a separate project-wide API/core/portrayal/query/edit/persistence/
renderer/export architecture gate; it cannot originate as a private Vaadin capability.

## Runtime offline and PWA boundary

The reusable component does not register a service worker, claim a URL scope, install an application
manifest, own host cache/update policy, or claim standalone offline operation. Vaadin Flow interaction,
queries, edits and authoritative state remain server-backed. Build-time offline reproducibility in G18-061
is a separate supply-chain property and does not imply runtime offline behavior.

A host application may install its own PWA/service worker under explicit application policy. The adapter
documents immutable fingerprinted frontend assets that are safe to cache and identifies scene, icon,
raster, upload, export, push and other session/authorization-bound URLs that generic caches must not retain
or replay. Cacheability derives from response contracts and host authority, never merely from a URL suffix.

The component exposes bounded connection-state and resynchronization behavior needed by a host: connection
loss cannot commit edits or silently treat stale local state as authoritative; reconnection obtains or
acknowledges a current generation before resuming interaction. Pending requests, resources and gestures
are canceled/reconciled exactly once. An optional example may demonstrate a host-owned PWA, but that does
not broaden the adapter's support claim or grant it application-scope control.

## Network and resource-authority boundary

All feature/raster/elevation/tile/service queries, remote endpoint policy, redirects, credentials, cookies,
authorization headers, retries, caches, validators, protocol parsing and decoder selection remain on the
Java/server side. The bundled client does not implement or directly contact HTTP Tiles, TileJSON, WMTS,
OGC API Tiles, MapLibre sources, workspace endpoints or arbitrary caller URLs. Scene metadata cannot grant
browser network authority.

The browser may retrieve component-issued same-origin session resources for efficient icons, rasters,
prepared geometry, renderer buffers and other closed payloads. Each URL is unguessable where appropriate,
owned by one application/session/component generation, media/profile typed, exact-length, bounded,
non-cacheable unless explicitly immutable, revocable and expired on replacement/detach/session close.
Responses are checked before allocation/decoding, and registration/unregistration remains failure-robust.

Remote public data still traverses the server resource broker. This is a deliberate security and semantic
boundary, not an assertion that every byte must use a Flow RPC. Large approved payloads can use guarded
resource responses while protocol/authentication behavior remains implemented once in the Java adapters.

## Scene transport and update model

Flow carries small closed control manifests. Large packed coordinates, indexes, label/glyph placement,
renderer buffers and similar immutable data use exact-length same-origin binary resources described by
those manifests. The binary profiles use explicitly versioned project-owned layouts with fixed byte order,
primitive types, alignment, counts, offsets and semantic limits; they are private transport structures,
not Java serialization or a new public geospatial format.

After a complete acknowledged snapshot, the server may send a prospective atomic patch from exactly that
accepted scene generation. Patch operations have stable layer/feature/resource identities and closed
add/remove/replace/reorder forms. The client downloads and validates the complete candidate manifest and
all required resources before swapping any visible, hit-test, interaction or renderer state. Removed
resources remain owned until the replacement commits and are then released exactly once.

Generation gaps, reconnects, missing resources, validation failures, unsupported private versions, excessive
patch chains, or patches that are not materially smaller select a full snapshot. The server never relies on
the browser to infer a patch base. Patch count/depth, operation count, identities, bytes, decompression,
allocation, validation, transfer concurrency and retained old/new generations are prospectively bounded.

Control and resource failures preserve the previously accepted scene, return stable public diagnostics,
cancel invalid interaction state where required, and allow a bounded full-snapshot retry. Evidence records
logical, manifest, binary, full, patch, transferred, retained-memory and paint costs separately.

## Spatial chunk reuse

Production navigation may use bounded server-authored spatial chunks to prefetch and retain nearby prepared
content. The server alone performs source/service access, projection, wrap/seam handling, portrayal, labels,
clipping, stable identity and chunk construction. The client never derives source requests or interprets a
remote tiling scheme from scene data.

A chunk key includes source/binding identity and revision, portrayal/style/catalog/font revision, map and
display CRS/profile, wrap/copy policy, scale/zoom band, spatial index and private schema/backend profile.
Chunks with incomplete or mismatched keys are not reused. Overlap uses stable logical and visual identities
so paint order, labels, selection, hover, hits and edits neither duplicate nor choose an arbitrary copy.

Prefetch radius/directions, chunk count, logical/visual features, primitives/glyphs, binary/resource bytes,
decoded/CPU/GPU memory, concurrent requests, retained generations and eviction work have hard prospective
ceilings. A source/style/CRS/wrap/font/edit revision invalidates affected entries atomically; terminal
failure, detach, session close and ownership replacement cancel requests and release resources exactly once.

This cache is session-memory optimization, not persistent browser storage or offline content. Disabling it
retains complete behavior through current-scene snapshots. Evidence compares cached/uncached semantics and
measures pan continuity, query/transfer savings, eviction, invalidation, failures and memory plateaus.

## Text, fonts, and accessibility semantics

Map-label geometry uses the project's bounded neutral shaped-text and placement result. Explicitly
registered, provenance-carrying fonts determine matching/fallback, script/language/direction, bidi,
glyph selection, advances, baselines, wrapping, decoration, path placement, collision bounds and hit
geometry before browser publication. The browser never substitutes an ambient platform font or reshapes
authoritative map text with `fillText`.

The private transport supplies immutable glyph identities plus validated outlines or atlas placements,
positioning, paint and clipping data. Canvas and WebGPU consume the same result with declared rasterization
tolerances. Glyph/font/atlas bytes, dimensions, entries, runs, paths, pixels, caches, uploads and retained
generations are prospectively bounded and session-owned. Missing fonts/glyphs follow an explicit caller
fallback or fail before scene acceptance.

Original normalized Unicode, language/direction and logical label/feature identity remain available for
accessible alternatives, selection, copy/inspection and diagnostics without forcing assistive technology
to infer text from glyph images. Browser-native text remains appropriate for ordinary Vaadin/HTML controls,
but it does not define map-label placement or cross-renderer parity.

## Host theming contract

The Java component publishes a small stable theme contract for non-portrayal chrome: component background,
focus indication, loading/progress state, error/empty state and accessibility/interaction cues such as the
non-color portion of selection and hover. It uses documented Java theme variants and a closed inventory of
CSS custom properties and/or `::part` names, with value syntax, inheritance, defaults and compatibility
rules. Defaults satisfy the declared contrast, forced-colors, focus and reduced-motion requirements.

The theme surface does not expose private canvases, renderer layers, binary/GPU resources or protocol state,
and cannot alter feature symbols, labels, z-order, opacity/blending semantics, collision, clipping, hits,
edits or resource authorization. Map content is styled only through the neutral portrayal APIs. Invalid or
unsafe theme values fall back predictably and cannot create unbounded paint/layout work.

Right-to-left host layout, density and Vaadin theme integration affect surrounding chrome without mirroring
map coordinates or changing directional cartographic semantics. Theme changes invalidate only the required
chrome/render state, remain bounded, and preserve focus/interaction across live updates.

## Localization contract

All component-authored human-facing text uses an immutable caller-supplied Java message provider: accessible
name/description/instructions, keyboard help, loading/empty/connection state, interaction announcements,
validation/errors and measurement/unit presentation. The module supplies a complete default English catalog
with stable message keys and typed bounded parameters. Missing/invalid entries have an explicit fallback or
configuration failure; raw patterns do not execute arbitrary formatting code.

The host supplies a supported `Locale` per component/session. The adapter does not consult or mutate the
JVM process default, browser ambient language, operating-system locale or hidden resource bundles. Locale,
number and unit formatting are deterministic for the explicit catalog/profile. Stable diagnostic codes,
context keys, protocol enums and machine evidence never change with language.

A live locale/catalog change validates the complete candidate, atomically refreshes applicable chrome and
semantic text, announces only meaningful changes, and preserves scene generations, focus, selection, tools
and gestures unless an unavoidable accessibility-state reset is documented. Unicode bidi/isolation and
right-to-left chrome are supported without reflecting map coordinates or directional cartography. Message
keys, code points, parameters, formatted length, announcements and update work are bounded.

## Current-view PNG capture

The public Java component may request a capture of the currently accepted browser map view. The capture
includes the documented map layers, labels, rasters and interaction overlays but excludes arbitrary host DOM
and application chrome. It uses the browser's native PNG encoder after complete Canvas/WebGPU readback and
publishes one bounded same-session resource/download only after successful encoding.

Dimensions, pixels, encoded bytes, readback/encode time, concurrency, temporary/retained memory and resource
lifetime have exact ceilings. Failed, canceled, stale, tainted or over-budget capture publishes nothing and
expires any prior pending result according to the closed API contract. All scene inputs remain same-origin,
so capture cannot broaden network authority.

PNG capture is a user convenience representing what that browser painted. It is not byte-deterministic,
pixel-reproducible across browsers/GPUs, archival, color-proof or a Java PNG-writer claim. JPEG and WebP
capture are excluded. Canonical SVG and future format exports continue from neutral server snapshots.

## Deliberate exclusions

- Vaadin Map, TestBench, commercial Vaadin components, third-party browser map engines and a public
  JavaScript scene/protocol API.
- Server-side AWT image rendering as the browser presentation path, arbitrary client styling code,
  ambient remote basemaps, credentials, service workers or offline application caching granted by the
  reusable component.
- Compatibility claims for browsers outside the pinned Vaadin matrix or for host applications that
  remove the component's names, keyboard paths, status delivery or workflow alternatives.

## Task decomposition

Cards G19-180 through G19-189 implement and close this matrix. G19-189 is the only module closeout card.

[vaadin-compat]: https://vaadin.com/docs/latest/compatibility
