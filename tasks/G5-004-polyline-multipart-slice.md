# G5-004 — Polyline multipart slice

Status: Complete
Depends on: G5-002
Gate: G5
Type: AFK

## Goal

Read bounded single- and multipart polyline records and display them through the shapefile source and
viewer.

## Context

G5-001 fixes the supported PolyLine profile. G5-002 establishes SHP framing, record identity,
diagnostics, and sequential iteration. G4-003 provides packed singular and multipart line geometry.
G5-003 may compose through the same private decoder but is not a prerequisite for this sidecar-free
slice.

## Scope

- Package-private shared multipart framing and PolyLine decoding in
  `modules/mundane-map-io-shapefile`
- Multipart feature mapping and viewer rendering
- Hand-built fixtures covering one and several parts

## Out of scope

- Polygon ring/hole interpretation, DBF attributes, Z/M ordinates, and line editing
- Simplification or clipping optimizations
- Public format/source APIs, public parser types, SHX behavior, and per-part object models

## Acceptance criteria

- Header type `3` is accepted while type `5` remains staged; existing frame, type, and optional-index
  validation order is preserved before PolyLine payload dispatch.
- The exact `44 + 4 * parts + 16 * points` content size, positive counts, configured limits, checked
  arithmetic, Java capacities, and complete prospective allocation charge are validated before
  variable allocation; table and coordinate values are validated before publication.
- Empty, descending, duplicate, and out-of-range part starts follow the approved profile and produce
  record-level diagnostics rather than unchecked exceptions.
- A whole part with fewer than two distinct coordinate pairs is rejected; consecutive duplicate
  vertices and structurally valid self-crossing lines are accepted and retained.
- Single- and multipart lines preserve source part order and use packed primitive coordinate
  storage, with fencepost offsets and no bridge between parts.
- Non-finite ordinates, signed zero, record/file bounds, and envelope/payload disagreements follow the
  exact validation and diagnostic order in the G5 design.
- The source returns stable record identity and the viewer renders every valid part without joining
  unrelated parts.
- Limits are checked with overflow-safe arithmetic. A malformed geometry terminates that cursor after
  cleanup; the reader neither resynchronizes nor skips ahead, and the otherwise open source remains
  reusable under the G4 failure contract.
- The shared multipart reader and PolyLine decoder remain package-private in the existing single
  package; no public API changes.

## Required tests

- Hand-built single-part, multipart, null-interleaved, rejected whole-part zero length, accepted
  interior duplicate/self-crossing, signed-zero-equivalent, and boundary-count fixtures.
- Negative tests for truncated part tables/payloads, invalid part starts, non-finite coordinates,
  every exact diagnostic reason, maximum signed counts, Java-array capacity, cumulative-allocation
  overflow/limit handling, and declared-length mismatch.
- Cancellation, cursor cleanup/source reuse, full-validation-before-query, and singular/multipart
  mapping tests. When G5-003 is present, also prove indexed/sequential decoder equivalence.
- Feature-source integration and offscreen viewer-render tests that assert visible parts and an
  unpainted inter-part gap rather than platform-specific pixels.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep format decoding separate from AWT rendering. Use the G5 design's private `ShpMultipartReader`
and `PolylineDecoder`; validate every value as it enters packed storage and publish only through the
immutable G4 geometry factories. G5-005 reuses the framing reader but owns all polygon semantics.

Completed on 2026-07-14 with a bounded package-private multipart reader and PolyLine decoder. Type-3
records now preserve singular or packed multipart geometry without bridging parts, while exact
count, size, allocation, table, coordinate, bounds, cancellation, and diagnostic rules are enforced
before publication. Byte-authored fixtures cover sequential and SHX-backed decoding, full validation
before filtering, source reuse after failure, and offscreen viewer rendering with an unpainted
inter-part gap. Polygon semantics remain staged for G5-005.
