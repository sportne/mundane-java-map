# G5-010 — Native shapefile smoke

Status: Proposed
Depends on: G5-009
Gate: G5
Type: HITL

## Goal

Read, query, and render a representative shapefile plus one stable malformed-record diagnostic in
the actual GraalVM Native Image smoke application.

## Context

G5-009 completes JVM and corpus behavior. Native compatibility is an architectural requirement and
must exercise the real parser/source/rendering path, not a class-loading placeholder.

## Scope

- `modules/mundane-map-native-tests` shapefile fixtures and smoke assertions
- Native-test dependencies and explicit resource declarations
- Native compatibility fixes within `mundane-map-io-shapefile` that do not change its profile

## Out of scope

- Broad corpus execution inside the native executable
- Reflection configuration as a substitute for explicit construction/registration
- Performance claims or native parsing libraries

## Acceptance criteria

- The native executable opens a supported fixture containing representative geometry and attributes,
  executes a bounded feature query, and renders through the offscreen AWT path.
- The smoke validates semantic results such as feature count, envelope/topology, selected attribute,
  and nonempty bounded render output rather than only process exit.
- A separate malformed record produces the same stable diagnostic code and record/offset context as
  the JVM path.
- A Windows-1252 attribute and undefined-byte null-substitution diagnostic prove the committed manual
  single-byte table is reachable and identical under Native Image.
- Fixtures/resources are declared explicitly and work without classpath scanning or implicit
  resource discovery.
- Source/cursor/file resources close on success and failure.
- Native-targeted code uses no reflection, dynamic proxies, Java serialization, JNI, `Unsafe`,
  internal JDK APIs, or automatic plugin discovery.
- **HITL checkpoint:** a maintainer reviews a successful Java 21 GraalVM `nativeSmoke` run (local or
  CI) and its diagnostic assertion before completion.

## Required tests

- JVM tests of the expanded smoke entrypoint for valid and malformed fixtures.
- Resource-presence tests that fail before native compilation if a required fixture is omitted.
- The actual Native Image build-and-run lane on Java 21.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the native fixture minimal and independently licensed. Do not hide reachability problems with
broad metadata; make registrations and resource inclusion explicit.
