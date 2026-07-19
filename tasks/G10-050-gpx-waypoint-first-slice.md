# G10-050 — GPX waypoint first slice

Status: Proposed
Depends on: G10-005
Gate: G10
Type: AFK

## Goal

Publish a working JDK-only GPX adapter that opens a bounded local GPX 1.1 snapshot and renders
waypoints through the existing feature-source stack.

## Context

G10-005 approves the exact GPX grammar, secure StAX policy, immutable materialized-source lifecycle,
limits, diagnostics, fixed schema, and publication boundary in
`design/G10-additional-formats-tiles-and-projections.md`.

## Scope

Create `modules/mundane-map-io-gpx` with its hardened local-file snapshot and directly constructed JDK
StAX boundary. Implement the approved root/version/namespace checks, waypoint Point mapping, fixed
attributes, generated IDs, EPSG:4326 metadata, bounded query/lifecycle behavior, cancellation, and
stable diagnostics. Register the working module in architecture, publication, offline-repository, and
consumer inventories, and render one waypoint through the real map stack.

## Out of scope

Track segments, routes, extension semantics, exhaustive hostile-input hardening, a viewer, Native
Image claims, KML, writing, and a shared XML abstraction.

## Acceptance criteria

- A valid bounded GPX 1.1 file opens from one immutable snapshot and exposes ordered waypoint Point
  records with the approved IDs, schema, attributes, extent, and recognized EPSG:4326 metadata.
- The parser uses the exact hardened `XMLInputFactory.newDefaultFactory()` policy and performs no
  schema, URL, entity, XInclude, reflection, discovery, or implicit resource access.
- Waypoints query and render through the ordinary `FeatureSource`/AWT stack, while cancellation,
  malformed values, close, and cursor lifecycle produce the approved stable behavior.
- The new module is AWT-free, leaks no parser or GPX types, stages complete artifacts, and works from
  a clean offline Java 21 consumer.

## Required tests

Snapshot and parser-policy tests; waypoint mapping, schema, ID, CRS, query, rendering, cancellation,
diagnostic, lifecycle, architecture, publication, and offline-consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-gpx:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The module must deliver the working waypoint slice in the same change that registers it. Keep the
private state machine format-specific; G10-054 independently implements the same security posture for
KML rather than introducing a common XML module.
