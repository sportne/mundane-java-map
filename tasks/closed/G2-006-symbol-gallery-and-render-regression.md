# G2-006 — Symbol Gallery and Render Regression

Status: Complete
Depends on: G2-004, G2-005
Gate: G2
Type: HITL

## Goal

Expose every Level 1 symbol behavior in a runnable gallery and establish a portable rendering
regression lane that catches material placement or painting regressions without requiring identical
pixels across platforms.

## Context

The repository currently has only `examples/basic-viewer` and one broad offscreen image assertion in
`MapViewTest`. G2-004 and G2-005 complete vector markers, composites, endpoints, hatches, raster
icons, catalogs, and explicit registration. This task is the first task allowed to create the root
`renderRegression` command.

## Scope

- A working `examples/symbol-gallery` application and automated construction test.
- A focused rendering-regression test source set or test module with committed, reviewable fixture
  definitions.
- Root Gradle wiring for `renderRegression` and relevant CI wiring kept separate from `qualityGate`.
- Example and lane documentation in existing lean project files only.

## Out of scope

- Cross-platform pixel-perfect golden images.
- SVG import, labels, theme editors, performance benchmarks, and Native Image resource testing.
- An empty reusable rendering-test framework unrelated to the gallery cases.

## Acceptance criteria

- The gallery visibly and by name covers all eight built-in markers, raster icons, every anchor,
  offsets, both size units, both rotation modes, opacity, composites, endpoint markers/arrowheads,
  and all basic hatch fills.
- Gallery construction is testable headlessly and uses the same public APIs and explicit renderer
  registration expected of consumers.
- `renderRegression` is a separate deterministic Gradle lane and exercises real offscreen AWT
  rendering at fixed logical viewport inputs.
- Regression assertions use painted bounds, sampled regions, alpha/color tolerances, path/shape
  invariants, and relative ordering; they do not compare full images byte-for-byte.
- Failure output names the scenario and expected invariant and writes optional diagnostic images only
  to ignored build output.
- The lane is stable with antialiasing/font differences and does not require a display server.
- The maintainer completes the HITL checkpoint by visually reviewing the gallery on a desktop and
  approving marker shapes, anchors, rotations, endpoints, icon interpolation, and hatch appearance.

## Required tests

- Headless example-construction test covering every gallery section.
- Rendering-regression scenarios for vector paths, placement, composition, raster icons, line
  endpoints, holes, and hatch clipping.
- A task-wiring test or CI invocation proving `renderRegression` is independent from `qualityGate`.

## Validation

```bash
./gradlew :examples:symbol-gallery:test --console=plain
./gradlew renderRegression --console=plain
./gradlew :examples:symbol-gallery:run
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL is limited to visual review of intended appearance. Record platform and JDK for that review;
portable automated invariants remain the release evidence. Do not commit generated screenshots as a
pixel oracle.

Completed on 2026-07-13. The new symbol-gallery example exposes the four named marker, placement,
line, and fill inventories through real features, one immutable catalog, and explicitly constructed
renderer registries. The separate headless `renderRegression` lane runs 35 independent rendering
scenarios with fixed portable tolerances, structured failure diagnostics, and no committed image
oracle; CI invokes it independently from `qualityGate`.

**G2 symbol-gallery visual approval:** Pass. The maintainer/task requester approved every gallery
section on 2026-07-13 after reviewing captures produced on Linux 5.15.167.4 WSL2 at 96 DPI (1x
desktop scale) with Ubuntu OpenJDK 21.0.11. The gallery was launched as a desktop application, every
tab was inspected at its initial fit, and scripted wheel/drag interaction exercised zoom and pan.
The approval included a final refinement of the arrow examples: their 5.4-pixel line weight terminates
under a WEST-anchored, head-only toolkit-neutral vector triangle, avoiding a separately antialiased
overlapping shaft and its visible seam. Generated review captures remain in ignored build output.
