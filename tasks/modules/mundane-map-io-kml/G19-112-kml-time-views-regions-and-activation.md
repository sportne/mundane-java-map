# G19-112 — KML time, views, regions, and activation

Status: Proposed
Depends on: G19-010, G19-110, G19-111
Gate: G19
Type: AFK

## Goal

Implement KML 2.3 temporal primitives, camera/look-at views, regions, LOD, and deterministic activation.

## Context

TimeStamp/TimeSpan, Camera/LookAt, Region, LatLonAltBox, and Lod are currently rejected, so temporal and
scale/view-dependent KML cannot be represented or queried correctly.

## Scope

- Model/parse KML date-time precision, TimeStamp/TimeSpan open bounds, inheritance, and viewer-time filtering.
- Implement Camera/LookAt position/orientation/range/horizontal FOV, time children, viewer options, altitude modes,
  and map-viewport conversion with explicit 2D limitations.
- Implement Region, LatLonAltBox, Lod pixel thresholds/fades, antimeridian/altitude behavior, nested activation,
  visibility/open interaction, and deterministic scheduling during viewport/time changes.
- Bound active candidates, hierarchy traversals, region calculations, time values, scheduled work, and retained state.
- Define stable activation/omission diagnostics and AWT/Vaadin parity.

## Out of scope

- 3D camera rendering, terrain fly-through, and wall-clock autoplay.

## Acceptance criteria

- Time/view/region values preserve KML semantics and activate identically in AWT/Vaadin at boundaries.
- View/time transitions are cancellation-aware, bounded, and cannot resurrect stale content.
- Invalid time/view/LOD/region values fail before partial state changes.

## Required tests

- Time precision/open-bound, Camera/LookAt/FOV/altitude, region/LOD/fade/nesting/dateline matrices.
- Viewport/time churn, boundary tolerances, stale scheduling, hostile hierarchy/candidates, and parity tests.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

None.
