# G8-004 — Level 1 release readiness

Status: Complete
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

### Level 1 release readiness approval — 2026-07-17

- `candidateVersion`: `0.1.0`; the default development version remains `0.1.0-SNAPSHOT`.
- `candidateRevision`: `a5d10791d6cf811b438cb72504ff8b00b2ab8d75`.
- `decision`: **GO** for the named candidate's Level 1 support statement. This record-only closeout
  commit is not the artifact revision and does not authorize publication, tagging, or a GitHub
  Release.
- `reviewer / reviewDate`: maintainer preapproval for all qualifying HITL tasks / 2026-07-17.
- `externalCiPrerequisite`: the prior maintainer-authorized CI-repair sequence, outside this task,
  pushed `main` and triggered the exact candidate on 2026-07-17. The
  [Native Image run](https://github.com/sportne/mundane-java-map/actions/runs/29578220777),
  [native job](https://github.com/sportne/mundane-java-map/actions/runs/29578220777/job/87877476971),
  and [normal CI run](https://github.com/sportne/mundane-java-map/actions/runs/29578220793) passed.
  The normal matrix includes successful [Java 21](https://github.com/sportne/mundane-java-map/actions/runs/29578220793/job/87877477180)
  and [Java 25](https://github.com/sportne/mundane-java-map/actions/runs/29578220793/job/87877477157)
  quality jobs for the exact SHA.
- `supportStatementApproved`: PASS. Java 21, JDK-only Level 1 runtime, the Swing/Java2D boundary,
  bounded G2–G7 capabilities, stable limits/diagnostics, environment-specific performance evidence,
  and Ubuntu 24.04 Linux x86-64 Oracle GraalVM Java 21.0.11 Native Image support are approved exactly
  as stated in the candidate README/CHANGELOG. DTED and all other Level 2 capabilities remain outside
  the Level 1 claim.
- `licenseProvenanceAudit`: PASS. The root BSD-3-Clause license, archive copies, authored fixtures,
  Natural Earth public-domain corpus material, GDAL-generated synthetic DTED evidence, and Apache-2.0
  source/test-only Gradle inputs are accounted for. No redistributed release material requires an
  additional `NOTICE`.
- `artifactManifest`: `build/release-evidence/artifact-manifest.tsv`, SHA-256
  `bffb3405f4f2e0671ceb4897ad041c60caf2f74ba2019b7bd56fa5e0db3e9172`; both fresh stagings produced
  the same 150-row current manifest with 30 primary files and 2,401,450 primary bytes. The immutable
  Level 1 subset is five coordinates, 125 rows, 25 primary files, and 2,255,006 primary bytes. The
  sixth coordinate is the previously approved G9 DTED addendum and does not widen this decision.
- `lanes`:
  - `nativeSmoke`: PASS. The authoritative job above built and ran the no-fallback executable through
    `mundane-map native smoke: OK`; a supplemental clean `/tmp` candidate run with GraalVM CE
    21.0.2+13.1 also compiled from scratch in 25.0 seconds and passed.
  - `shapefileCorpus`: PASS on Ubuntu OpenJDK 21.0.11/Linux x86-64. Nine datasets and 36 components
    passed; manifest SHA-256 is
    `4975e0616511a1f4387d48d70d8bcba92266a2f98e7e611516f678741aaad90b`.
  - `renderRegression`: PASS on Ubuntu OpenJDK 21.0.11/Linux x86-64 in headless mode; 42 semantic and
    tolerance tests completed with zero failures/errors.
  - `performanceEvidence`: PASS in 2 minutes 2 seconds with the exact candidate revision, Java
    21.0.11, 32 processors, 512 MiB G1 heap, and `/tmp` working data. Report SHA-256 values are
    `d765eebb87c209dac80a3798aa6708dad03c6c80baead829dd33dff379e5c6c6` (JSON) and
    `38eb0604850d1e80700b6ff10f071543faaa4ae9956c28f7c9948eb0b61ade3c` (Markdown). Semantic/counter
    checks passed; the private vector-template cache remains retained and eager DTED remains accepted.
  - `publicationDryRun` / `consumerSmoke`: PASS. Fresh runs completed in 46 seconds and 1 minute
    6 seconds; manifests were byte-identical and the second staging printed
    `mundane-map consumer smoke: OK` using only staged `0.1.0` artifacts and the offline mirror.
  - `qualityGate`: PASS locally on Ubuntu OpenJDK 21.0.11 in 17 seconds and remotely in the exact-SHA
    Java 21/25 jobs linked above.
- `knownLimitations`: only EPSG:4326/EPSG:3857 operations; bounded 2D read-only shapefiles; bounded
  PNG/JPEG/world files through explicit AWT decoding; no general CRS/raster reprojection; no portable
  performance guarantee; Native Image claim limited to the recorded Ubuntu lane; Level 2 remains
  outside the Level 1 release claim.
- `blockingFindings`: none.
- `taskRemoteActionsPerformed`: none. Candidate validation used a clean detached `/tmp` worktree;
  this task did not push, dispatch workflows, publish, tag, sign, or access credentials.
