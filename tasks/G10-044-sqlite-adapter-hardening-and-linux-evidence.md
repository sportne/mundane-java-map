# G10-044 — SQLite adapter hardening and Linux evidence

Status: Proposed
Depends on: G10-042, G10-043
Gate: G10
Type: HITL

## Goal

Close MBTiles hostile-input behavior and record the exact Linux JVM deployment and packaging evidence
for both approved Xerial-backed map adapters.

## Context

G10-042 completes GeoPackage behavior and G10-043 supplies the MBTiles slice. The proposed G10-004
and G11-004 decisions limit support to a pinned Java 21 Linux x86-64/glibc JVM path and explicitly
exclude Native Image.

## Scope

Complete MBTiles exact/one-over limits, diagnostics, cancellation/progress interruption, cache
rollback/LRU, input mutation/sidecar, corrupt/truncated database, lifecycle and cleanup cases; add
legally redistributable independent fixtures; verify exact Xerial artifacts/licenses/checksums and
closed third-party mechanism inventory; run isolated supported/unsupported deployment processes;
add `.github/workflows/sqlite-adapter-evidence.yml` with pinned Ubuntu 22.04/glibc 2.35, Ubuntu
24.04/glibc 2.39, and Alpine/musl jobs; and record exact-reviewed-SHA JVM evidence for both adapters.

## Out of scope

Native Image, Windows/macOS/musl/other-architecture claims, project-owned/repacked JNI, alternate
SQLite versions/drivers, generic adapters, public-network tests, or changing Level 1 support.

## Acceptance criteria

- Every MBTiles limit and stable diagnostic has direct malformed, corrupt, mutation, cancellation,
  cache, cleanup, equality, and one-over evidence with no sensitive database data leakage.
- Independent GeoPackage/MBTiles fixtures have approved provenance, redistribution terms, recipes,
  versions, and hashes.
- Fresh supported JVMs open/query/read/render both staged adapters; isolated musl/non-Linux/non-x86,
  code-only, and unusable-temp cases produce the exact approved deployment diagnostics before a
  later fresh-process success.
- The support statement names only the exact pinned Java 21 Linux x86-64/glibc 2.35+ JVM evidence and
  says both adapters are Native Image `not-targeted`.
- The reviewed commit's platform workflow records each Ubuntu image, JDK, kernel, architecture, and
  `getconf GNU_LIBC_VERSION` result; the Alpine job records its musl detection; and all jobs retain
  the isolated fresh-JVM negative/success outcomes relevant to their lane.

## Required tests

Complete MBTiles hostile matrix, deterministic mutation, cache/cancellation/lifecycle/cleanup,
fixture provenance/digests, dependency and prohibited-mechanism inventory, isolated deployment
negative/success processes, both pinned Ubuntu JVM integrations plus Alpine/musl negative evidence,
workflow contract tests, publication, offline consumer, and a successful exact-reviewed-SHA workflow
run inspected at the HITL checkpoint.

## Validation

```bash
./gradlew :modules:mundane-map-io-geopackage-xerial:check :modules:mundane-map-io-mbtiles-xerial:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 SQLite adapter Linux support review**. It can occur only after G10-004 and
G11-004 are approved and all implementation dependencies complete. A maintainer approves fixture
rights, exact external-artifact evidence, the exact-reviewed-SHA workflow run and recorded image/JDK/
kernel/glibc facts, isolated deployment observations, and the bounded JVM-only wording.
