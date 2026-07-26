# G17-003 — Comprehensive public Javadocs

Status: Proposed
Depends on: G17-001, G17-002
Gate: G17
Type: AFK

## Goal

Give every public or protected Java declaration in the project useful, strict Javadocs so consumers
and maintainers can understand contracts without reading implementation code.

## Context

G8-002 established strict Javadocs for the original five Level 1 published modules. Later format,
symbology, workspace, export, world-wrap, stress-example, and build-support work expanded the
repository. The documentation requirement must now cover the entire current project consistently,
including types, constructors, methods, fields, parameters, return values, exceptions, and record
components.

## Scope

- Hand-authored production Java under `modules/`, `examples/`, and `build-logic/`
- Package documentation and every public/protected class, interface, enum, record, annotation,
  constructor, method, and field
- `@param`, `@return`, `@throws`, `@since`, `@deprecated`, `{@inheritDoc}`, links, units, coordinate
  spaces, nullability, ownership, lifecycle, limits, thread/EDT behavior, and diagnostics where
  applicable
- Shared strict doclint and missing-Javadoc enforcement for every in-scope source set

## Out of scope

- Documentation for private/package-private implementation details unless needed to explain a
  non-obvious invariant
- Generated sources, vendored source, production behavior changes, API redesign, or speculative
  guarantees
- User guides and roadmap reconciliation already owned by G17-001

## Acceptance criteria

- Every in-scope public/protected type and member has non-placeholder Javadocs; public fields and
  constants explain meaning and units, and public constructors explain the created object's
  invariants and ownership.
- Every method/constructor type parameter and value parameter is documented; every non-void return
  value has an accurate `@return`; checked and contractually meaningful unchecked failures have
  `@throws`.
- Record components are documented through type-level `@param`; enum constants and annotation
  elements are documented; unchanged overrides use accurate inherited documentation rather than
  duplicated text.
- Documentation states immutability/defensive-copy behavior, coordinate space and CRS, units,
  limits, cancellation/close ownership, thread or EDT constraints, structured diagnostics, and
  unsupported behavior wherever those facts affect correct use.
- Deprecations name the supported replacement and intended compatibility window without inventing a
  removal release.
- Package documentation explains each module's public role and dependency/toolkit boundary.
- Java 21 Javadoc generation runs offline with UTF-8, all doclint groups, warning-as-error, and
  deterministic no-timestamp output for every in-scope source set.
- Missing-Javadoc enforcement covers all in-scope source sets and declaration kinds, with no
  blanket package, module, annotation, generated-pattern, or visibility suppression.
- Published source/Javadoc artifacts contain the same documented public surface verified locally.

## Required tests

- Exhaustive declaration inventory tests that fail on an undocumented in-scope public/protected
  type, constructor, method, field, enum constant, annotation element, or package.
- Strict Java 21 doclint and local-link validation for all modules, examples, and build support.
- Publication/consumer inspection of source and Javadoc artifacts.

## Validation

```bash
./gradlew javadocAll --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

If `javadocAll` does not exist when implementation begins, this task may introduce it as the
declarative aggregate for existing per-project Javadoc tasks. It must not run Javadocs twice inside
`qualityGate`. Documentation must describe observed contracts; do not change a public API merely to
make its prose easier to write.
