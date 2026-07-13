# G7-003 — Clipping and simplification

Status: Proposed
Depends on: G7-002
Gate: G7
Type: AFK

## Goal

Reduce offscreen and subpixel vector work with viewport clipping and scale-aware simplification while
preserving visible line and polygon topology.

## Context

The spatial index limits candidate features. G7-001 evidence identifies remaining projection/path
cost, and unsimplified production geometry remains the fallback and correctness reference.

## Scope

- Immutable optimization limits/results and deterministic JDK-only screen clipping, simplification,
  and bounded polygon-safety algorithms in `modules/mundane-map-core`
- One private operation-local packed screen plan and built-in paint integration in
  `modules/mundane-map-awt`
- Authoritative-geometry interaction/endpoint preservation, rendering regression and architecture
  coverage, and paired unoptimized/optimized performance scenarios

## Out of scope

- Source-coordinate simplification, source/query/fit/hit/selection changes, query padding, projection
  densification, topology repair, concave polygon overlay/splitting, and custom-renderer optimization
- Public optimization switches/metrics, persistent projected paths, retained LODs or tile pyramids,
  format-specific simplification, caching, and native/JTS acceleration

## Acceptance criteria

- Eligible built-in line/fill paint projects once and uses one bounded operation-local screen plan;
  custom renderers and over-budget/unsafe cases use the exact authoritative path.
- The closed clip expands the logical component by one antialias pixel plus the maximum applicable
  half-stroke width, and production RDP tolerance is exactly 0.25 logical pixel.
- Lines use deterministic Liang-Barsky clipping followed by iterative RDP, preserve source-part and
  fragment order, and never replace original endpoints/tangents with clip intersections.
- Polygon optimization validates bounded simple shell/hole topology, clips only the safe convex/
  contained profile, revalidates closed simplified candidates, and otherwise falls back for the
  complete polygon without repair or partial candidate publication.
- Disjoint path culling, degenerate output, hole collapse/reassociation, topology-budget exhaustion,
  and numerically unsafe candidates have exact deterministic cull/fallback rules.
- Results are deterministic for the same geometry, viewport, and tolerance.
- Hit/hover/selection, endpoint markers, measurement, fit, source queries, and public geometry remain
  authoritative and unchanged; a path-culled line may still paint original endpoint markers.
- Explicit limits bound output coordinates, cumulative plan bytes, and topology comparisons;
  optimization-limit fallback emits no source diagnostic, and source success wins atomically before
  uncancellable per-feature painting begins.
- Evidence preserves earlier disabled rows, adds exact small/dense/pan/zoom optimized comparisons,
  reports plan/work counters and before/after median/p95, and treats semantic equality—not duration—as
  the gate.
- No public mutable buffer, public mode/metric, persistent plan, external dependency, or native code is
  introduced.

## Required tests

- Exact line clipping/fragment mapping tests for every edge/corner, repeated/degenerate coordinates,
  multipart order, robust distance, RDP equality/ties, limits, and overflow.
- Polygon tests for closed-ring anchoring, convex partial clips, simple/invalid/touching/nested holes,
  orientation/association preservation, concave/boundary-hole/limit/numeric fallback, and mixed
  multipolygon outcomes.
- AWT enabled/disabled tests for built-in lines/fills/hatches/composites, endpoint markers, custom
  bypass, authoritative hit/hover/selection, overlays, source lifecycle/cancellation, and graphics
  state.
- Rendering-regression cases using tolerant regions/bounds/colors and fixed performance rows/counters
  for small overhead, dense rendering, pan, and zoom under both profiles.
- Architecture tests for core/AWT boundaries, packed immutable values, no recursion by coordinate
  count, no public mode/metrics, no cache/global state, and no external/native implementation.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-performance-tests:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew renderRegression --console=plain
./gradlew performanceEvidence --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The production policy is fixed and evidence-backed, not a caller tuning surface. Correct topology,
authoritative interaction, and exact fallback take priority over vertex-count reduction; G7-004 owns
any later retention/cache decision.
