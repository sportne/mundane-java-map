# G0-004 — Declarative Gradle verification wiring

Status: Complete
Depends on: G0-003, G7-005, G8-005
Gate: G0
Type: AFK

## Goal

Finish the build cleanup by making lane dependencies explicit, eliminating `afterEvaluate` and live
task-graph walkers, wiring coverage to tests, and leaving project build scripts as concise DSL.

## Context

Corpus isolation currently rewrites `check.dependsOn` after evaluation. Architecture, performance,
and root scripts each interpret arbitrary Gradle dependency notation to prove lane isolation. JaCoCo
coverage verification does not independently schedule its test task.

## Scope

Build-logic conventions, root/settings Gradle files, Shapefile/DTED/architecture support builds,
architecture tests, design records, task index, and roadmap.

## Out of scope

Production behavior, removing distinct verification commands, or changing coverage thresholds.

## Acceptance criteria

- `jacocoTestReport` and `jacocoTestCoverageVerification` explicitly consume a completed `test` task.
- Corpus analysis remains reachable only from its corpus lifecycle without `afterEvaluate` or
  `setDependsOn` mutation.
- Normal, native, corpus, rendering, performance, publication, and consumer lanes are registered
  declaratively; general dependency-notation walkers are removed.
- Runtime dependency and public/native architecture checks remain active.
- Root and support-module build scripts contain configuration, not large task implementations.

## Required tests

Focused build-logic and architecture tests, dry-run lane dependency checks, every affected specialized
lane, normal quality gate, and configuration-cache reuse.

## Validation

```bash
./gradlew -p build-logic check --console=plain
./gradlew :modules:mundane-map-architecture-tests:check --console=plain
./gradlew shapefileCorpus dtedCorpus renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not replace one generic build framework with another. Prefer a few typed tasks and shared
conventions with explicit dependencies.
