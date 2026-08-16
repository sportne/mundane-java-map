# G19-104 — GPX domain, feature-source, and dimensional mapping

Status: Proposed
Depends on: G19-001, G19-103
Gate: G19
Type: AFK

## Goal

Project the complete GPX document into stable queryable features without losing domain access, dimensions,
route/segment identity, ordering, or bounded-source behavior.

## Context

The current source flattens waypoints/tracks into a small attribute schema. Complete GPX content requires an
explicit mapping rather than treating the feature view as the only document model.

## Scope

- Expose the complete immutable GPX document independently of its feature projection and document ownership lifecycle.
- Define stable feature kinds/IDs/schemas for top-level waypoints, routes, tracks, and optionally explicit segment/point
  views without duplicating or ambiguously flattening identity.
- Map WGS 84 XY plus available elevation/time/measure ordinates under the dimensional geometry contract; retain segment
  boundaries, point membership, standard metadata, extensions, and absent/null distinctions.
- Define metadata bounds versus derived extent, antimeridian behavior, empty/degenerate geometry, selection, filtering,
  ordering, and cancellation semantics.
- Bound feature/coordinate/attribute/extension projection, cursors, snapshots, diagnostics, and query work prospectively.

## Out of scope

- Routing, simplification, resampling, map matching, activity statistics, and automatic reprojection on storage.

## Acceptance criteria

- Domain-to-feature mappings are documented, deterministic, reversible where claimed, and retain dimensional/segment identity.
- Queries preserve stable source order and cancellation/limit behavior without reparsing or hidden ambient work.
- Every standard value remains accessible even when it does not fit the neutral feature schema.

## Required tests

- Waypoint/route/track/segment/point feature and domain mapping, IDs, dimensions, nulls, extents, antimeridian, and order.
- Query/cancellation/lifecycle, large attribute/extension projection, empty/degenerate input, and aggregate source limits.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, source/corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
