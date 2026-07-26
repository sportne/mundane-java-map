# G17-002 — Build and CI duration reduction

Status: Complete
Depends on: G10-044, G16-007
Gate: G17
Type: HITL

## Goal

Reduce local build and GitHub CI feedback time using measured bottleneck evidence while preserving
the complete verification manifest, supported Java lanes, isolation guarantees, and diagnostic
quality.

## Context

The exact reviewed G10-044 remediation run took about 12 minutes for the main CI workflow and 16
minutes for the isolated offline-repository workflow. The build spans many modules and examples,
and several lanes intentionally use cold Gradle homes, offline execution, rerun tasks, alternate
JDKs, corpus fixtures, Native Image, or deployment probes. Optimization must start with task- and
job-level evidence rather than deleting slow checks or relying on warm local state.

## Scope

- Root/included build configuration, `build-logic`, Gradle properties, dependency locking and
  repository staging, and `.github/workflows/`
- Task graph, configuration/cache behavior, worker/daemon settings, duplicated compilation or
  analysis, job decomposition, artifact reuse, path filters, and cold-versus-warm execution
- A checked-in, machine-readable and LLM-readable before/after report naming environment, commit,
  commands, task/job timings, critical path, changes retained or rejected, and verification
  equivalence
- Focused contract tests for build tasks and workflow structure

## Out of scope

- Removing quality, coverage, architecture, corpus, rendering, performance, publication/consumer,
  Native Image, SQLite-platform, or offline-repository evidence
- Weakening Java 21 compilation, the additional supported test-JDK lane, cold/offline guarantees,
  dependency locking, diagnostics, or task failure propagation
- Portable wall-clock pass/fail thresholds, unreviewed remote build services, or production feature
  optimization

## Acceptance criteria

- A reproducible baseline separates Gradle configuration, compilation, unit tests, Javadocs,
  Checkstyle, SpotBugs, JaCoCo, specialized lanes, Native Image, staging, and isolated child-build
  time; GitHub job timing is correlated with controlled local cold and warm runs.
- The task identifies the actual critical path and tests each proposed optimization independently;
  rejected changes and their evidence are retained in the report.
- At least one material bottleneck is removed or reduced, and repeated controlled measurements show
  a meaningful median reduction in the affected local command or CI critical path without claiming
  portable timing guarantees.
- A machine-checked verification manifest proves that every pre-task required lane, Java version,
  architecture rule, coverage check, corpus, rendering, performance, Native Image, publication/
  consumer, SQLite deployment, and offline-isolation outcome remains scheduled in the appropriate
  workflow or explicit opt-in lane.
- Gradle task dependencies remain declarative and configuration-cache compatible; no task-graph
  interpreter, hidden network access, broad cache invalidation, or duplicated full quality build is
  introduced.
- Workflow concurrency, caching, artifacts, matrices, and path filters cannot allow a required
  check to be skipped for a change that can affect it.
- Fresh-clone and warm-iteration behavior are both documented, including any intentional expensive
  lanes and why they remain separate.
- **HITL checkpoint — build/CI equivalence and timing review:** a maintainer approves the before/
  after evidence, retained verification manifest, and any workflow scheduling trade-offs from the
  exact reviewed commit.

## Required tests

- Build-logic unit/functional tests and workflow contract tests for all changed scheduling.
- Controlled repeated cold and warm timing captures with identical inputs before and after.
- Exact local execution of every changed lane and an exact-commit GitHub Actions run.
- Negative tests proving failures in retained checks still fail the owning task/job.

## Validation

```bash
./gradlew :build-logic:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
./gradlew offlineRepositoryVerification --console=plain
./gradlew renderRegression shapefileCorpus dtedCorpus performanceEvidence --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
git diff --check
```

## Notes

Record the HITL checkpoint with the reviewed commit, runner/local environments, raw timing artifact
locations, medians and variance, before/after task and job manifests, and the maintainer disposition.
Native Image and SQLite deployment evidence may be satisfied by their exact-commit workflows rather
than being folded into `qualityGate`.

The 2026-07-26 maintainer checkpoint approved the reviewed Option 2 schedule: Java 21 owns the full
`qualityGate`, while Java 25 runs every normal project and included-build JUnit suite through
`supportedJdkTests`. The explicitly Java-21-bound performance entry-contract tests remain mandatory
in a separate tagged Java 21 task. Controlled evidence, rejected alternatives, retained-lane
manifest, build-duration causes, and future optimization choices are recorded in
`design/G17-project-hardening.md` and `verification/G17-002-*.tsv`. Exact-commit GitHub outcomes and
timings are correlated after the resulting task commit is pushed.
