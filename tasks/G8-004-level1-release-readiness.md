# G8-004 — Level 1 release readiness

Status: Proposed
Depends on: G8-003
Gate: G8
Type: HITL

## Goal

Make and record a maintainer go/no-go decision for a useful Level 1 release after every independent
verification lane and release artifact check succeeds.

## Context

This is the explicit Level 1 completion gate. It verifies evidence produced by prior tasks and may
correct release metadata, but it does not publish or expand scope.

## Scope

- Release version/support statement and Level 1 capability/limitation summary
- License/notices, artifact checksums, publication metadata, roadmap/task status, and release notes
- Independent final execution and recording of every Level 1 verification lane

## Out of scope

- Remote publication, Git tagging, GitHub releases, signing with unavailable credentials, and Level 2
  implementation
- Claiming Windows/macOS Native Image support without separate build-and-run evidence
- Waiving a failed lane by merging it into another command

## Acceptance criteria

- The maintainer selects the release version and approves a support statement covering Java 21,
  Linux Native Image evidence, supported symbol/interaction/source/shapefile/raster profiles, limits,
  diagnostics, and known performance boundaries.
- Every distributable file has correct project/license/third-party attribution; corpus and binary
  fixtures have explicit provenance and redistribution permission.
- Publication metadata, coordinates, dependency scopes, source/Javadoc artifacts, checksums, and
  consumer results match the selected version and contain no snapshot/path/credential leakage.
- `qualityGate`, `nativeSmoke`, `shapefileCorpus`, `renderRegression`, `performanceEvidence`, and the
  publication/consumer lane are run as separate commands from a clean revision and their exact
  environment/results are recorded without claiming unrun checks.
- Normal, Native Image, corpus, rendering, performance, and publication failures remain independently
  diagnosable; all release-blocking failures are resolved or the decision is no-go.
- ROADMAP and task index distinguish implemented-and-verified Level 1 work from proposed Level 2
  work, with G8-004 as the completion point.
- No remote repository upload, commit, tag, or release is performed by this task.
- **HITL checkpoint:** the maintainer reviews all evidence, version/support wording, licenses,
  checksums, and artifacts, then records an explicit go or no-go decision.

## Required tests

- All six independent verification lanes listed in Validation.
- A clean staged-artifact consumer run using the chosen non-snapshot candidate version.
- Manual manifest/license/checksum comparison and review of Native Image platform claims.
- Link/status/dependency validation for the task index and roadmap.

## Validation

Run each specialized lane independently, then finish with the normal gate:

```bash
./gradlew nativeSmoke --console=plain
./gradlew shapefileCorpus --console=plain
./gradlew renderRegression --console=plain
./gradlew performanceEvidence --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

A no-go result is a valid task outcome but does not make the gate Complete; record the blocking
evidence and leave release status unclaimed. Linux is the required Native Image release lane.
