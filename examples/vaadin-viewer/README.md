# Vaadin viewer example

This non-published Spring Boot application demonstrates `MundaneMap` with an in-memory study area,
route, editable points, and explicitly opened server-local sources. It deliberately has no basemap,
map-data download, API key, account, or commercial Vaadin component. Starting the example does not
open a source path or contact a remote map service.

## Development launch

Use Java 21 and an explicitly installed Node.js 24.14.0 with its bundled npm 11.9.0. The build does
not allow Flow to download Node implicitly. From the repository root:

```bash
./gradlew :examples:vaadin-viewer:bootRun
```

Then open `http://127.0.0.1:8080/`. The initial frontend preparation needs the exact npm inputs
frozen by the G18 dependency profile. Until G18-061 extends the repository's offline assembly to
frontend inputs, a download-disabled build must use a separately and explicitly primed npm cache
for the committed lockfile. A modern keyboard-accessible desktop or mobile browser is required.
Complete automated browser/version coverage is intentionally deferred to G18-060.

The toolbar provides fit/zoom, navigation, measurement, point creation/movement, undo/redo, a
compatible horizontal-wrap toggle, and server-side SVG preparation/download. The sidebar provides
ordered visibility controls, browser upload, coordinates, selection identity, source-diagnostic
status, and measurement status. All controls use native HTML focus order and the map exposes its
own keyboard help and interaction semantics.

## Server-local source workflows

The four fixture buttons open checked repository data: a shapefile, a display GeoTIFF, a signed
integer elevation GeoTIFF interpreted as metres, and a feature-only workspace. The adjacent path
field opens the same supported formats from a caller-selected path on the server. It is not a
browser upload: the value names a file readable by the application process and must therefore be
treated as trusted administrative input. Do not expose this control to untrusted users without an
application-specific authorization and path policy.

Shapefiles are opened by `mundane-map-io-shapefile`, GeoTIFF raster/elevation files by
`mundane-map-io-geotiff`, and `.mmap.xml` files by `mundane-map-workspace`. The workspace registry is
closed and permits only its versioned shapefile opener and checked symbol catalog. It does not scan
directories or infer decoders. Every boundary applies tighter query, raster, or workspace ceilings,
uses cooperative cancellation, and reports only stable diagnostic codes—never local paths, source
values, stack traces, or format-specific data to the browser. Decoding remains in Java; the Flow
frontend receives only the component's bounded vector/raster protocol.

## Browser upload

The browser upload form is separate from the trusted server-path field. Every UI receives a fresh
temporary directory. Client names must be single leaf names, are used only to validate one logical
group, and are replaced by server-generated `dataset` names before any decoder sees a path. The
viewer never follows a client directory, scans a staging directory, or infers a decoder.

One request accepts at most 8 files, 16 MiB per file, and 32 MiB in aggregate. A UI may retain at
most 32 files and 64 MiB until its route/session ends. Shapefile upload requires exactly one
case-insensitive stem with `.shp`, `.shx`, and `.dbf`; `.prj` and `.cpg` are the only optional
sidecars. Raster and elevation upload each accept one `.tif` or `.tiff`. Workspace upload accepts
one `.xml`; only self-contained workspace content is useful because arbitrary referenced uploads
are deliberately not remapped. Exact declared/received lengths, duplicate sidecars, unsupported
extensions, cancellation, and all prospective ceilings are checked before publication.

Uploads are not persisted or shared. Replacement may leave an older staged group present while an
owned source finishes closing, so the aggregate session ceiling covers all retained groups. Route
detach, Vaadin session destruction, or application shutdown cancels work, closes sources, expires
the upload authorization, and recursively deletes the route-owned staging root. This example does
not provide authentication, authorization, malware scanning, content-disarm, tenant quotas, or
remote object storage; those remain deployment responsibilities.

## SVG export

`Prepare SVG` captures the latest browser-acknowledged vector scene on the server and passes the
detached snapshot to the existing canonical `mundane-map-io-svg` encoder. `Download SVG` serves the
result through a route-scoped Flow resource for five minutes with exact length, `nosniff`, sandbox,
and private `no-store` headers. A replaced, expired, detached, or session-closed resource returns no
document. Export never runs in browser JavaScript and never forwards source paths or metadata.

The closed SVG profile includes supported vector geometries, built-in vector symbols, hatches,
endpoints, and accepted label measurements. Raster/elevation primitives, raster icons,
interaction overlays, custom/legacy symbols, a pending label handshake, and over-limit content are
not rasterized or silently omitted; the status area reports the existing stable
`VECTOR_EXPORT_*`/`SVG_EXPORT_*` code instead.

## Production build and deployment boundary

With the pinned Node/npm inputs already installed or explicitly cached, build the production
frontend and executable archive with:

```bash
./gradlew :examples:vaadin-viewer:clean \
  :examples:vaadin-viewer:vaadinBuildFrontend \
  :examples:vaadin-viewer:bootJar -Pvaadin.productionMode --console=plain
java -jar examples/vaadin-viewer/build/libs/vaadin-viewer-0.1.0-SNAPSHOT.jar
```

Bind and proxy the application according to the deployment's own TLS, authentication, request-size,
session-expiry, temporary-storage, and denial-of-service policy. The proxy must preserve Vaadin's
long-lived push transport, including WebSocket upgrades and suitable connection timeouts (or its
long-poll fallback), because asynchronous source and export status is delivered through automatic
push. The checked limits are defensive application ceilings, not a multi-tenant capacity or
security assessment. The example makes no cloud topology, availability, throughput, browser
pixel-identity, or map-data completeness claim.

## Ownership and limits

Every route instance owns one `MundaneMap`, one fixed-lane edit binding, one source-opening lane,
and every directly opened source or workspace session. Replacement first cancels superseded work,
retires component-owned serialized leases, and closes the prior owner after its final query-bound
lease releases. Route detach, Vaadin session
destruction, or Spring application shutdown closes sources, component resources, registrations,
pending work, and the edit lane exactly once. State is per UI session: it is neither persisted nor
shared with another user or browser tab.

This is a bounded example, not a production security or scalability profile. It provides no
authentication, authorization, persistence, collaboration, remote basemap, cross-browser pixel
identity, map-data completeness, malware screening, or cloud-deployment claim.
