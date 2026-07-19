# G10-054 — KML point and line first slice

Status: Proposed
Depends on: G10-005
Gate: G10
Type: AFK

## Goal

Publish a working JDK-only KML adapter that securely traverses static local KML 2.2 containers and
renders Point and LineString placemarks.

## Context

G10-005 approves the exact static KML grammar, direct hardened StAX policy, immutable source
lifecycle, limits, diagnostics, fixed schema, and rejected dynamic/network behavior.

## Scope

Create `modules/mundane-map-io-kml` with its own local snapshot and directly constructed JDK StAX state
machine. Implement approved Document/Folder/Placemark traversal, Point and LineString mappings,
generated IDs, names/descriptions, clamp-to-ground coordinate handling, EPSG:4326 metadata, ordered
queries, cancellation, diagnostics, and rendering. Register the working module in architecture,
publication, offline-repository, and consumer inventories.

## Out of scope

Polygon, MultiGeometry, styles, labels, ExtendedData semantics, overlays, models, NetworkLinks, tours,
regions, time, altitude rendering, KMZ, writing, exhaustive hardening, and Native Image.

## Acceptance criteria

- Bounded local KML 2.2 Documents/Folders yield ordered Point and LineString records with the approved
  IDs, schema, attributes, extent, and recognized EPSG:4326 metadata.
- No href, style URL, schema location, entity, XInclude, or other input construct causes network or
  external-file I/O, and parser/KML types do not leak publicly.
- Supported features query and render through the real stack while malformed coordinates,
  cancellation, source close, and cursor lifecycle use stable approved behavior.
- The new module is JDK-only and AWT-free, stages complete artifacts, and works from a clean offline
  Java 21 consumer.

## Required tests

Snapshot/parser-policy, container traversal, Point/LineString coordinates, IDs/schema/CRS, ordered
query, rendering, cancellation, diagnostics, lifecycle, architecture, publication, and offline-
consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-kml:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Implement the KML state machine independently of GPX. Shared public or production XML abstractions are
outside the approved profile even though the two modules enforce the same security posture.
