# Design

## Goals

- Provide a lightweight embeddable Java map component.
- Keep Level 1 production modules free of third-party runtime dependencies.
- Support explicit, deterministic APIs that work on the JVM and with GraalVM Native Image.
- Grow by tested vertical capabilities rather than speculative abstraction layers.
- Keep data formats behind format-neutral vector and raster source contracts as they are added.

## Non-goals

- A full desktop GIS.
- Runtime plugin discovery, classpath scanning, or reflection-based registration.
- Arbitrary CRS transformation, GeoTIFF, vector editing, or geometry overlay operations in the
  initial slice.
- Custom native libraries for performance without benchmark evidence.

## Dependency policy

Level 1 production modules use only the JDK and other `mundane-map` modules. Build and test tooling
may use JUnit, ArchUnit, JaCoCo, Checkstyle, SpotBugs, Error Prone, Spotless, and GraalVM Native
Build Tools. Future external integrations must live in optional adapter modules and must not leak
their types through `mundane-map-api`.

## Module boundaries

```text
mundane-map-api
      ^
      |
mundane-map-core
      ^
      |
mundane-map-awt
```

- `api` depends on `java.base` only.
- `core` depends on `api` and `java.base` only.
- `awt` owns `java.desktop`, Swing, Java2D, pointer wiring, and render caches.
- I/O modules may depend on `api` and `core`, but never on `awt`.
- Test, native, architecture, and example modules are not published.

## Geometry and features

The public geometry model is immutable. Coordinate sequences use packed primitive storage and make
defensive copies at API boundaries. Features combine geometry with a stable ID, display name,
attributes, and style. Geometry remains separate from future symbol/rendering extensions.

## Projection pipeline

```text
source coordinate -> map projection -> projected world coordinate -> viewport -> screen pixel
```

`Projection` owns forward and inverse projection. `MapViewport` owns only projected-world to screen
math. The initial concrete projection is Web Mercator; source coordinates are longitude/latitude in
degrees.

## Rendering and interaction

`MapView` is a Swing `JComponent`. It renders through Java2D, owns its viewport state, and follows
the Swing event-dispatch-thread rule. Pointer listeners receive both screen coordinates and inverse-
projected map coordinates. Render registration will remain explicit when custom graphics arrive.

## Native Image

Native-targeted code avoids reflection, runtime scanning, dynamic proxies, Java serialization,
`Unsafe`, internal JDK APIs, and implicit resource discovery. A real offscreen render is the first
native smoke path; metadata workarounds require a recorded design decision.

## Decisions

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-07-11 | Use `mundane-java-map` and `io.github.mundanej.map`. | Align with the existing MundaneJ family. |
| 2026-07-11 | Use Java 21 and Gradle 9.5.1 Groovy DSL. | Match the existing project baseline. |
| 2026-07-11 | Use BSD 3-Clause. | Match the existing project family. |
| 2026-07-11 | Use Swing/Java2D initially. | Smallest JDK-only desktop path. |
| 2026-07-11 | Keep Level 1 production modules JDK-only. | Preserve portability and native-image friendliness. |
| 2026-07-11 | Add format modules only with working behavior. | Avoid empty or speculative modules. |
| 2026-07-11 | Keep Native Image outside the default gate. | Native tooling is optional for normal development. |

