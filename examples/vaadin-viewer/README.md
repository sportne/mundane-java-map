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

The toolbar provides fit/zoom, navigation, measurement, point creation/movement, undo/redo, and a
compatible horizontal-wrap toggle. The sidebar provides ordered visibility controls, coordinates,
selection identity, source-diagnostic status, and measurement status. All controls use native HTML
focus order and the map exposes its own keyboard help and interaction semantics.

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

## Ownership and limits

Every route instance owns one `MundaneMap`, one fixed-lane edit binding, one source-opening lane,
and every directly opened source or workspace session. Replacement first cancels superseded work,
retires component-owned serialized leases, and closes the prior owner after its final query-bound
lease releases. Route detach, Vaadin session
destruction, or Spring application shutdown closes sources, component resources, registrations,
pending work, and the edit lane exactly once. State is per UI session: it is neither persisted nor
shared with another user or browser tab.

This is a development example, not a production security or scalability profile. It provides no
authentication, authorization, persistence, collaboration, remote basemap, cross-browser pixel
identity, map-data completeness, browser upload, export, or cloud-deployment claim.
