# G11-004 — Optional adapter boundaries

Status: Proposed
Depends on: G10-003, G10-004, G10-007, G11-001
Gate: G11
Type: HITL

## Goal

Approve exactly two Xerial-backed SQLite format adapters and record evidence-based `DEFER` decisions
for JTS, PROJ, and GDAL without weakening Level 1 or reserving speculative integration surfaces.

## Context

G10-003's bounded JDK-only GeoTIFF profile demonstrates no GDAL gap; G10-004 provides two complete
SQLite container profiles; G10-007 records no PROJ-requiring CRS; and G11-001's point-first editing and
same-CRS snapping demonstrate no current JTS need. The authoritative disposition is in
`design/G11-editing-styling-persistence-adapters-export.md`; G10 retains the concrete format details.

## Scope

Define `ACCEPT|DEFER|REJECT`; record the four dispositions and exact reopen evidence; qualify the two
existing Xerial module boundaries, external-JNI exception, public-type isolation, direct construction,
dependency/classifier/checksum pin, licensing/notices, lifecycle/diagnostics, Linux JVM support,
Native Image policy, publication/consumer evidence, and corrected G10-039 through G10-044 graph.

## Out of scope

Production code/modules/tasks; a generic adapter/SQLite/native-loader API; JTS, PROJ, or GDAL module
names/dependencies/cards; external types in public contracts; project-owned/repacked native binaries;
discovery; new platform/Native Image claims; and native performance work without benchmarks and a
separate decision.

## Acceptance criteria

- **G11 optional-adapter disposition approval** records SQLite/Xerial `ACCEPT` and JTS/PROJ/GDAL
  `DEFER`, with no reserved module or card for a deferred candidate.
- Acceptance is only `mundane-map-io-geopackage-xerial` and `mundane-map-io-mbtiles-xerial`; no generic
  SQLite/API type exists, and JDBC/Xerial/native types remain private under architecture checks.
- The verified `3.53.2.0` code/Linux classifiers, exact runtime graph/checksums, direct private
  construction, closed third-party reflection/host/resource/JNI inventory, licenses/notices,
  external-JNI exception, Java 21 Linux x86-64/glibc 2.35 floor, and Native Image `not-targeted`
  policy are exact.
- Stable deployment diagnostics, lifecycle/cleanup, publication/classifier mirror, and offline
  consumer evidence are mapped to G10 working cards; missing dependency identity fails verification,
  not an application runtime protocol.
- G10-040 and G10-043 are the accepted working roots after G11-004; the corrected graph contains no
  duplicate G11 implementation task, while every deferred candidate has a concrete reopen gate.

## Required tests

No production tests in this design task. Require later exact dependency/signature/checksum and notice
checks; external-type/prohibited-call architecture tests; Linux JVM format success; stable
`unsupportedPlatform|nativeLoad|temporaryDirectory` cases in isolated fresh JVMs; third-party
mechanism inventory; lifecycle/cleanup; publication; and a clean offline consumer. Native Image is
neither run nor claimed while these adapters are `not-targeted`.

## Validation

```bash
./gradlew :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 optional-adapter disposition approval**. The maintainer approves the one
`ACCEPT`, three `DEFER` outcomes, exact Xerial artifacts/licenses/external-JNI boundary, Java 21 Linux
x86-64/glibc 2.35+ JVM-only claim, Native Image `not-targeted` policy, reopen evidence, and reused G10
graph. Project code still declares/loads no native library; Level 1 remains JDK-only.
