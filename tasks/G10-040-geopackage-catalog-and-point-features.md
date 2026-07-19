# G10-040 — GeoPackage catalog and point features

Status: Proposed
Depends on: G10-004, G11-004
Gate: G10
Type: AFK

## Goal

Publish the first approved optional Xerial-backed GeoPackage adapter with a bounded catalog and
Point/MultiPoint feature query/render slice.

## Context

G10-004 and G11-004 must first approve the exact GeoPackage/Xerial profile, dependency artifacts,
JNI boundary, and Linux JVM-only support claim. This task may not execute while either decision is
Proposed.

## Scope

After both approvals, reverify and pin the recorded Xerial code/Linux classifiers, licenses,
checksums, and dependency graph; create `modules/mundane-map-io-geopackage-xerial`; implement platform
and immutable-file preflight, direct private connection/session policy, bounded catalog inspection,
Point/MultiPoint GeoPackage binary decoding, fixed primary-key feature queries, rendering, lifecycle,
cancellation, stable diagnostics, architecture inventory, publication staging, and offline consumer
behavior.

## Out of scope

Executing before G10-004/G11-004 complete; line/polygon features, attributes, tiles, writing,
extensions/views, generic SQLite APIs, non-Linux support, Native Image, or project-owned native code.

## Acceptance criteria

- On the approved Java 21 Linux x86-64/glibc path, a strict fixture catalogs and queries bounded
  Point/MultiPoint features in stable order and renders through G4 without exposing JDBC/Xerial types.
- Preflight, direct `JDBC4Connection`, fixed policy/SQL, progress cancellation, fingerprint/sidecar
  checks, and deterministic cleanup follow the approved profile; project code performs no discovery
  or native loading.
- The exact classified dependency, license, checksum, external-mechanism inventory, publication
  metadata, build-only mirror, and clean offline consumer are verified without accepting the ordinary
  all-platform JAR or SLF4J.

## Required tests

Platform/file/header/session-policy tests, catalog schema and CRS rows, Point/MultiPoint geometry,
query/render, limits/cancellation/fingerprint/lifecycle, architecture and external-artifact inventory,
publication metadata, classified dependency mirror, and clean offline-consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-geopackage-xerial:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

This planning card does not approve G10-004 or G11-004. If either checkpoint changes the artifact,
platform, profile, or adapter boundary, update this card from the approved design before execution.
