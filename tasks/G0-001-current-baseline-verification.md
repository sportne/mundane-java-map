# G0-001 — Current Baseline Verification

Status: Complete
Depends on: None
Gate: G0
Type: AFK

## Goal

Make the existing Java 21 multi-module skeleton configure and pass its normal build and publication
checks on supported CI JDKs, including a fully local dependency-repository mode.

## Context

The root `build.gradle` already defines `checkAll`, `qualityGate`, and `publicationDryRun`, while
`settings.gradle` exposes `map.offlineRepo`. The convention scripts under `build-logic` currently
call unsupported `libs.findLibrary(...)` methods during configuration, `build-logic/settings.gradle`
does not honor the offline repository property, and `.github/workflows/ci.yml` changes
`map.javaRelease` to the matrix JDK instead of consistently compiling the library for Java 21.

## Scope

- Root and included-build Gradle settings, convention plugins, version catalog, and wrapper
  configuration.
- Root verification and publication-staging task wiring.
- The JVM CI workflow and focused build-configuration tests or fixtures.

## Out of scope

- Production API or runtime behavior.
- Native Image execution or changes to the native workflow.
- Publishing artifacts to a remote repository.
- Raising the Java 21 language or bytecode baseline.

## Acceptance criteria

- Gradle 9.5.1 configures every included project without unsupported version-catalog API calls.
- Java compilation always uses `--release 21`; CI executes the same Java 21 output checks on its
  Java 21 and newer-JDK legs rather than changing the release target.
- `map.offlineRepo` applies consistently to plugin and dependency resolution in both the root build
  and `build-logic`, and the build does not silently contact public repositories when it is set.
- Missing artifacts in offline mode fail with a clear repository-resolution error that names the
  requested coordinate.
- `qualityGate` completes all JVM checks, formatting checks, and Javadocs.
- `publicationDryRun` stages the API, core, and AWT POM, binary, sources, and Javadoc artifacts at
  the documented local Maven coordinates without publishing remotely.

## Required tests

- Gradle configuration coverage for normal and `map.offlineRepo` repository selection.
- A separate full-build offline-repository verification using copied sources and isolated Gradle
  state.
- JVM CI coverage on Java 21 and at least one newer JDK while compiling with release 21.
- Publication-staging assertions for expected modules and classifier artifacts.

## Validation

```bash
./gradlew checkAll --console=plain
./gradlew offlineRepositoryVerification --console=plain
./gradlew publicationDryRun --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep build and test dependencies out of published runtime variants. An offline validation may use a
temporary repository fixture, but it must not embed machine-specific paths or require network access.

Completed on 2026-07-13 with isolated normal/offline repository-policy fixtures, missing-coordinate
failure checks, Java 21 class-file verification, exact staged-artifact verification, and successful
local `checkAll`, `publicationDryRun`, and `qualityGate` runs on Java 21. The expensive copied-build
offline proof was separated on 2026-07-13 into `offlineRepositoryVerification`, a Java 21 CI lane
triggered by Gradle dependency and repository-policy changes. The normal CI matrix fixes the
artifact release at 21 and selects Java 21 or 25 only as the test launcher.
