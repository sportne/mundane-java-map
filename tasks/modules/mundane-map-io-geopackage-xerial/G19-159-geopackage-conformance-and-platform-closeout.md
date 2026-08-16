# G19-159 — GeoPackage conformance and platform closeout

Status: Proposed
Depends on: G19-158
Gate: G19
Type: HITL

## Goal

Close the GeoPackage 1.4 reader/writer/updater and separate community profiles with expert interoperability evidence.

## Context

Feature completeness requires requirement-level conformance, independent databases/applications, fault handling,
native-driver supply-chain evidence and exact support wording—not merely green unit tests.

## Scope

- Map every applicable OGC 12-128r19 and official-extension requirement/test to read/write/update evidence.
- Run the OGC executable test suite and provenance-recorded GDAL/QGIS/Esri/NGA/community producer/consumer corpora.
- Separately report vector-tile and styling community profile results without including them in GeoPackage conformance.
- Exercise all geometry/dimension/tile/coverage/metadata/schema/relation/unknown-extension/transaction/failure limits.
- Verify Xerial version/native binaries/checksums/licenses and supported JDK/OS/architecture deployment matrix.
- Verify offline repository, publication, JPMS/Javadocs, examples, AWT/Vaadin, performance, diagnostics and docs.

## Out of scope

- Adding new extensions/platforms during closeout or claiming official status for community profiles.

## Acceptance criteria

- Every `CAPABILITIES.md` row has normative evidence or an explicit exclusion and all applicable OGC tests pass.
- Independent applications read written/updated packages and their output is read consistently by this adapter.
- Public claims distinguish standard conformance, community compatibility, platform support and deliberate exclusions.

## Required tests

- Full OGC/corpus/cross-producer/read-write-update/fault/security/limit matrix on every supported platform.
- Offline/publication/dependency/license/native deployment, long soak, performance and resource-leak evidence.

## Validation

Run all module/OGC/platform/community lanes, qualityGate, applicable offline/publication checks, and `git diff --check`.

## Notes

HITL checkpoint: an external GeoPackage expert approves requirement evidence, platform matrix and support wording.
