# G11-030 — Workspace model and secure reader

Status: Complete
Depends on: G11-003
Gate: G11
Type: AFK

## Goal

Read a bounded local `.mmap.xml` version 1 file into an immutable portable workspace document without
instantiating sources, AWT objects, or arbitrary application state.

## Context

G11-003 approves the exact Level 1 field/exclusion list, XML grammar, local relative-path model,
limits/accounting, stable problems, and hardened JDK StAX policy.

## Scope

Create working JDK-only `modules/mundane-map-workspace` behavior with immutable document, view,
layer, source, symbol-reference, relative-path, limits, problem, exception, and file values; implement
bounded file snapshot/UTF-8 decoding and the strict namespace-aware version 1 XML reader; register the
working module in architecture/build inventories.

## Out of scope

Writing, source/catalog registries, opening a session, AWT integration, a viewer, alternate versions,
migration, JSON, schema compilation, remote paths, or concrete format dependencies.

## Acceptance criteria

- Canonical and equivalently ordered valid version 1 XML reads into immutable ordered values with
  exact EPSG:4326/EPSG:3857 keys and portable guarded relative paths.
- Unknown/missing/duplicate structure, versions/namespaces, numeric/value errors, malformed UTF-8/XML,
  DTD/entities/external access, and every exact/one-over bound fail with the approved stable problem.
- Reader security properties are explicitly set and read back with no weaker fallback, discovery,
  DOM, XSD, Transformer, or internal JDK API.
- The new module depends only on API, core, and `java.xml` and exposes no AWT or format-adapter type.

## Required tests

Immutable-value/copy/grammar/accounting tests; canonical/variant XML, strict UTF-8/XML 1.0, security
negative controls, structure/value/problem precedence, limit boundaries, file snapshot mutation and
cleanup tests; architecture inventory.

## Validation

```bash
./gradlew :modules:mundane-map-workspace:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The module is created only with this working read slice. It remains AWT-free, JDK-only, and unaware
of every concrete source format.
