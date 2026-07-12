# G5-004 — Polyline multipart slice

Status: Proposed
Depends on: G5-002
Gate: G5
Type: AFK

## Goal

Read bounded single- and multipart polyline records and display them through the shapefile source and
viewer.

## Context

G5-002 establishes SHP framing, record identity, diagnostics, and sequential iteration. G4 provides
the format-neutral multipart geometry representation.

## Scope

- Polyline record decoding in `modules/mundane-map-io-shapefile`
- Multipart feature mapping and viewer rendering
- Hand-built fixtures covering one and several parts

## Out of scope

- Polygon ring/hole interpretation, DBF attributes, Z/M ordinates, and line editing
- Simplification or clipping optimizations

## Acceptance criteria

- Polyline record bounds, part count, point count, part-index table, coordinate payload, and declared
  content length are validated before allocation.
- Empty, descending, duplicate, and out-of-range part starts follow the approved profile and produce
  record-level diagnostics rather than unchecked exceptions.
- Single- and multipart lines preserve source part order and use packed primitive coordinate
  storage.
- Non-finite ordinates and envelope/payload disagreements are handled by one documented policy.
- The source returns stable record identity and the viewer renders every valid part without joining
  unrelated parts.
- Limits are checked with overflow-safe arithmetic, and malformed records do not corrupt subsequent
  record framing when continued reading is allowed.
- Public geometry/source changes, if any, are immutable and documented.

## Required tests

- Hand-built single-part, multipart, zero-length-part-policy, and boundary-count fixtures.
- Negative tests for truncated part tables/payloads, invalid part starts, non-finite coordinates,
  count overflow, and declared-length mismatch.
- Feature-source integration and offscreen viewer-render tests that assert part topology rather than
  platform-specific pixels.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep format decoding separate from AWT rendering. Coordinate arrays should be validated once and
published only through immutable views.
