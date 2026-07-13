# G1-001 — First Map Slice Verification

Status: Complete
Depends on: G0-002
Gate: G1
Type: HITL

## Goal

Prove that the implemented in-memory point, line, and polygon slice works through public geometry,
projection, viewport, Java2D rendering, Swing interaction, the basic viewer, and the native smoke
entrypoint, fixing only defects exposed by that verification.

## Context

The current source includes immutable packed `CoordinateSequence` values, point/line/polygon
geometry, `InMemoryLayer`, `WebMercatorProjection`, `MapViewport`, `MapView`,
`examples/basic-viewer`, and `NativeSmokeMain`. Existing tests cover selected model, transform, and
offscreen-render cases, but they do not directly prove pointer callbacks, installed navigation event
wiring, polygon-hole pixels, each geometry renderer independently, listener mutation/removal, or
all empty/degenerate fit cases. The obsolete G1 card marked the slice complete without a passing
repository gate.

## Scope

- Existing production code in `mundane-map-api`, `mundane-map-core`, and `mundane-map-awt` only as
  needed to fix verified G1 defects.
- Existing module tests, architecture tests, `mundane-map-native-tests`, and `examples/basic-viewer`.
- `build-logic` only for a verified defect in the existing Native Image executable lane.
- Public Javadocs affected by a defect fix.

## Out of scope

- New symbol contracts, hit testing, selection, measurement, source adapters, or projections.
- New production modules or broad API redesign.
- Pixel-perfect rendering baselines.

## Acceptance criteria

- Public point, line, and polygon values remain immutable, use defensive copies, and reject invalid
  finite/closure/cardinality inputs consistently.
- Independent offscreen tests prove point fill/stroke, line stroke, polygon fill/stroke, and holes;
  a hole leaves the background visible.
- `MapViewport` round trips coordinates, pans with the installed drag listeners, preserves the
  cursor anchor during wheel zoom, resizes coherently, and fits point, line, empty, and degenerate
  layer envelopes with documented padding behavior.
- Moved and clicked pointer callbacks report the originating screen coordinates and correctly
  inverse-projected map coordinate on the Swing event-dispatch thread.
- Listener add/remove, duplicate registration, and listener-list mutation during callback have
  deterministic tested behavior.
- The basic viewer constructs all three geometry kinds and remains runnable.
- The JVM native-smoke test exercises the same real offscreen path, and the existing Native Image
  lane succeeds when GraalVM is available.
- The maintainer completes the HITL checkpoint by running the basic viewer and confirming pan,
  cursor-centered zoom, fit, geometry appearance, and pointer-coordinate output on a desktop.

## Required tests

- API unit tests for immutability and invalid geometry inputs.
- Core unit tests for projection and viewport edge cases.
- AWT event-dispatch-thread interaction tests using dispatched Swing mouse events.
- Offscreen renderer tests with region/color assertions for each geometry and polygon holes.
- Example construction test plus JVM and Native Image smoke coverage.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test :modules:mundane-map-awt:test :examples:basic-viewer:test :modules:mundane-map-native-tests:test --console=plain
./gradlew :examples:basic-viewer:run
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL is limited to the desktop observation and availability of a GraalVM `native-image` toolchain;
all behavior that can be asserted deterministically belongs in automated tests. Do not loosen tests
to accommodate a defect or expand G1 into later-gate design work.

Completed on 2026-07-13. Focused tests cover geometry validation and copying, viewport edge cases,
isolated tolerant rendering, holes, repeated-paint clearing, installed navigation, pointer routing,
listener identity/mutation, example geometry construction, and the EDT-safe JVM smoke. The desktop
checkpoint ran through WSLg and visually confirmed fit, point/line/polygon appearance, primary drag,
cursor-centered wheel zoom, background clearing, and live coordinate updates. `nativeSmoke` built
and ran the real offscreen path on Linux x86-64 with GraalVM CE Java 21.0.2 after adding the minimal
agent-derived AWT JNI/reflection metadata to the support application. The focused lanes, full
`qualityGate`, and final review are recorded with the task commit.
