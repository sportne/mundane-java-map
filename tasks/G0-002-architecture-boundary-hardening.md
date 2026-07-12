# G0-002 — Architecture Boundary Hardening

Status: Proposed
Depends on: G0-001
Gate: G0
Type: AFK

## Goal

Turn the documented module and Native Image restrictions into executable checks that fail as soon as
production code crosses a dependency or runtime-mechanism boundary.

## Context

`DESIGN.md` assigns public contracts to `mundane-map-api`, JDK-only algorithms to
`mundane-map-core`, and Swing/Java2D to `mundane-map-awt`. The existing
`ArchitectureRulesTest` checks several package dependencies and a subset of dynamic APIs, but it does
not yet cover I/O module direction, external runtime types, discovery mechanisms, Java serialization,
dynamic proxies, `Unsafe`, internal JDK APIs, JNI, or all native-targeted production modules.

## Scope

- `modules/mundane-map-architecture-tests` and its build dependencies.
- Root/build-logic metadata needed to expose production runtime dependencies to architecture tests.
- Architecture-test fixtures that demonstrate each rule can fail.

## Out of scope

- Refactoring a future format module before that module has working behavior.
- Optional Level 2 adapter policies beyond ensuring their external types cannot leak into the API.
- Native compilation and performance testing.

## Acceptance criteria

- Automated rules enforce `api <- core <- awt`, keep `java.desktop` types in AWT or consumer code,
  and reject dependencies from any `mundane-map-io-*` module to AWT.
- Level 1 production runtime configurations contain only JDK modules and other `mundane-map`
  artifacts; test/build tooling is excluded from this assertion.
- Public signatures in `mundane-map-api` cannot expose an external-adapter type.
- Native-targeted production classes are checked for reflection, classpath or resource scanning,
  dynamic proxies, Java serialization, JNI/native methods, `Unsafe`, internal JDK APIs, and dynamic
  class loading.
- Registration remains explicit: a fixture using service/classpath discovery or an implicit plugin
  registry makes the architecture lane fail.
- Rule diagnostics name the violated boundary and offending class or dependency.
- Positive and negative fixtures prove every rule is active and avoid false positives in tests,
  examples, and build tooling.

## Required tests

- Architecture tests for allowed dependencies in the existing API, core, and AWT modules.
- One deliberately rejected fixture for each grouped boundary and prohibited-runtime rule.
- Runtime dependency graph assertions for every Level 1 published module.

## Validation

```bash
./gradlew :modules:mundane-map-architecture-tests:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer bytecode and resolved-configuration inspection over source-text matching. Keep the rule set
data-driven so later working `mundane-map-io-*` modules are included explicitly when registered,
without reflection or classpath scanning.

