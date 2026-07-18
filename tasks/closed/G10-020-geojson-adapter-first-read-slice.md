# G10-020 — GeoJSON adapter and first read slice

Status: Complete
Depends on: G10-002
Gate: G10
Type: AFK

## Goal

Publish a working optional Jackson Core adapter that reads bounded Point and MultiPoint GeoJSON
through the existing FeatureSource query and lifecycle contracts.

## Context

G10-002 approves the exact RFC 7946 profile, direct non-recycling Jackson factory, dependency record,
limits, diagnostics, and external-type isolation.

## Scope

Create `modules/mundane-map-io-geojson-jackson`; register the optional adapter in build, architecture,
publication, and offline-consumer inventories; pin and verify Jackson Core 3.1.5; implement strict
snapshot opening, Feature/FeatureCollection Point and MultiPoint records, IDs, properties, metadata,
queries, cancellation, and cleanup.

## Out of scope

Line, polygon, multipart rendering, writing, exhaustive hostile-input coverage, viewer, and Native
Image claims.

## Acceptance criteria

- A local Point/MultiPoint document opens and queries in source order with canonical EPSG:4326.
- The public surface contains only JDK and MundaneJ types; no databind, discovery, reflection, or AWT
  enters the adapter.
- Publication and a clean offline Java 21 consumer resolve the exact staged adapter and dependency.

## Required tests

Factory-policy, valid/invalid opening, IDs/properties, limits, cancellation, cursor lifecycle,
architecture, publication, and offline-consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geojson-jackson:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The module must deliver working behavior in the same change that registers it. Keep Jackson private
and construct the exact pinned factory directly.

Completed with independent review. The adapter uses a bounded two-phase root dispatch, primitive
coordinate accumulation, exact snapshot ownership, locked/checksummed Jackson provenance, and a
staged-artifact consumer smoke.
