# AGENTS.md

## Project priorities

- Keep production runtime modules JDK-only unless a task explicitly adds an optional adapter.
- Prefer immutable values, packed primitive storage, explicit registration, and stable diagnostics.
- Add one tested vertical capability at a time; do not create empty future-format modules.
- Add or update Javadocs for public API changes.

## Commands

- Full local gate: `./gradlew qualityGate --console=plain`
- JVM checks: `./gradlew checkAll --console=plain`
- Offline repository: `./gradlew offlineRepositoryVerification --console=plain`
- Basic viewer: `./gradlew :examples:basic-viewer:run`
- Native smoke: `./gradlew nativeSmoke --console=plain`
- Publication smoke: `./gradlew publicationDryRun --console=plain`
- Whitespace: `git diff --check`

## Boundaries

- Public contracts live in `modules/mundane-map-api`.
- JDK-only algorithms live in `modules/mundane-map-core`.
- Swing and Java2D live only in `modules/mundane-map-awt` and consumer examples/tests.
- Format adapters use the `mundane-map-io-*` prefix and must not depend on AWT.
- Native-targeted production code must not use reflection, classpath scanning, dynamic proxies,
  Java serialization, JNI, `Unsafe`, internal JDK APIs, or implicit resource scanning.

## Validation rule

End each task with its narrowest relevant test command, then run `qualityGate` before handoff.
Offline-repository, native, corpus, rendering-regression, and performance lanes remain separate
unless the task changes one of those concerns.
