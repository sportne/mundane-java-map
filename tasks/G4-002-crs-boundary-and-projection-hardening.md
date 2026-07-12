# G4-002 — CRS Boundary and Projection Hardening

Status: Proposed
Depends on: G4-001
Gate: G4
Type: AFK

## Goal

Make source and display CRS assumptions explicit, recognize only EPSG:4326 and EPSG:3857 through an
explicit registry, and reject or diagnose unsupported transformations and projection-domain errors.

## Context

`Projection` currently exposes only a string ID, `WebMercatorProjection` assumes longitude/latitude
degrees and silently clamps latitude, and `ProjectionEnvelopes` projects four corners without CRS or
domain validation. Shapefile PRJ retention and distance strategy selection require immutable CRS
metadata that can preserve an unknown definition without pretending it is transformable.

## Scope

- Immutable CRS metadata and projection-boundary contracts in `mundane-map-api`.
- Explicit CRS/projection registry, EPSG:4326 identity behavior, hardened Web Mercator, and envelope
  validation in `mundane-map-core`.
- Focused API/core tests and affected public Javadocs.

## Out of scope

- Arbitrary WKT parsing, datum shifts, additional projections, antimeridian-splitting geometry, or a
  PROJ adapter.
- Heuristic CRS recognition from coordinate ranges or filenames.

## Acceptance criteria

- CRS metadata is immutable and records canonical identifier, geographic/projected/unknown kind,
  declared units/axis meaning where recognized, and a bounded retained original definition.
- An application-owned explicit registry recognizes canonical EPSG:4326 and EPSG:3857 entries and
  their deliberately listed aliases only; duplicate registration and unknown lookup are diagnosed
  stably without global scanning.
- Unknown definitions are retained for metadata/reporting but never receive a guessed transform.
- Source-to-display operations detect missing, unsupported, and mismatched CRS values using the
  G4-001 diagnostic shape and do not silently assume EPSG:4326.
- Web Mercator documents and tests longitude, latitude, projected-world, pole, and overflow policy;
  every accepted result is finite and within the conventional EPSG:3857 world envelope.
- The existing latitude-clamp behavior is either retained with an explicit clipped-domain result or
  replaced by a stable domain failure; no silent behavior remains at source/query boundaries.
- Envelope projection validates axis/domain bounds, degenerate extents, and non-finite intermediate
  results before allocation or rendering.
- Existing ordinary EPSG:4326/Web Mercator round trips remain accurate within documented tolerances.

## Required tests

- API immutability, retained-definition bound, and invalid-metadata tests.
- Registry tests for recognized entries, explicit aliases, duplicates, isolation, and unknown values.
- Projection tests at ordinary points, world edges, poles, invalid longitude/latitude, projected
  limits, degenerate envelopes, and overflow cases.
- Structured diagnostic tests for missing, mismatched, unknown, and clipped/out-of-domain CRS input.

## Validation

```bash
./gradlew :modules:mundane-map-api:test :modules:mundane-map-core:test --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep registry construction explicit and JDK-only. Preserve raw unknown definitions only within the
approved text limit; retention is not recognition and must not trigger reflection or optional tools.

