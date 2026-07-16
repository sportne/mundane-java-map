# G8-001 — Level 1 Native Image and CI hardening

Status: Proposed
Depends on: G2-007, G3-004, G5-010, G6-005, G7-004
Gate: G8
Type: HITL

## Goal

Aggregate representative Level 1 success and diagnostic paths into a required Linux Java 21
GraalVM Native Image lane and make that evidence a release blocker.

## Context

Earlier native tasks prove symbols, shapefiles, and rasters individually. This gate validates their
coexistence with interaction/measurement and the final G7 default render path under the Level 1
dependency graph. It preserves their one executable and exact 12-resource inventory.

## Scope

- `modules/mundane-map-native-tests`: one direct aggregate scenario sequence and shared JVM/native
  assertions over API, core, AWT, shapefile, and image modules
- Root/build-logic Native Image wiring and `.github/workflows/native-image.yml`
- `modules/mundane-map-architecture-tests`: production prohibition, resource-inventory, workflow, and
  specialized-lane graph policies
- `DESIGN.md`, `design/G8-release-readiness.md`, this task/index, and `ROADMAP.md`: exact Linux support
  claim and the named release-lane approval record
- Only narrow owning-module compatibility fixes required by explicit aggregate reachability

## Out of scope

- Windows/macOS Native Image support claims without separate evidence
- Native performance acceleration, broad reflection metadata, and executing corpus/performance lanes
  inside the native binary
- A second native executable/test binary, new resource, wildcard metadata, tracing-agent output,
  private G7 cache mode/counter, or native timing assertion
- Reopening normal CI/toolchain behavior owned by G0 or claiming every Linux distribution/architecture
- Publishing release artifacts

## Acceptance criteria

- `NativeSmokeMain.runSmoke()` directly runs the approved G2 symbol scenario, one scoped G5 workspace
  and scenario, one scoped G6 workspace and scenario, then one final G8 scenario before printing the
  unchanged `mundane-map native smoke: OK` sentinel.
- The G8 scenario proves exact duplicate renderer registration diagnostic/context, planar and
  antimeridian geographic distances, real Swing measurement routing/state, repeated final-default G7
  rendering through one exact core `InMemoryLayer`, and observable tool claim release/reuse after
  close without duplicating G2/G5/G6 assertions.
- The aggregate smoke verifies `SYMBOL_RENDERER_DUPLICATE`, G5's exact malformed-record diagnostic,
  and G6's exact malformed-PNG diagnostic without inspecting exception message text. The approved G2
  catalog-missing diagnostic remains additional coverage.
- Assertions validate semantic outputs and lifecycle behavior, not only class reachability or exit
  status.
- Registries and the exact 12 resource paths are explicit; G8 adds no resource or reachability entry,
  and the aggregate path uses no reflection, classpath/resource scanning, dynamic proxies, Java
  serialization, JNI, `Unsafe`, or internal JDK APIs.
- Root `nativeSmoke` depends only on the support module's one Java 21 `nativeRun`; metadata repository
  and fallback remain disabled and no `nativeTest` binary is built.
- One Ubuntu 24.04 x86_64 CI job installs GraalVM Java 21, records full Java/native-image versions,
  runs only `./gradlew nativeSmoke --console=plain`, retains the bounded log/toolchain record even on
  failure, and blocks Level 1 release when it fails.
- Normal JVM CI remains separate and does not masquerade as Native Image evidence.
- Support wording claims only Java 21 GraalVM Native Image on the recorded Ubuntu 24.04 Linux x86_64
  lane. Windows, macOS, Linux AArch64, other distributions, and cross-platform compatibility remain
  unverified until maintained build-and-run evidence exists.
- **HITL checkpoint — G8 Linux Native Image release-lane approval:** a maintainer reviews one clean CI
  run on the reviewed commit, exact tools/platform, sentinel, 12-resource inventory, diagnostics,
  final G7 cache disposition (including none), repeat-render result, and narrow support wording.

## Required tests

- JVM tests for every aggregate smoke branch, exact 12-resource metadata/tree, mutated invariant
  controls, real EDT events, same-tool cross-view claim/release, repeated invocation, and lifecycle
  cleanup.
- Architecture tests over all Level 1 production modules for prohibited native-targeted mechanisms.
- A clean Java 21 GraalVM Native Image build-and-run in Linux CI.
- Workflow/task-graph tests ensuring the native lane remains one pinned Linux job and excludes
  normal/corpus/render/performance/publication/consumer lanes.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

A local native run is useful but does not replace the required Linux CI result. Record the checkpoint
in these Notes with commit/run URL, reviewer/date, OS/architecture, GraalVM and full tool versions,
command/sentinel, resources, diagnostics, final G7 disposition, repeat-render result, support wording,
and outcome. Do not add broad reachability metadata to silence failures; fix explicit construction and
resource wiring or mark an actual incompatibility Blocked.

Implementation evidence pending the named checkpoint (2026-07-16): the JVM aggregate scenario,
workflow policy, and configured task-graph isolation are implemented. Focused JVM and architecture
checks pass with the unchanged exact 12-resource inventory, all required diagnostics, two identical
final-default paints, tool release/reuse, and two complete sequential smoke invocations. G7's final
default retains only the private vector-template cache.

The supplemental local no-fallback run used GraalVM CE Java 21.0.2+13.1 / `native-image 21.0.2` on
Ubuntu 24.04.1 WSL2 Linux x86_64. Image compilation completed in 28.1 seconds, but execution failed
before the sentinel when that cached toolchain's `SunFontManager` attempted a JNI lookup of
`sun.font.TrueTypeFont` from the newly reached measurement-label path. No G8 resource, JNI,
reflection, or reachability metadata was added to work around that toolchain incompatibility. The
task remains Proposed until the reviewed commit receives a clean required Ubuntu 24.04 CI run and
the **G8 Linux Native Image release-lane approval** records its URL, exact current tool versions,
sentinel, resources, diagnostics, repeat-render result, and approval. The permitted support wording
remains: GraalVM Native Image is verified with Java 21 on Linux x86_64 using the recorded Ubuntu
24.04 CI environment; Windows, macOS, Linux AArch64, other distributions, and cross-platform Native
Image compatibility are unverified.
