# G1 — First map slice

Status: Complete in the initial skeleton

## Goal

Prove the module boundaries with an end-to-end Swing map component that displays in-memory point,
line, and polygon features and supports pan, cursor-centered zoom, fit-to-data, and map-coordinate
pointer events.

## Scope

- `modules/mundane-map-api`
- `modules/mundane-map-core`
- `modules/mundane-map-awt`
- `modules/mundane-map-architecture-tests`
- `modules/mundane-map-native-tests`
- `examples/basic-viewer`

## Acceptance criteria

- [x] Point, line, and polygon geometry is immutable.
- [x] Features contain IDs, names, attributes, and basic styles.
- [x] Web Mercator forward/inverse projection is implemented.
- [x] Viewport pan and cursor-centered zoom preserve expected coordinates.
- [x] A Swing component renders all three geometry types.
- [x] Pointer listeners receive screen and map coordinates.
- [x] The basic example is runnable.
- [x] Offscreen rendering is tested.
- [x] A native smoke entrypoint exercises the real offscreen rendering path.

## Validation

```bash
./gradlew qualityGate --console=plain
./gradlew nativeSmoke --console=plain
```

