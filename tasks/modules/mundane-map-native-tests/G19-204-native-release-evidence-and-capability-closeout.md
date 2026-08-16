# G19-204 — Native release evidence and capability closeout

Status: Proposed
Depends on: G19-203
Gate: G19
Type: HITL

## Goal

Make the supported Native Image claim reproducible, measurable, accurately documented, and independently reviewable at release time.

## Context

Green native tests are insufficient if their toolchains, inputs, linkage, resource closure, output provenance, or platform exceptions cannot be reconstructed and audited.

## Scope

- Produce a checksummed release-evidence bundle for every supported host containing toolchain, runner, compiler/linker, CPU baseline, linkage, build options, dependency/resource manifests, logs, and normalized corpus results.
- Rebuild from clean connected and staged-offline inputs and compare normalized image contents and behavior; document unavoidable host/toolchain nondeterminism rather than claiming unsupported bit reproduction.
- Record image size, build time/peak memory, startup, steady-state memory, and representative workload timing as non-regression evidence with named environments and justified thresholds.
- Audit public documentation, package descriptions, registry metadata, publication checks, and support tables against `CAPABILITIES.md`.
- Obtain independent review of the platform matrix, closed-world configuration, corpus coverage, exclusions, and evidence provenance.

## Out of scope

- Publishing native executables as product artifacts unless a later release card explicitly authorizes it, or promising identical bytes across different operating systems/toolchains.

## Acceptance criteria

- A clean release candidate reproduces the reviewed behavior and normalized manifests on all five supported hosts from exact staged inputs.
- Every exception or variance is scoped, explained, and approved; no required row, module, corpus class, or cleanup check is missing.
- Documentation states standard dynamically linked host executables only and does not imply musl/static, cross-compilation, Windows ARM, desktop UI, or excluded-adapter support.

## Required tests

- Clean connected/offline rebuilds, normalized artifact/manifests comparison, linkage and CPU-baseline inspection, performance/resource regressions, publication integration, and documentation consistency.
- Independent diff/source/configuration/evidence audit plus a synthetic stale or incomplete evidence-bundle rejection.

## Validation

Run the full five-host native matrix, `./gradlew nativeSmoke offlineRepositoryVerification publicationDryRun --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve the complete evidence bundle and final Native Image support wording before closeout.
