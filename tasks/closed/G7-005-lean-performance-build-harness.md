# G7-005 — Lean performance build harness

Status: Complete
Depends on: G7-004, G9-007
Gate: G7
Type: AFK

## Goal

Retain the Java 21 `/tmp` performance-evidence behavior and reports while removing build-script
security machinery and live task-graph interpretation that do not protect an untrusted runtime
boundary.

## Context

G7/G9 evidence proved that native `/tmp` staging avoids the WSL mounted-filesystem bottleneck. The
performance module build script now contains nearly one thousand lines for classpath copying,
symlink threat checks, atomic report locks, negative scratch tests, cleanup services, JFR execution,
and general Gradle dependency-notation traversal.

## Scope

`modules/mundane-map-performance-tests`, root performance lifecycle wiring, performance design,
task index, and roadmap.

## Out of scope

Changing scenarios, fixtures, thresholds, report schema, production optimizations, or adding JMH or
native benchmark dependencies.

## Acceptance criteria

- Full and quick evidence still run on Java 21 from invocation-unique `/tmp` work/runtime/output
  directories and publish the same two bounded reports.
- DTED probe/corpus inputs and optional JFR remain supported.
- Cleanup is deterministic on success and ordinary failure without treating trusted build inputs as
  hostile parser data.
- Live task-dependency-notation parsing and negative scratch-boundary tasks are removed.
- `qualityGate` still excludes full/quick/JFR evidence by declarative task wiring.

## Required tests

Performance module tests, quick evidence, full evidence, report presence/schema checks, and normal
quality-gate isolation.

## Validation

```bash
./gradlew :modules:mundane-map-performance-tests:check --console=plain
./gradlew performanceQuick --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Preserve evidence meaning and `/tmp` execution. Simplify build orchestration, not the Java evidence
model.
