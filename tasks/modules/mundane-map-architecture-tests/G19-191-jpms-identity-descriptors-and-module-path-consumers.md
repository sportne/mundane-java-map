# G19-191 — JPMS identity, descriptors, and module-path consumers

Status: Proposed
Depends on: G19-190
Gate: G19
Type: HITL

## Goal

Give every published artifact an honest stable Java module identity and prove its declared module-path behavior.

## Context

The project has no descriptors or automatic-module-name policy. Requiring strong descriptors over non-modular reflective
dependency graphs would be as misleading as leaving module identities unstable.

## Scope

- Assign globally unique stable module names to all published artifacts and fail duplicates, filename-derived drift and split packages.
- Add minimal explicit descriptors to JDK-only and optional-adapter graphs that are demonstrably module-path safe, with exact exports,
  `requires`/`requires transitive`, `uses`/`provides` and narrowly justified qualified `opens` only.
- Give remaining ecosystem adapters stable `Automatic-Module-Name` manifests and test them as automatic modules; reevaluate pinned
  Vaadin/Xerial/Jackson and future dependency graphs on upgrade rather than granting permanent exemptions.
- Verify public signature dependency readability, concealed/internal package access, services/resources, classpath/module-path parity,
  jlink where applicable and unchanged native/JDK-only restrictions.
- Keep examples/tests/benchmarks/build logic non-published and outside the public module contract.

## Out of scope

- Broad `opens`, claiming strong encapsulation for automatic modules, modularizing all third-party libraries or public support modules.

## Acceptance criteria

- Every published JAR has one stable tested identity and documentation accurately distinguishes explicit from automatic modules.
- Explicit modules expose/read only required public contracts and pass real compile/run/service consumers on the module path.
- Classpath, module-path, offline/publication and native-target behavior remain compatible with their declared profiles.

## Required tests

- Synthetic duplicate/split/export/readability/qualified-open/service/resource fixtures and explicit/automatic consumer applications.
- Every published artifact on classpath and module path, dependency-upgrade eligibility, jdeps/jlink where applicable, malformed JAR/
  manifest/descriptor and publication metadata tests.

## Validation

Run architecture and all module-path/classpath/native/offline/publication consumer lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve stable names, explicit-versus-automatic classifications and any qualified reflective access.
