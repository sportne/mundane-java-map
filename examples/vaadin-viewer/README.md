# Vaadin viewer example

This non-published Spring Boot application demonstrates `MundaneMap` with an in-memory study area,
route, and editable points. It deliberately has no basemap, map-data download, API key, account, or
commercial Vaadin component. Starting the example does not open a source path or contact a remote
map service.

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

## Ownership and limits

Every route instance owns one `MundaneMap` and one fixed-lane edit binding. Route detach or Vaadin
session destruction closes component resources, registrations, pending work, and the edit lane.
State is per UI session: it is neither persisted nor shared with another user or browser tab.

This is a development example, not a production security or scalability profile. It provides no
authentication, authorization, persistence, collaboration, remote basemap, cross-browser pixel
identity, map-data completeness, or cloud-deployment claim. Server-local sources, uploads, export,
and production deployment are introduced only by the later bounded viewer tasks.
