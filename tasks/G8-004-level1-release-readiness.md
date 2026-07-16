# G8-004 — Level 1 release readiness

Status: Blocked
Depends on: G8-001, G8-003
Gate: G8
Type: HITL

## Goal

Make and record a maintainer go/no-go decision for a useful Level 1 release after every independent
verification lane and release artifact check succeeds on one immutable candidate revision.

## Context

This is the explicit Level 1 completion gate. It reconciles evidence produced by prior tasks, may
correct release metadata/documentation, and records the decision without publishing or expanding
scope. The recommended first candidate is `0.1.0`; the maintainer owns the final choice.

## Scope

- Strict non-snapshot candidate-version selection and one clean full candidate Git SHA
- Exact Java/toolkit/Native Image/capability/limitation support statement and one root `CHANGELOG.md`
  release entry
- License/provenance/notices, G8-003 artifact manifest/checksums/metadata, README, roadmap/task status,
  and release-decision record
- Independent execution and provenance recording of every Level 1 verification lane
- Intake of maintainer-supplied exact-candidate GitHub CI evidence as an explicit external prerequisite
- `DESIGN.md`/G8 design holistic Level 1 simplicity and developer-ergonomics closeout

## Out of scope

- Git push, remote publication, tag, GitHub Release, signing/credential lookup, and Level 2
  implementation
- Claiming Windows/macOS Native Image support without separate build-and-run evidence
- Waiving a failed lane by merging it into another command

## Acceptance criteria

- The maintainer selects a strict `MAJOR.MINOR.PATCH` non-snapshot candidate (recommended `0.1.0`)
  while the default development version remains `0.1.0-SNAPSHOT`.
- All release inputs are committed and the six lane records refer to one clean full 40-character Git
  SHA; the strict version/SHA forms and clean unchanged HEAD/tree are checked both before and after the
  lanes. Untracked files or evidence from another revision cannot be mixed into a go decision.
- A maintainer, outside this task, pushes or otherwise makes that exact candidate SHA available to the
  configured GitHub workflows and supplies the resulting G8-001 Native Image and normal CI URLs. The
  task performs no push; without this exact-SHA prerequisite it is `Blocked` and cannot record `GO`.
- The maintainer approves a support statement covering Java 21,
  Linux Native Image evidence, supported symbol/interaction/source/shapefile/raster profiles, limits,
  diagnostics, and known performance boundaries.
- Every distributable file has correct project/license/third-party attribution; corpus and binary
  fixtures have explicit provenance and redistribution permission.
- Publication metadata, five coordinates, dependency scopes, source/Javadoc artifacts, reproducible
  archives, artifact-manifest SHA-256, and consumer results match the explicitly supplied candidate
  version and contain no snapshot/path/credential leakage.
- The publication lane performs two fresh candidate-version stagings, preserves their independently
  generated immutable-artifact manifests, and requires byte-identical manifests/primary SHA-256
  values. The consumer runs against the second staging; repository timestamp metadata is validated
  but excluded from the reproducibility manifest.
- `qualityGate`, `nativeSmoke`, `shapefileCorpus`, `renderRegression`, `performanceEvidence`, and the
  publication/consumer lane are run as separate commands from a clean revision and their exact
  environment/results are recorded without claiming unrun checks.
- Specialized root task graphs remain independent. Performance semantic/counter/report failures block;
  duration alone is advisory and cannot waive or create a release failure.
- Normal, Native Image, corpus, rendering, performance, and publication failures remain independently
  diagnosable; all release-blocking failures are resolved or the decision is no-go.
- ROADMAP and task index distinguish implemented-and-verified Level 1 work from proposed Level 2
  work, with G8-004 as the completion point.
- The candidate already contains final proposed README/CHANGELOG support wording. A post-evidence
  record commit may update only task/index/roadmap and G8 decision records while naming the tested
  candidate SHA; it is not relabelled as the artifact revision. Any other change invalidates the
  complete candidate evidence matrix.
- No push, remote repository upload, tag, GitHub Release, signing, or credential lookup occurs.
- **HITL checkpoint — Level 1 release readiness approval:** the maintainer reviews all evidence,
  version/support wording, licenses, checksums, artifacts, and known limitations, then records explicit
  `GO` or `NO-GO` for the named candidate SHA.

## Required tests

- All six independent verification lanes listed in Validation on one candidate SHA.
- Maintainer-supplied exact-candidate Linux Native Image and normal CI run URLs, with the external
  push/trigger actor/date distinguished from task actions.
- A clean staged-artifact consumer run using the chosen non-snapshot candidate version.
- Two fresh staging manifests with byte-identical primary artifact/checksum rows before the second
  staging's consumer result is accepted.
- Manual manifest/license/checksum comparison and review of Native Image platform claims.
- Link/status/dependency validation for the task index and roadmap.
- A final normal gate and link/whitespace check over any permitted evidence-record-only update.

## Validation

Run each specialized lane independently, then finish with the normal gate:

```bash
CANDIDATE_VERSION=0.1.0
CANDIDATE_SHA=$(git rev-parse HEAD)
[[ "$CANDIDATE_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]
[[ "$CANDIDATE_SHA" =~ ^[0-9a-f]{40}$ ]]
test -z "$(git status --porcelain=v1 --untracked-files=all)"
./gradlew nativeSmoke --console=plain
./gradlew shapefileCorpus --console=plain
./gradlew renderRegression --console=plain
./gradlew -PperformanceRevision="$CANDIDATE_SHA" performanceEvidence --console=plain
mkdir -p build/release-reproducibility
./gradlew -Pmap.version="$CANDIDATE_VERSION" publicationDryRun --console=plain
cp build/release-evidence/artifact-manifest.tsv build/release-reproducibility/first.tsv
./gradlew -Pmap.version="$CANDIDATE_VERSION" publicationDryRun consumerSmoke --rerun-tasks --console=plain
cmp build/release-reproducibility/first.tsv build/release-evidence/artifact-manifest.tsv
./gradlew qualityGate --console=plain
test "$(git rev-parse HEAD)" = "$CANDIDATE_SHA"
test -z "$(git status --porcelain=v1 --untracked-files=all)"
git diff --check
```

## Notes

A no-go result is a valid checkpoint outcome but does not make the task/gate Complete; record the
blocking evidence and leave release status unclaimed. Use `Blocked` only for a real unavailable
external requirement such as Linux CI/tooling, unresolved redistribution rights, or missing
maintainer approval. Linux x86_64 on the exact G8-001 lane is the required Native Image evidence;
other platforms remain unverified. After the clean candidate commit, pause until the maintainer
supplies the exact-SHA remote CI prerequisite; do not push or trigger it from this task.

### Pre-candidate checkpoint

- `candidateVersion`: `0.1.0` selected as the proposed strict release version; the default remains
  `0.1.0-SNAPSHOT`.
- `candidateRevision`: not yet selected. The release inputs in this task must first be reviewed and
  committed, so no earlier SHA is eligible.
- `decision`: `NO-GO` for this pre-candidate checkpoint. The immutable candidate and required remote
  evidence do not exist yet; this is not a judgment that the implemented Level 1 surface is
  unsuitable for release.
- `externalCiPrerequisite`: `NO_EVIDENCE`. The one authorized final push has not occurred, so there
  are no exact-candidate Native Image or normal-CI URLs.
- `supportStatementApproved`: the proposed Java/toolkit/capability/limitation wording is now present
  in `README.md` and `CHANGELOG.md`; final release approval remains tied to the evidence checkpoint.
- `licenseProvenanceAudit`: locally reviewed. Published sources use the root BSD-3-Clause license,
  each publication archive is required by G8-003 to carry its exact bytes, repository-authored G2/
  G6/native/performance fixtures are identified as BSD-3-Clause, and the G5 corpus manifest maps
  every component to either the carried BSD-3-Clause text or the carried Natural Earth public-domain
  dedication. The Apache-2.0 Gradle wrapper and corpus remain source/test inputs rather than Maven
  runtime contents. No redistributed material currently requires an additional `NOTICE`.
- `artifactManifest`: not release evidence yet. G8-003 verifies the five-coordinate manifest shape,
  checksums, metadata, reproducibility, and isolated consumer locally; candidate-version manifests
  must be regenerated twice on the selected clean SHA.
- `lanes`: all six are `NO_EVIDENCE` for release-decision purposes until they run independently on
  the selected clean SHA. Development runs from earlier commits are intentionally not relabelled.
- `knownLimitations`: Java 21; bounded two-dimensional read-only shapefiles; bounded PNG/JPEG with
  explicit AWT decoding; EPSG:4326/EPSG:3857 only; no general CRS/raster reprojection; no portable
  performance guarantee; Native Image claim limited to the exact Ubuntu 24.04 Linux x86_64 GraalVM
  Java 21 lane; Level 2 capabilities remain excluded.
- `blockingFindings`: exact-candidate Linux Native Image and normal CI URLs are unavailable before
  the authorized final push. This is the real external blocker represented by `Status: Blocked`.
- `taskRemoteActionsPerformed`: none.

The maintainer-approved G8-004 design and proposed `0.1.0` profile are implemented as candidate
inputs. This record is deliberately not the final evidence record and does not make G8 or Level 1
complete.
